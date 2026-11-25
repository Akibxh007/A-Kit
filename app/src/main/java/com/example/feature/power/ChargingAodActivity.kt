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
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.example.data.ClockNumberStyle
import com.example.data.GaugeStyle
import com.example.data.ThemeMode
import com.example.ui.theme.AKitTheme
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.MontserratAlternatesFontFamily
import com.example.ui.theme.RobotoCondensedFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class ChargingAodActivity : ComponentActivity() {

    private var torchEnabled = false

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == ACTION_DISMISS_AOD || action == Intent.ACTION_POWER_DISCONNECTED) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PowerTelemetryEngine.ensureInitialized(applicationContext)

        // Show immediately over the lockscreen & turn screen on with zero delay
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
        // True AMOLED pitch-black window background (zero dark-grey bleed)
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
            AKitTheme(
                themeMode = ThemeMode.AMOLED,
                dynamicColor = false,
                gaugeStyle = designConfig.gaugeStyle,
                floatingPillStyle = designConfig.floatingPillStyle,
                clockNumberStyle = designConfig.clockNumberStyle,
                accentSwatch = designConfig.accentSwatch,
            ) {
                ChargingAodScreen(
                    onExit = { finish() },
                    onOpenFullDashboard = { openMainDashboard() },
                    onToggleFlashlight = { desired -> toggleTorchSafely(desired) },
                    onLaunchCamera = { launchCameraShortcut() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PowerTelemetryEngine.setAodVisible(true)
        // Clear any transient full-screen lockscreen launch notification once visible
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
    }
}

@Composable
private fun ChargingAodScreen(
    onExit: () -> Unit,
    onOpenFullDashboard: () -> Unit,
    onToggleFlashlight: (Boolean) -> Boolean,
    onLaunchCamera: () -> Unit,
) {
    val state by PowerTelemetryEngine.state.collectAsStateWithLifecycle()
    val designConfig by PowerTelemetryEngine.designConfig.collectAsStateWithLifecycle()
    val settings = state.settings

    // Resolve crisp AMOLED accent respecting Design Studio accent swatch and AOD preset
    val accent = remember(designConfig.accentSwatch, settings.aodAccent, state.isCharging) {
        when {
            settings.aodAccent != PowerAccentPreset.CYAN -> settings.aodAccent.composeColor
            designConfig.accentSwatch == AccentSwatch.DYNAMIC_AUTO ->
                if (state.isCharging) Color(0xFF7CE7A7) else Color(0xFFF9B384)
            else -> Color(designConfig.accentSwatch.argbInt)
        }
    }

    val clockFontFamily = when (designConfig.clockNumberStyle) {
        ClockNumberStyle.MONTSERRAT_BOLD -> MontserratAlternatesFontFamily
        ClockNumberStyle.CONDENSED_TECH -> RobotoCondensedFontFamily
        ClockNumberStyle.OUTLINE_DIGITAL -> MontserratAlternatesFontFamily
    }
    val clockFontWeight = when (designConfig.clockNumberStyle) {
        ClockNumberStyle.MONTSERRAT_BOLD -> FontWeight.Bold
        ClockNumberStyle.CONDENSED_TECH -> FontWeight.SemiBold
        ClockNumberStyle.OUTLINE_DIGITAL -> FontWeight.Light
    }
    val clockLetterSpacing = when (designConfig.clockNumberStyle) {
        ClockNumberStyle.MONTSERRAT_BOLD -> (-1.5).sp
        ClockNumberStyle.CONDENSED_TECH -> 0.sp
        ClockNumberStyle.OUTLINE_DIGITAL -> 3.sp
    }

    val context = LocalContext.current
    var flashlightOn by remember { mutableStateOf(false) }
    var showMoreInfo by remember { mutableStateOf(false) }

    // Dedicated 1-second live telemetry + digital clock ticker so decimal % and wattage never freeze
    var currentTimeText by remember { mutableStateOf(formatCleanClockNow(context)) }
    var currentDateText by remember { mutableStateOf(formatDateNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeText = formatCleanClockNow(context)
            currentDateText = formatDateNow()
            withContext(Dispatchers.Default) {
                PowerTelemetryEngine.pollFreshHardwareSample(recordSample = true)
            }
            delay(1_000L)
        }
    }

    // Ultra-subtle OLED burn-in protection: 1–2px micro-drift every 60s via graphicsLayer
    var microDriftStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            microDriftStep = (microDriftStep + 1) % 8
        }
    }

    val driftOffsetsPx = remember {
        listOf(
            0f to 0f,
            1.5f to -1.5f,
            2f to 0.5f,
            1f to 2f,
            -1.5f to 1.5f,
            -2f to -0.5f,
            -1f to -2f,
            0.5f to -1f,
        )
    }
    val (targetDx, targetDy) = driftOffsetsPx[microDriftStep]
    val smoothDriftX by animateFloatAsState(
        targetValue = targetDx,
        animationSpec = tween(durationMillis = 2_400, easing = LinearEasing),
        label = "oled_micro_drift_x",
    )
    val smoothDriftY by animateFloatAsState(
        targetValue = targetDy,
        animationSpec = tween(durationMillis = 2_400, easing = LinearEasing),
        label = "oled_micro_drift_y",
    )

    var showStyleControls by remember { mutableStateOf(false) }
    LaunchedEffect(showStyleControls) {
        if (showStyleControls) {
            delay(4_500L)
            showStyleControls = false
        }
    }

    val livePowerText = remember(state.isCharging, state.wattage) {
        val formatted = String.format(Locale.US, "%.2f W", state.wattage)
        if (state.isCharging) formatted else "-$formatted"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onExit() },
                    onTap = { showStyleControls = !showStyleControls },
                )
            }
            .testTag("charging_aod_root"),
    ) {
        // Spacious AmpereFlow Full-Screen Vertical Composition: Top Anchor (80.dp) | Center Hero | Bottom Anchor
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = smoothDriftX
                    translationY = smoothDriftY
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 80.dp, bottom = 32.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. TOP ANCHOR: Bold 78.sp Clean Clock (No cramped AM/PM) + Date Subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = currentTimeText,
                    fontFamily = clockFontFamily,
                    fontWeight = clockFontWeight,
                    fontSize = 78.sp,
                    lineHeight = 82.sp,
                    letterSpacing = clockLetterSpacing,
                    color = if (designConfig.clockNumberStyle == ClockNumberStyle.OUTLINE_DIGITAL) {
                        accent
                    } else {
                        Color(0xFFF5F8FC)
                    },
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("aod_clock_text"),
                )
                Text(
                    text = currentDateText,
                    style = TextStyle(
                        fontFamily = RobotoCondensedFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        letterSpacing = 1.2.sp,
                    ),
                    color = Color(0xFF8A99AA),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("aod_date_text"),
                )
            }

            // 2. CENTER HERO: Prominent 240.dp 270-Degree Arc/Ring Gauge + 3-Column Metric Boxes + "More Info"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(),
            ) {
                when (settings.aodStyle) {
                    AodVisualStyle.SPEEDOMETER_RING -> Aod270ArcRingGauge(
                        state = state,
                        accent = accent,
                        gaugeStyle = designConfig.gaugeStyle,
                        clockNumberStyle = designConfig.clockNumberStyle,
                    )
                    AodVisualStyle.PLASMA_WAVE -> AodEnergyWaveRing(
                        state = state,
                        accent = accent,
                    )
                    AodVisualStyle.MINIMAL_DIGITAL -> AodMinimalDigitalCluster(
                        state = state,
                        accent = accent,
                    )
                }

                // 3-Column Outlined Metric Boxes: Temp | Voltage | Estimate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AodOutlinedMetricBox(
                        icon = Icons.Rounded.Thermostat,
                        label = "TEMP",
                        value = settings.tempUnit.format(state.temperatureCelsius),
                        accent = accent,
                        modifier = Modifier.weight(1f),
                    )
                    AodOutlinedMetricBox(
                        icon = Icons.Rounded.ElectricBolt,
                        label = "VOLTAGE",
                        value = String.format(Locale.US, "%.2f V", state.rawVoltageMv / 1000f),
                        accent = accent,
                        modifier = Modifier.weight(1f),
                    )
                    AodOutlinedMetricBox(
                        icon = Icons.Rounded.Schedule,
                        label = "ESTIMATE",
                        value = state.estimatedTimeShort,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Subtle clickable "More Info" link + expandable inline live telemetry drawer
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { showMoreInfo = !showMoreInfo }
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

            // 3. BOTTOM ANCHOR: Optional Tap-Revealed Style Chips + Live Wattage Readout flanked by Flashlight & Camera
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = showStyleControls,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(380)),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            onClick = {
                                val styles = AodVisualStyle.entries
                                val next = styles[(styles.indexOf(settings.aodStyle) + 1) % styles.size]
                                PowerTelemetryEngine.updateSettings { it.copy(aodStyle = next) }
                            },
                            shape = CircleShape,
                            color = Color.Black,
                            border = BorderStroke(1.dp, Color(0xFF232E3F)),
                            modifier = Modifier.testTag("aod_cycle_style_button"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Style,
                                    contentDescription = "Switch AOD style",
                                    tint = accent,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = settings.aodStyle.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFD6E0EC),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                val colors = PowerAccentPreset.entries
                                val next = colors[(colors.indexOf(settings.aodAccent) + 1) % colors.size]
                                PowerTelemetryEngine.updateSettings { it.copy(aodAccent = next) }
                            },
                            shape = CircleShape,
                            color = Color.Black,
                            border = BorderStroke(1.dp, Color(0xFF232E3F)),
                            modifier = Modifier.testTag("aod_cycle_accent_button"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = "Switch AOD accent",
                                    tint = accent,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = settings.aodAccent.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFD6E0EC),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }

                // Bottom Bar: 44.dp Circular Flashlight | Centered Live Wattage Readout | 44.dp Circular Camera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        onClick = { flashlightOn = onToggleFlashlight(!flashlightOn) },
                        shape = CircleShape,
                        color = if (flashlightOn) accent.copy(alpha = 0.22f) else Color.Black,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (flashlightOn) accent else Color(0xFF222B36),
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("aod_flashlight_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (flashlightOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                contentDescription = "Toggle Flashlight",
                                tint = if (flashlightOn) accent else Color(0xFF9AA8B8),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Live Wattage Readout (e.g. 8.44 W) centered at the bottom anchor
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
                            text = "DOUBLE-TAP TO EXIT",
                            style = MonoLabelStyle.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                            color = Color(0xFF566373),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    Surface(
                        onClick = onLaunchCamera,
                        shape = CircleShape,
                        color = Color.Black,
                        border = BorderStroke(1.dp, Color(0xFF222B36)),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("aod_camera_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Open Camera",
                                tint = Color(0xFF9AA8B8),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Prominent 240.dp 270-Degree AmpereFlow Arc/Ring Gauge for AMOLED Charging AOD,
 * honoring the user's selected Design Studio [GaugeStyle] and [ClockNumberStyle].
 */
@Composable
private fun Aod270ArcRingGauge(
    state: PowerTelemetryState,
    accent: Color,
    gaugeStyle: GaugeStyle,
    clockNumberStyle: ClockNumberStyle,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (state.exactLevelPercent / 100f).coerceIn(0.01f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "aod_arc_progress",
    )

    val formattedExact = String.format(Locale.US, "%.2f", state.exactLevelPercent)
    val integerPart = formattedExact.substringBefore(".")
    val decimalPart = "." + formattedExact.substringAfter(".")

    val numberFontFamily = when (clockNumberStyle) {
        ClockNumberStyle.MONTSERRAT_BOLD -> MontserratAlternatesFontFamily
        ClockNumberStyle.CONDENSED_TECH -> RobotoCondensedFontFamily
        ClockNumberStyle.OUTLINE_DIGITAL -> MontserratAlternatesFontFamily
    }

    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startAngle = 135f
            val totalSweep = 270f
            val activeSweep = totalSweep * animatedProgress
            val center = Offset(size.width / 2f, size.height / 2f)
            val trackColor = Color(0xFF171E27)

            when (gaugeStyle) {
                GaugeStyle.MINIMAL_THIN_ARC -> {
                    val strokePx = 9.dp.toPx()
                    val inset = strokePx + 6.dp.toPx()
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    val topLeft = Offset(inset, inset)

                    drawArc(
                        color = trackColor,
                        startAngle = startAngle,
                        sweepAngle = totalSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = accent.copy(alpha = 0.2f),
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx * 2.1f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = accent,
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )

                    val tipRad = (startAngle + activeSweep) * PI / 180.0
                    val radius = arcSize.width / 2f
                    drawCircle(
                        color = Color.White,
                        radius = strokePx * 0.44f,
                        center = Offset(
                            center.x + (radius * cos(tipRad)).toFloat(),
                            center.y + (radius * sin(tipRad)).toFloat(),
                        ),
                    )
                }

                GaugeStyle.THICK_RING -> {
                    val strokePx = 18.dp.toPx()
                    val inset = strokePx / 2f + 6.dp.toPx()
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    val topLeft = Offset(inset, inset)

                    drawArc(
                        color = trackColor,
                        startAngle = startAngle,
                        sweepAngle = totalSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            0.0f to accent.copy(alpha = 0.72f),
                            0.55f to accent,
                            1.0f to accent.copy(alpha = 0.92f),
                            center = center,
                        ),
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }

                GaugeStyle.SEGMENTED_TICK -> {
                    val tickCount = 46
                    val activeTicks = (animatedProgress * tickCount).roundToInt().coerceIn(0, tickCount)
                    val outerRadius = size.minDimension / 2f - 4.dp.toPx()
                    val innerRadius = outerRadius - 15.dp.toPx()

                    for (i in 0 until tickCount) {
                        val fraction = i.toFloat() / (tickCount - 1).coerceAtLeast(1)
                        val angleDeg = startAngle + (totalSweep * fraction)
                        val rad = angleDeg * PI / 180.0
                        val isFilled = i < activeTicks
                        val isLead = i == activeTicks - 1 && activeTicks > 0

                        val start = Offset(
                            center.x + (innerRadius * cos(rad)).toFloat(),
                            center.y + (innerRadius * sin(rad)).toFloat(),
                        )
                        val end = Offset(
                            center.x + (outerRadius * cos(rad)).toFloat(),
                            center.y + (outerRadius * sin(rad)).toFloat(),
                        )

                        drawLine(
                            color = when {
                                isLead -> Color.White
                                isFilled -> accent.copy(alpha = 0.5f + 0.5f * fraction)
                                else -> trackColor
                            },
                            start = start,
                            end = end,
                            strokeWidth = if (isLead) 4.dp.toPx() else 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = integerPart,
                    fontFamily = numberFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 54.sp,
                    letterSpacing = (-1.5).sp,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = "$decimalPart%",
                    style = MonoMetricStyle.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                    color = accent,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
            }
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = if (state.isCharging) "CHARGING" else "DISCHARGING",
                    style = MonoLabelStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = accent,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun AodEnergyWaveRing(
    state: PowerTelemetryState,
    accent: Color,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aod_wave_transition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave_phase",
    )

    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 6.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val circlePath = Path().apply {
                addOval(
                    Rect(
                        left = center.x - radius,
                        top = center.y - radius,
                        right = center.x + radius,
                        bottom = center.y + radius,
                    ),
                )
            }

            clipPath(circlePath) {
                val fillFraction = (state.exactLevelPercent / 100f).coerceIn(0.08f, 0.96f)
                val baseY = center.y + radius - (radius * 2f * fillFraction)
                val amplitude = 8.dp.toPx()

                val backWave = Path().apply {
                    moveTo(0f, size.height)
                    var x = 0f
                    while (x <= size.width) {
                        val y = baseY + sin((x / size.width) * 2.5f * PI + phase + 1.4f).toFloat() * amplitude
                        lineTo(x, y)
                        x += 6f
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path = backWave, color = accent.copy(alpha = 0.16f))

                val frontWave = Path().apply {
                    moveTo(0f, size.height)
                    var x = 0f
                    while (x <= size.width) {
                        val y = baseY + sin((x / size.width) * 2.2f * PI - phase).toFloat() * (amplitude * 0.85f)
                        lineTo(x, y)
                        x += 6f
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(
                    path = frontWave,
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.08f)),
                        startY = baseY - amplitude,
                        endY = size.height,
                    ),
                )
            }

            drawCircle(
                color = accent.copy(alpha = 0.55f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.US, "%.2f%%", state.exactLevelPercent),
                fontFamily = MontserratAlternatesFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = if (state.isCharging) "CHARGING" else "DISCHARGING",
                style = MonoLabelStyle,
                color = accent,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun AodMinimalDigitalCluster(
    state: PowerTelemetryState,
    accent: Color,
) {
    Column(
        modifier = Modifier
            .height(240.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = String.format(Locale.US, "%.2f%%", state.exactLevelPercent),
            fontFamily = MontserratAlternatesFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 58.sp,
            letterSpacing = (-2).sp,
            color = accent,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${state.statusLabel.uppercase(Locale.US)} • ${state.pluggedLabel.uppercase(Locale.US)}",
            style = MonoLabelStyle,
            color = Color(0xFF7D8B9B),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun AodOutlinedMetricBox(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.Black,
        border = BorderStroke(1.dp, Color(0xFF1F2A37)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = label,
                    style = MonoLabelStyle.copy(fontSize = 10.sp),
                    color = Color(0xFF7F8E9F),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = MontserratAlternatesFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                color = Color(0xFFEAF2FA),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private fun formatCleanClockNow(context: Context): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
}

private fun formatDateNow(): String =
    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
