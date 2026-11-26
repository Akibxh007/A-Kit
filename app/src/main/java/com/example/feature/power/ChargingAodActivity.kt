package com.example.feature.power

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.example.data.AccentSwatch
import com.example.data.ThemeMode
import com.example.feature.aod.AodBatteryDetailsRow
import com.example.feature.aod.AodClockDisplay
import com.example.feature.aod.AodMediaControlsBar
import com.example.feature.aod.AodNotificationBridgeService
import com.example.feature.aod.AodNotificationIconsRow
import com.example.feature.aod.AodSpeedometerGauge
import com.example.feature.aod.formatAodDate
import com.example.feature.aod.formatClockTime
import com.example.feature.aod.resolveAodMetrics
import com.example.ui.theme.AKitTheme
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.theme.MontserratAlternatesFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ChargingAodActivity : ComponentActivity() {

    private var torchEnabled = false
    private var isManualPreview = false

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == ACTION_DISMISS_AOD) {
                finish()
            } else if (action == Intent.ACTION_POWER_DISCONNECTED && !isManualPreview) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PowerTelemetryEngine.ensureInitialized(applicationContext)
        isManualPreview = intent?.getBooleanExtra(EXTRA_IS_MANUAL_PREVIEW, false) == true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)

        enableEdgeToEdge()
        hideSystemBars()

        val filter = IntentFilter().apply {
            addAction(ACTION_DISMISS_AOD)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dismissReceiver, filter)
        }

        setContent {
            val designConfig by PowerTelemetryEngine.designConfig.collectAsStateWithLifecycle()
            val prefs = designConfig.preferences
            AKitTheme(
                themeMode = ThemeMode.AMOLED,
                dynamicColor = false,
                gaugeStyle = prefs.gaugeStyle,
                floatingPillStyle = prefs.floatingPillStyle,
                clockNumberStyle = prefs.clockNumberStyle,
                accentSwatch = prefs.accentSwatch,
                use24HourTime = prefs.use24HourTime,
            ) {
                ChargingAodScreen(
                    isManualPreview = isManualPreview,
                    onExit = { finish() },
                    onOpenFullDashboard = { openMainDashboard() },
                    onToggleFlashlight = { desired -> toggleTorchSafely(desired) },
                    onLaunchCamera = { launchCameraShortcut() },
                    onApplyWindowDim = { dimActive -> applyWindowBrightness(dimActive) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isManualPreview = intent.getBooleanExtra(EXTRA_IS_MANUAL_PREVIEW, false)
    }

    override fun onStart() {
        super.onStart()
        PowerTelemetryEngine.setAodVisible(true)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { nm?.cancel(PowerMonitorService.AOD_LAUNCH_NOTIFICATION_ID) }
    }

    override fun onStop() {
        PowerTelemetryEngine.setAodVisible(false)
        if (torchEnabled) {
            toggleTorchSafely(false)
        }
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dismissReceiver) }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun applyWindowBrightness(dimActive: Boolean) {
        runCatching {
            val attrs = window.attributes
            attrs.screenBrightness = if (dimActive) 0.04f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = attrs
        }
    }

    private fun openMainDashboard() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(intent) }
        finish()
    }

    private fun toggleTorchSafely(enable: Boolean): Boolean {
        return runCatching {
            val cm = getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            val backCameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cm.setTorchMode(backCameraId, enable)
            torchEnabled = enable
            enable
        }.getOrDefault(false)
    }

    private fun launchCameraShortcut() {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        const val ACTION_DISMISS_AOD = "com.example.feature.power.ACTION_DISMISS_AOD"
        const val EXTRA_LAUNCHED_FROM_PLUGIN = "extra_launched_from_plugin"
        const val EXTRA_IS_MANUAL_PREVIEW = "extra_is_manual_preview"
    }
}

