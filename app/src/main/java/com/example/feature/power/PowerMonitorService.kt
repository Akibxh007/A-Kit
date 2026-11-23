package com.example.feature.power

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class PowerMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var hudContainer: LinearLayout? = null
    private var hudMetricsText: TextView? = null
    private var hudCloseText: TextView? = null
    private var hudParams: WindowManager.LayoutParams? = null

    private var highAlarmLatched = false
    private var lowAlarmLatched = false
    private var overheatAlarmLatched = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PowerTelemetryEngine.ensureInitialized(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        createNotificationChannels()
        startForegroundSafely(PowerTelemetryEngine.state.value)
        PowerTelemetryEngine.setServiceRunning(true)

        serviceScope.launch {
            PowerTelemetryEngine.state.collect { state ->
                syncHudOverlay(state)
                evaluateProtectionAlarms(state)
                updateForegroundNotification(state)

                if (!state.settings.requiresBackgroundService) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
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

    override fun onDestroy() {
        removeHudOverlay()
        PowerTelemetryEngine.setServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely(state: PowerTelemetryState) {
        val notification = buildOngoingNotification(state)
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

    private fun updateForegroundNotification(state: PowerTelemetryState) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(state))
    }

    private fun buildOngoingNotification(state: PowerTelemetryState): Notification {
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
    private fun syncHudOverlay(state: PowerTelemetryState) {
        val canDraw = Settings.canDrawOverlays(this)
        if (!state.settings.hudEnabled || !canDraw) {
            removeHudOverlay()
            return
        }

        if (hudContainer == null) {
            createHudView(state)
        }
        updateHudAppearanceAndText(state)
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

        val metricsTv = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.MONOSPACE
            setSingleLine(true)
        }

        val closeTv = TextView(this).apply {
            text = "  ×"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.argb(190, 220, 230, 240))
            setOnClickListener {
                PowerTelemetryEngine.updateSettings { it.copy(hudEnabled = false) }
            }
        }

        container.addView(metricsTv)
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
            hudCloseText = closeTv
            hudParams = params
        }
    }

    private fun updateHudAppearanceAndText(state: PowerTelemetryState) {
        val container = hudContainer ?: return
        val metricsTv = hudMetricsText ?: return

        val accentArgb = state.settings.hudAccent.argbInt
        val alphaByte = (state.settings.hudOpacity.coerceIn(0.4f, 1.0f) * 255).roundToInt()
        val bgColor = Color.argb(alphaByte, 9, 13, 19)
        val strokeColor = Color.argb((alphaByte * 0.78f).roundToInt().coerceIn(80, 255), Color.red(accentArgb), Color.green(accentArgb), Color.blue(accentArgb))
        val density = resources.displayMetrics.density

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f * density
            setColor(bgColor)
            setStroke((1.3f * density).roundToInt().coerceAtLeast(1), strokeColor)
        }
        container.background = bgDrawable

        val boltPrefix = if (state.isCharging) "⚡" else "•"
        val wattsStr = String.format(Locale.US, "%.1fW", state.wattage)
        val maStr = "${abs(state.currentNowMa)}mA"
        val tempStr = state.settings.tempUnit.format(state.temperatureCelsius)
        metricsTv.text = "$boltPrefix $wattsStr  $maStr  $tempStr"
        metricsTv.setTextColor(accentArgb)
    }

    private fun removeHudOverlay() {
        val container = hudContainer ?: return
        runCatching { windowManager?.removeView(container) }
        hudContainer = null
        hudMetricsText = null
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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR_ID,
            "Power Telemetry & HUD",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps live wattage HUD and battery protection guards running reliably."
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

        nm.createNotificationChannel(monitorChannel)
        nm.createNotificationChannel(alertChannel)
    }

    companion object {
        private const val CHANNEL_MONITOR_ID = "akit_power_monitor_channel"
        private const val CHANNEL_ALERTS_ID = "akit_power_alerts_channel"
        private const val ONGOING_NOTIFICATION_ID = 4101
        private const val ALERT_HIGH_ID = 4102
        private const val ALERT_LOW_ID = 4103
        private const val ALERT_OVERHEAT_ID = 4104

        const val ACTION_SYNC = "com.example.feature.power.ACTION_SYNC"
        const val ACTION_STOP = "com.example.feature.power.ACTION_STOP"
        const val ACTION_DISMISS_HUD = "com.example.feature.power.ACTION_DISMISS_HUD"

        fun syncServiceState(context: Context, settings: PowerSettings) {
            val app = context.applicationContext
            val intent = Intent(app, PowerMonitorService::class.java).apply {
                action = if (settings.requiresBackgroundService) ACTION_SYNC else ACTION_STOP
            }
            if (settings.requiresBackgroundService) {
                runCatching { app.startForegroundService(intent) }
            } else {
                runCatching { app.stopService(intent) }
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
