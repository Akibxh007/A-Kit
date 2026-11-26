package com.example.feature.power

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.data.AccentSwatch
import com.example.data.FloatingPillStyle
import com.example.data.ThemePreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class PowerMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var hudContainer: LinearLayout? = null
    private var hudMetricsText: TextView? = null
    private var hudSubMetricsText: TextView? = null
    private var hudCloseText: TextView? = null
    private var hudParams: WindowManager.LayoutParams? = null

    private var highAlarmLatched = false
    private var lowAlarmLatched = false
    private var overheatAlarmLatched = false

    @Volatile
    private var shouldStayPersistent = true

    @Volatile
    private var autoLaunchAodEnabled = false

    /**
     * Zero-delay dynamic BroadcastReceiver for charger plug-in & unplug events.
     * Runs continuously inside this persistent foreground service even when A-Kit is
     * closed or swiped from Recent Apps and the device screen is locked.
     */
    private val powerConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    if (autoLaunchAodEnabled || PowerTelemetryEngine.isAutoLaunchAodEnabled) {
                        launchChargingAodImmediately(applicationContext)
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    dismissChargingAodImmediately(applicationContext)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PowerTelemetryEngine.ensureInitialized(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        createNotificationChannels()
        startForegroundSafely(PowerTelemetryEngine.state.value)
        PowerTelemetryEngine.setServiceRunning(true)

        // Dynamically register zero-delay charger connection / disconnection listener
        val powerFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerConnectionReceiver, powerFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(powerConnectionReceiver, powerFilter)
        }

        // Hydrate Design Studio & Auto-Launch AOD preferences directly from DataStore so
        // cold service restarts after task removal retain full configuration.
        val themePrefsRepo = ThemePreferencesRepository(applicationContext)
        serviceScope.launch {
            themePrefsRepo.preferencesFlow.collect { prefs ->
                autoLaunchAodEnabled = prefs.autoLaunchAodOnPlugIn
                PowerTelemetryEngine.updateDesignPreferences(prefs)
                if (PowerTelemetryEngine.isAutoLaunchAodEnabled != prefs.autoLaunchAodOnPlugIn) {
                    PowerTelemetryEngine.setAutoLaunchAodOnPlugIn(prefs.autoLaunchAodOnPlugIn)
                }
            }
        }

        serviceScope.launch {
            combine(
                PowerTelemetryEngine.state,
                PowerTelemetryEngine.designConfig,
                PowerTelemetryEngine.autoLaunchAodEnabledFlow,
            ) { state, design, autoAod ->
                Triple(state, design, autoAod)
            }.collect { (state, design, autoAod) ->
                autoLaunchAodEnabled = autoAod
                val keepAlive = state.settings.requiresBackgroundService || autoAod
                shouldStayPersistent = keepAlive

                syncHudOverlay(state, design)
                evaluateProtectionAlarms(state)
                updateForegroundNotification(state, autoAod)

                if (!keepAlive) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shouldStayPersistent = false
                PowerTelemetryEngine.updateSettings {
                    it.copy(
                        hudEnabled = false,
                        highChargeAlarmEnabled = false,
                        lowBatteryAlarmEnabled = false,
                        overheatAlarmEnabled = false,
                    )
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DISMISS_HUD -> {
                PowerTelemetryEngine.updateSettings { it.copy(hudEnabled = false) }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (shouldStayPersistent) {
            scheduleImmediateRestart(applicationContext)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(powerConnectionReceiver) }
        removeHudOverlay()
        PowerTelemetryEngine.setServiceRunning(false)
        serviceScope.cancel()
        if (shouldStayPersistent) {
            scheduleImmediateRestart(applicationContext)
        }
        super.onDestroy()
    }

    private fun startForegroundSafely(state: PowerTelemetryState) {
        val notification = buildOngoingNotification(state, autoLaunchAodEnabled)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                ONGOING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification(state: PowerTelemetryState, autoAod: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(state, autoAod))
    }

    private fun buildOngoingNotification(state: PowerTelemetryState, autoAod: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PowerMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val tempFormatted = state.settings.tempUnit.format(state.temperatureCelsius)
        val wattsFormatted = String.format(Locale.US, "%.1fW", state.wattage)
        val guardsLabel = buildList {
            if (state.settings.hudEnabled) add("HUD")
            if (state.settings.activeAlarmCount > 0) add("${state.settings.activeAlarmCount} Guards")
            if (autoAod) add("AOD Trigger")
        }.joinToString(" • ").ifEmpty { "Power Telemetry" }

        return NotificationCompat.Builder(this, CHANNEL_MONITOR_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("A-Kit Power • ${state.levelPercent}% ($wattsFormatted)")
            .setContentText("$tempFormatted • ${abs(state.currentNowMa)}mA • $guardsLabel")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, "Stop Guard", stopIntent)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun syncHudOverlay(state: PowerTelemetryState, design: DesignConfigSnapshot) {
        val canDraw = Settings.canDrawOverlays(this)
        if (!state.settings.hudEnabled || !canDraw) {
            removeHudOverlay()
            return
        }

        if (hudContainer == null) {
            createHudView(state)
        }
        updateHudAppearanceAndText(state, design)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createHudView(state: PowerTelemetryState) {
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        val padH = (12 * density).roundToInt()
        val padV = (7 * density).roundToInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, padV, padH, padV)
            elevation = 8 * density
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }

        val metricsTv = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setSingleLine(true)
        }

        val subMetricsTv = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            setSingleLine(true)
            visibility = View.GONE
        }

        textColumn.addView(metricsTv)
        textColumn.addView(subMetricsTv)

        val closeTv = TextView(this).apply {
            text = "  ×"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.argb(190, 220, 230, 220))
            setOnClickListener {
                PowerTelemetryEngine.updateSettings { it.copy(hudEnabled = false) }
            }
        }

        container.addView(textColumn)
        container.addView(closeTv)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (24 * density).roundToInt()
            y = (110 * density).roundToInt()
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).roundToInt()
                    params.y = initialY + (event.rawY - initialTouchY).roundToInt()
                    runCatching { wm.updateViewLayout(container, params) }
                    true
                }
                else -> false
            }
        }

        runCatching {
            wm.addView(container, params)
            hudContainer = container
            hudMetricsText = metricsTv
            hudSubMetricsText = subMetricsTv
            hudCloseText = closeTv
            hudParams = params
        }
    }

    private fun updateHudAppearanceAndText(state: PowerTelemetryState, design: DesignConfigSnapshot) {
        val container = hudContainer ?: return
        val metricsTv = hudMetricsText ?: return
        val subMetricsTv = hudSubMetricsText ?: return

        val accentArgb = when (design.accentSwatch) {
            AccentSwatch.DYNAMIC_AUTO -> if (state.isCharging) 0xFF7CE7A7.toInt() else 0xFFF9B384.toInt()
            else -> design.accentSwatch.argbInt
        }
        val alphaByte = (state.settings.hudOpacity.coerceIn(0.4f, 1.0f) * 255).roundToInt()
        val density = resources.displayMetrics.density

        val boltPrefix = if (state.isCharging) "⚡" else "•"
        val wattsStr = String.format(Locale.US, "%.1fW", state.wattage)
        val maStr = "${if (state.currentNowMa >= 0) "+" else ""}${state.currentNowMa}mA"
        val tempStr = state.settings.tempUnit.format(state.temperatureCelsius)
        val voltStr = String.format(Locale.US, "%.2fV", state.rawVoltageMv / 1000f)

        when (design.floatingPillStyle) {
            FloatingPillStyle.COMPACT_SINGLE_LINE -> {
                val bgColor = Color.argb(alphaByte, 17, 20, 17)
                val strokeColor = Color.argb(
                    (alphaByte * 0.78f).roundToInt().coerceIn(80, 255),
                    Color.red(accentArgb),
                    Color.green(accentArgb),
                    Color.blue(accentArgb),
                )
                container.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 999f * density
                    setColor(bgColor)
                    setStroke((1.3f * density).roundToInt().coerceAtLeast(1), strokeColor)
                }
                metricsTv.text = "$boltPrefix $wattsStr  $maStr  $tempStr"
                metricsTv.setTextColor(accentArgb)
                subMetricsTv.visibility = View.GONE
            }

            FloatingPillStyle.TWO_ROW_BOXED -> {
                val bgColor = Color.argb(alphaByte.coerceAtLeast(215), 22, 26, 22)
                val strokeColor = Color.argb(
                    (alphaByte * 0.72f).roundToInt().coerceIn(80, 255),
                    Color.red(accentArgb),
                    Color.green(accentArgb),
                    Color.blue(accentArgb),
                )
                container.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14f * density
                    setColor(bgColor)
                    setStroke((1.4f * density).roundToInt().coerceAtLeast(1), strokeColor)
                }
                metricsTv.text = "$boltPrefix $wattsStr  •  ${state.levelPercent}%"
                metricsTv.setTextColor(accentArgb)
                subMetricsTv.visibility = View.VISIBLE
                subMetricsTv.text = "$maStr  •  $tempStr  •  $voltStr"
                subMetricsTv.setTextColor(Color.argb(220, 224, 232, 224))
            }

            FloatingPillStyle.GLASS_PILL -> {
                val glassAlpha = (alphaByte * 0.56f).roundToInt().coerceIn(95, 190)
                val bgColor = Color.argb(
                    glassAlpha,
                    (Color.red(accentArgb) * 0.16f + 16).roundToInt().coerceIn(0, 255),
                    (Color.green(accentArgb) * 0.16f + 18).roundToInt().coerceIn(0, 255),
                    (Color.blue(accentArgb) * 0.16f + 16).roundToInt().coerceIn(0, 255),
                )
                val strokeColor = Color.argb(
                    230,
                    Color.red(accentArgb),
                    Color.green(accentArgb),
                    Color.blue(accentArgb),
                )
                container.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 999f * density
                    setColor(bgColor)
                    setStroke((1.8f * density).roundToInt().coerceAtLeast(2), strokeColor)
                }
                metricsTv.text = "$boltPrefix $wattsStr • $tempStr • ${state.levelPercent}%"
                metricsTv.setTextColor(Color.WHITE)
                subMetricsTv.visibility = View.GONE
            }
        }
    }

    private fun removeHudOverlay() {
        val container = hudContainer ?: return
        runCatching { windowManager?.removeView(container) }
        hudContainer = null
        hudMetricsText = null
        hudSubMetricsText = null
        hudCloseText = null
        hudParams = null
    }

    private fun evaluateProtectionAlarms(state: PowerTelemetryState) {
        if (!state.hasLiveReading) return
        val settings = state.settings

        // 1. High Charge Limit Alarm
        if (settings.highChargeAlarmEnabled) {
            if (state.isCharging && state.levelPercent >= settings.highChargeThreshold) {
                if (!highAlarmLatched) {
                    highAlarmLatched = true
                    fireProtectionAlert(
                        notificationId = ALERT_HIGH_ID,
                        title = "Charge Limit Reached (${state.levelPercent}%)",
                        message = "Battery reached your ${settings.highChargeThreshold}% limit. Unplug to preserve cell longevity.",
                    )
                }
            } else if (state.levelPercent <= settings.highChargeThreshold - 2 || !state.isCharging) {
                highAlarmLatched = false
            }
        } else {
            highAlarmLatched = false
        }

        // 2. Low Battery Alert
        if (settings.lowBatteryAlarmEnabled) {
            if (!state.isCharging && state.levelPercent <= settings.lowBatteryThreshold) {
                if (!lowAlarmLatched) {
                    lowAlarmLatched = true
                    fireProtectionAlert(
                        notificationId = ALERT_LOW_ID,
                        title = "Low Battery Warning (${state.levelPercent}%)",
                        message = "Charge dropped to your ${settings.lowBatteryThreshold}% threshold. Connect charger soon.",
                    )
                }
            } else if (state.levelPercent >= settings.lowBatteryThreshold + 3 || state.isCharging) {
                lowAlarmLatched = false
            }
        } else {
            lowAlarmLatched = false
        }

        // 3. Overheat Thermal Alert
        if (settings.overheatAlarmEnabled) {
            if (state.temperatureCelsius >= settings.overheatThresholdC) {
                if (!overheatAlarmLatched) {
                    overheatAlarmLatched = true
                    val tempStr = settings.tempUnit.format(state.temperatureCelsius)
                    val limitStr = settings.tempUnit.format(settings.overheatThresholdC.toFloat())
                    fireProtectionAlert(
                        notificationId = ALERT_OVERHEAT_ID,
                        title = "Thermal Guard Alert ($tempStr)",
                        message = "Battery temperature exceeded $limitStr. Pause heavy load or fast charging.",
                    )
                }
            } else if (state.temperatureCelsius <= settings.overheatThresholdC - 2f) {
                overheatAlarmLatched = false
            }
        } else {
            overheatAlarmLatched = false
        }
    }

    private fun fireProtectionAlert(notificationId: Int, title: String, message: String) {
        PowerTelemetryEngine.setActiveAlert("$title — $message")
        triggerAlarmSoundAndVibration(applicationContext)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val openIntent = PendingIntent.getActivity(
            this,
            notificationId,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        nm.notify(notificationId, notification)
    }

    private fun createNotificationChannels() {
        ensureChannelsStatic(this)
    }

    companion object {
        private const val CHANNEL_MONITOR_ID = "akit_power_monitor_channel"
        private const val CHANNEL_ALERTS_ID = "akit_power_alerts_channel"
        private const val CHANNEL_AOD_LAUNCH_ID = "akit_aod_lockscreen_launch_channel"
        private const val ONGOING_NOTIFICATION_ID = 4101
        private const val ALERT_HIGH_ID = 4102
        private const val ALERT_LOW_ID = 4103
        private const val ALERT_OVERHEAT_ID = 4104
        const val AOD_LAUNCH_NOTIFICATION_ID = 4109
        private const val RESTART_REQUEST_CODE = 4190

        const val ACTION_SYNC = "com.example.feature.power.ACTION_SYNC"
        const val ACTION_STOP = "com.example.feature.power.ACTION_STOP"
        const val ACTION_DISMISS_HUD = "com.example.feature.power.ACTION_DISMISS_HUD"

        private fun ensureChannelsStatic(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR_ID,
                "Power Telemetry & HUD",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps live wattage HUD, lockscreen Charging AOD trigger, and battery guards active."
                setShowBadge(false)
            }

            val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Battery Protection Alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts for charge limit, low battery, and overheat thermal thresholds."
                enableVibration(true)
                setSound(
                    alarmSoundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }

            val aodLaunchChannel = NotificationChannel(
                CHANNEL_AOD_LAUNCH_ID,
                "Charging AOD Lockscreen Trigger",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Wakes and displays the AMOLED Charging AOD screen over the lockscreen on plug-in."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            nm.createNotificationChannel(monitorChannel)
            nm.createNotificationChannel(alertChannel)
            nm.createNotificationChannel(aodLaunchChannel)
        }

        /**
         * Launches [ChargingAodActivity] with zero delay on charger plug-in—compatible with
         * HyperOS / MIUI and Android 14+ background activity launch restrictions:
         * 1. Acquires a short [PowerManager.SCREEN_BRIGHT_WAKE_LOCK] + [PowerManager.ACQUIRE_CAUSES_WAKEUP]
         *    to wake the screen immediately when power connects while the display is off.
         * 2. Posts a high-priority `FullScreenIntent` notification (using `USE_FULL_SCREEN_INTENT`)
         *    so the OS reliably brings [ChargingAodActivity] directly above the lockscreen.
         * 3. Also fires direct `startActivity()` when overlay or background pop-up permission is granted.
         */
        fun isWithinHideSchedule(
            enabled: Boolean,
            fromMinutes: Int,
            untilMinutes: Int,
        ): Boolean {
            if (!enabled || fromMinutes == untilMinutes) return false
            val cal = java.util.Calendar.getInstance()
            val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            return if (fromMinutes < untilMinutes) {
                nowMinutes in fromMinutes until untilMinutes
            } else {
                // Overnight window (e.g. 22:00 -> 06:00)
                nowMinutes >= fromMinutes || nowMinutes < untilMinutes
            }
        }

        @SuppressLint("WakelockTimeout")
        fun launchChargingAodImmediately(
            context: Context,
            isManualPreview: Boolean = false,
        ) {
            val app = context.applicationContext
            val prefs = PowerTelemetryEngine.designConfig.value.preferences
            if (!isManualPreview && isWithinHideSchedule(
                    enabled = prefs.hideOnScheduleEnabled,
                    fromMinutes = prefs.hideScheduleFromMinutes,
                    untilMinutes = prefs.hideScheduleUntilMinutes,
                )
            ) {
                return
            }

            ensureChannelsStatic(app)
            PowerTelemetryEngine.pollFreshHardwareSample(recordSample = true)

            val aodIntent = Intent(app, ChargingAodActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
                putExtra(ChargingAodActivity.EXTRA_LAUNCHED_FROM_PLUGIN, !isManualPreview)
                putExtra(ChargingAodActivity.EXTRA_IS_MANUAL_PREVIEW, isManualPreview)
            }

            // 1. Always wake the display immediately when power connects while locked or screen is off
            runCatching {
                val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null) {
                    @Suppress("DEPRECATION")
                    val wl = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "AKit:ChargingAodWake",
                    )
                    wl.setReferenceCounted(false)
                    wl.acquire(4_500L)
                }
            }

            // 2. Always fire high-priority FullScreenIntent notification (Android 10–15 & HyperOS lockscreen wake)
            val fullScreenPi = PendingIntent.getActivity(
                app,
                AOD_LAUNCH_NOTIFICATION_ID,
                aodIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(app, CHANNEL_AOD_LAUNCH_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("Charging AOD Active")
                .setContentText("Tap to open live AMOLED charging screen")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(false)
                .setTimeoutAfter(6_000L)
                .setContentIntent(fullScreenPi)
                .setFullScreenIntent(fullScreenPi, true)
                .build()
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            runCatching { nm?.notify(AOD_LAUNCH_NOTIFICATION_ID, notification) }

            // 3. Direct zero-delay launch (succeeds immediately when overlay / HyperOS pop-up permission is granted)
            runCatching { app.startActivity(aodIntent) }
        }

        fun dismissChargingAodImmediately(context: Context) {
            val app = context.applicationContext
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            runCatching { nm?.cancel(AOD_LAUNCH_NOTIFICATION_ID) }
            val dismissIntent = Intent(ChargingAodActivity.ACTION_DISMISS_AOD).apply {
                setPackage(app.packageName)
            }
            runCatching { app.sendBroadcast(dismissIntent) }
        }

        fun syncServiceState(
            context: Context,
            settings: PowerSettings,
            autoLaunchAodOnPlugIn: Boolean = PowerTelemetryEngine.isAutoLaunchAodEnabled,
        ) {
            val app = context.applicationContext
            val shouldRun = settings.requiresBackgroundService || autoLaunchAodOnPlugIn
            val intent = Intent(app, PowerMonitorService::class.java).apply {
                action = if (shouldRun) ACTION_SYNC else ACTION_STOP
            }
            if (shouldRun) {
                runCatching { app.startForegroundService(intent) }
            } else {
                runCatching { app.stopService(intent) }
            }
        }

        fun scheduleImmediateRestart(context: Context) {
            val app = context.applicationContext
            val restartIntent = Intent(app, PowerServiceRestartReceiver::class.java).apply {
                action = PowerServiceRestartReceiver.ACTION_RESTART_POWER_SERVICE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                app,
                RESTART_REQUEST_CODE,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val triggerAt = SystemClock.elapsedRealtime() + 1_200L
            runCatching {
                alarmManager?.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            }
        }

        fun triggerAlarmSoundAndVibration(context: Context) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, uri)
                ringtone?.play()
            }
            runCatching {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                val pattern = longArrayOf(0, 180, 110, 220)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        }
    }
}