@Composable
private fun ChargingAodScreen(
    isManualPreview: Boolean,
    onExit: () -> Unit,
    onOpenFullDashboard: () -> Unit,
    onToggleFlashlight: (Boolean) -> Boolean,
    onLaunchCamera: () -> Unit,
    onApplyWindowDim: (Boolean) -> Unit,
) {
    val state by PowerTelemetryEngine.state.collectAsStateWithLifecycle()
    val designConfig by PowerTelemetryEngine.designConfig.collectAsStateWithLifecycle()
    val mediaSnapshot by AodNotificationBridgeService.mediaSnapshot.collectAsStateWithLifecycle()
    val activeNotifications by AodNotificationBridgeService.notifications.collectAsStateWithLifecycle()

    val prefs = designConfig.preferences
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Resolve crisp AMOLED accent honoring Design Studio 10-swatch palette
    val accent = remember(prefs.accentSwatch, state.isCharging) {
        when (prefs.accentSwatch) {
            AccentSwatch.DYNAMIC_AUTO -> if (state.isCharging) Color(0xFF7CE7A7) else Color(0xFFF9B384)
            else -> Color(prefs.accentSwatch.argbInt)
        }
    }

    var flashlightOn by remember { mutableStateOf(false) }
    var showMoreInfo by remember { mutableStateOf(false) }
    var showTapHud by remember { mutableStateOf(isManualPreview) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isDimmed by remember { mutableStateOf(false) }
    var isPowerSavingIdle by remember { mutableStateOf(false) }

    // 1-second live telemetry + clock/date + behavior timers (Power Saving, Dim After, Hide After)
    var currentTimeText by remember(prefs.use24HourTime) {
        mutableStateOf(formatClockTime(prefs.use24HourTime))
    }
    var currentDateText by remember(prefs.dateFormatOption) {
        mutableStateOf(formatAodDate(prefs.dateFormatOption))
    }

    LaunchedEffect(
        prefs.use24HourTime,
        prefs.dateFormatOption,
        prefs.powerSavingMode,
        prefs.dimScreenAfter,
        prefs.hideScreenAfter,
    ) {
        while (true) {
            val nowDate = Date()
            val nowMs = nowDate.time
            currentTimeText = formatClockTime(prefs.use24HourTime, nowDate)
            currentDateText = formatAodDate(prefs.dateFormatOption, nowDate)

            val idleMs = (nowMs - lastInteractionMs).coerceAtLeast(0L)
            isPowerSavingIdle = prefs.powerSavingMode && idleMs >= 30_000L

            val dimDelay = prefs.dimScreenAfter.delayMs
            val shouldDim = dimDelay != null && idleMs >= dimDelay
            if (shouldDim != isDimmed) {
                isDimmed = shouldDim
                onApplyWindowDim(shouldDim)
            }

            val hideDelay = prefs.hideScreenAfter.delayMs
            if (hideDelay != null && idleMs >= hideDelay) {
                onExit()
                break
            }

            withContext(Dispatchers.Default) {
                PowerTelemetryEngine.pollFreshHardwareSample(recordSample = true)
            }
            delay(1_000L)
        }
    }

    LaunchedEffect(showTapHud) {
        if (showTapHud) {
            delay(4_500L)
            showTapHud = false
        }
    }

    // OLED burn-in protection: subtle 2dp–4dp pixel shift every 60s when enabled
    var microDriftStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(prefs.oledBurnInProtection) {
        if (!prefs.oledBurnInProtection) {
            microDriftStep = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(60_000L)
            microDriftStep = (microDriftStep + 1) % 8
        }
    }

    val driftOffsetsDp = remember {
        listOf(
            0f to 0f,
            3f to -2.5f,
            3.5f to 1.5f,
            2f to 3.5f,
            -2.5f to 3f,
            -3.5f to -1f,
            -2f to -3.5f,
            1.5f to -2f,
        )
    }
    val (targetDx, targetDy) = if (prefs.oledBurnInProtection) driftOffsetsDp[microDriftStep] else (0f to 0f)
    val smoothDriftX by animateFloatAsState(
        targetValue = targetDx,
        animationSpec = tween(durationMillis = 2_200, easing = LinearEasing),
        label = "oled_drift_x",
    )
    val smoothDriftY by animateFloatAsState(
        targetValue = targetDy,
        animationSpec = tween(durationMillis = 2_200, easing = LinearEasing),
        label = "oled_drift_y",
    )

    val screenDimAlpha by animateFloatAsState(
        targetValue = if (isDimmed) 0.36f else 1.0f,
        animationSpec = tween(durationMillis = 650),
        label = "aod_dim_alpha",
    )

    val resolvedMetrics = remember(prefs.aodSelectedMetrics, state) {
        resolveAodMetrics(prefs.aodSelectedMetrics, state)
    }

    val livePowerText = remember(state.isCharging, state.wattage, state.currentNowMa, prefs.showWattsInsteadOfMa) {
        if (prefs.showWattsInsteadOfMa) {
            val formatted = String.format(Locale.US, "%.2f W", abs(state.wattage))
            if (state.isCharging) formatted else "-$formatted"
        } else {
            val sign = if (state.currentNowMa >= 0) "+" else ""
            "$sign${state.currentNowMa} mA"
        }
    }

    fun registerUserTouch() {
        lastInteractionMs = System.currentTimeMillis()
        if (isDimmed) {
            isDimmed = false
            onApplyWindowDim(false)
        }
        isPowerSavingIdle = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(prefs.doubleTapToExit) {
                detectTapGestures(
                    onDoubleTap = {
                        registerUserTouch()
                        if (prefs.doubleTapToExit) {
                            onExit()
                        } else {
                            showTapHud = true
                        }
                    },
                    onTap = {
                        registerUserTouch()
                        showTapHud = !showTapHud
                    },
                )
            }
            .testTag("charging_aod_root"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = smoothDriftX.dp.toPx()
                    translationY = smoothDriftY.dp.toPx()
                    alpha = screenDimAlpha
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 64.dp, bottom = 28.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. TOP ANCHOR: Clock (6 styles incl. OFF) + Date Format + Optional Notification Icons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AodClockDisplay(
                    clockStyle = prefs.clockNumberStyle,
                    timeText = currentTimeText,
                    dateText = currentDateText,
                    accent = accent,
                    compactMode = false,
                )

                if (prefs.showAodNotifications) {
                    AodNotificationIconsRow(
                        notifications = activeNotifications,
                        accent = accent,
                        compactMode = false,
                        showMockFallback = isManualPreview,
                    )
                }
            }

            // 2. CENTER HERO: 12-Style Speedometer Gauge + 4-Style Battery Details Row + Media + More Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(),
            ) {
                AodSpeedometerGauge(
                    levelPercent = if (state.hasLiveReading) state.exactLevelPercent else 72.4f,
                    wattage = if (state.hasLiveReading) state.wattage else 18.5f,
                    currentNowMa = if (state.hasLiveReading) state.currentNowMa else 3820,
                    isCharging = if (state.hasLiveReading) state.isCharging else true,
                    gaugeStyle = prefs.gaugeStyle,
                    clockNumberStyle = prefs.clockNumberStyle,
                    accentColor = accent,
                    gaugeSize = 232.dp,
                    compactMode = false,
                    amoledMode = true,
                    brightnessAlpha = prefs.speedometerBrightness,
                    percentagePrimaryDisplay = prefs.percentagePrimaryDisplay,
                    showWattsInsteadOfMa = prefs.showWattsInsteadOfMa,
                    powerSavingSlowAnim = isPowerSavingIdle,
                    statusBadgeText = if (state.isCharging) "CHARGING" else "DISCHARGING",
                )

                AodBatteryDetailsRow(
                    style = prefs.batteryDetailsStyle,
                    metrics = resolvedMetrics,
                    accent = accent,
                    compactMode = false,
                )

                if (prefs.showMediaControls) {
                    AodMediaControlsBar(
                        media = mediaSnapshot,
                        accent = accent,
                        onPrevious = {
                            registerUserTouch()
                            AodNotificationBridgeService.skipPrevious(context)
                        },
                        onPlayPause = {
                            registerUserTouch()
                            AodNotificationBridgeService.togglePlayPause(context)
                        },
                        onNext = {
                            registerUserTouch()
                            AodNotificationBridgeService.skipNext(context)
                        },
                        compactMode = false,
                        showMockFallback = isManualPreview,
                    )
                }

                if (prefs.showMoreInfoButton) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    registerUserTouch()
                                    showMoreInfo = !showMoreInfo
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .testTag("aod_more_info_link"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = if (showMoreInfo) "Hide Info" else "More Info",
                                style = MonoLabelStyle.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = Color(0xFF8C9BAE),
                                maxLines = 1,
                                softWrap = false,
                            )
                            Icon(
                                imageVector = if (showMoreInfo) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = "Toggle more charging info",
                                tint = Color(0xFF8C9BAE),
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        AnimatedVisibility(
                            visible = showMoreInfo,
                            enter = fadeIn(tween(200)) + expandVertically(),
                            exit = fadeOut(tween(180)) + shrinkVertically(),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black,
                                border = BorderStroke(1.dp, Color(0xFF1D2733)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${if (state.currentNowMa >= 0) "+" else ""}${state.currentNowMa} mA • ${state.pluggedLabel} • Peak ${String.format(Locale.US, "%.1fW", state.peakSessionWatts)}",
                                        style = MonoLabelStyle.copy(fontSize = 11.sp),
                                        color = Color(0xFFCBD5E1),
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Surface(
                                        onClick = onOpenFullDashboard,
                                        shape = CircleShape,
                                        color = accent.copy(alpha = 0.16f),
                                        modifier = Modifier.testTag("aod_open_dashboard_button"),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = "OPEN",
                                                style = MonoLabelStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                color = accent,
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.OpenInNew,
                                                contentDescription = "Open Power Dashboard",
                                                tint = accent,
                                                modifier = Modifier.size(12.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. BOTTOM ANCHOR: Optional Exit Pill (for preview or double-tap OFF) + Hold-to-Trigger Torch | Live Power | Camera
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimatedVisibility(
                    visible = showTapHud || !prefs.doubleTapToExit,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(300)),
                ) {
                    Surface(
                        onClick = onExit,
                        shape = CircleShape,
                        color = Color(0xFF10151C),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("aod_exit_chip"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Exit AOD",
                                tint = accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = if (isManualPreview) "EXIT PREVIEW" else "EXIT AOD",
                                style = MonoLabelStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                                color = Color(0xFFDDE7F2),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (prefs.showTorchButton) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (flashlightOn) accent.copy(alpha = 0.24f) else Color.Black,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (flashlightOn) accent else Color(0xFF222B36),
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .pointerInput(flashlightOn) {
                                        detectTapGestures(
                                            onLongPress = {
                                                registerUserTouch()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                flashlightOn = onToggleFlashlight(!flashlightOn)
                                            },
                                            onDoubleTap = {
                                                registerUserTouch()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                flashlightOn = onToggleFlashlight(!flashlightOn)
                                            },
                                        )
                                    }
                                    .testTag("aod_flashlight_button"),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (flashlightOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                        contentDescription = "Hold to toggle flashlight",
                                        tint = if (flashlightOn) accent else Color(0xFF9AA8B8),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Text(
                                text = "HOLD",
                                style = MonoLabelStyle.copy(fontSize = 8.5.sp),
                                color = Color(0xFF515E6E),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    // Centered Live Wattage / mA readout
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = livePowerText,
                                style = TextStyle(
                                    fontFamily = MontserratAlternatesFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    letterSpacing = (-0.5).sp,
                                ),
                                color = accent,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.testTag("aod_bottom_wattage_text"),
                            )
                        }
                        Text(
                            text = if (prefs.doubleTapToExit) "DOUBLE-TAP TO EXIT" else "TAP FOR EXIT CONTROL",
                            style = MonoLabelStyle.copy(fontSize = 9.5.sp, letterSpacing = 0.9.sp),
                            color = Color(0xFF566373),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    if (prefs.showCameraButton) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black,
                                border = BorderStroke(1.dp, Color(0xFF222B36)),
                                modifier = Modifier
                                    .size(48.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = {
                                                registerUserTouch()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onLaunchCamera()
                                            },
                                            onDoubleTap = {
                                                registerUserTouch()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onLaunchCamera()
                                            },
                                        )
                                    }
                                    .testTag("aod_camera_button"),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.CameraAlt,
                                        contentDescription = "Hold to open camera",
                                        tint = Color(0xFF9AA8B8),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Text(
                                text = "HOLD",
                                style = MonoLabelStyle.copy(fontSize = 8.5.sp),
                                color = Color(0xFF515E6E),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}
