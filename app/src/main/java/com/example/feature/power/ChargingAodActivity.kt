package com.example.feature.power

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ThemeMode
import com.example.ui.theme.AKitTheme
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ChargingAodActivity : ComponentActivity() {

    private var torchEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PowerTelemetryEngine.ensureInitialized(applicationContext)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        setContent {
            AKitTheme(themeMode = ThemeMode.AMOLED, dynamicColor = false) {
                ChargingAodScreen(
                    onExit = { finish() },
                    onToggleFlashlight = { desired -> toggleTorchSafely(desired) },
                    onLaunchCamera = { launchCameraShortcut() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PowerTelemetryEngine.setAodVisible(true)
    }

    override fun onStop() {
        PowerTelemetryEngine.setAodVisible(false)
        if (torchEnabled) {
            toggleTorchSafely(false)
        }
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
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
}

@Composable
private fun ChargingAodScreen(
    onExit: () -> Unit,
    onToggleFlashlight: (Boolean) -> Boolean,
    onLaunchCamera: () -> Unit,
) {
    val state by PowerTelemetryEngine.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val accent = settings.aodAccent.composeColor

    var flashlightOn by remember { mutableStateOf(false) }

    // Live digital 12-hour clock (clean 11:48 format)
    var currentTimeText by remember { mutableStateOf(formatClock12HourNow()) }
    var currentMeridiemText by remember { mutableStateOf(formatMeridiemNow()) }
    var currentDateText by remember { mutableStateOf(formatDateNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeText = formatClock12HourNow()
            currentMeridiemText = formatMeridiemNow()
            currentDateText = formatDateNow()
            delay(1_000L)
        }
    }

    // Ultra-subtle OLED burn-in protection: 1–2px micro-drift every 60s via graphicsLayer (zero layout jumps)
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

    var showStyleControls by remember { mutableStateOf(true) }
    LaunchedEffect(showStyleControls) {
        if (showStyleControls) {
            delay(5_000L)
            showStyleControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onExit() },
                    onTap = { showStyleControls = true },
                )
            }
            .testTag("charging_aod_root"),
    ) {
        // Main OLED cluster with ultra-subtle graphicsLayer micro-drift (never triggers relayout)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    translationX = smoothDriftX
                    translationY = smoothDriftY
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Clock + Date header (12-hour clean format e.g. 11:48)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = currentTimeText,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Light,
                        fontSize = 60.sp,
                        letterSpacing = (-2).sp,
                        color = Color(0xFFEAF2F8),
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = currentMeridiemText,
                        style = MonoLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = accent.copy(alpha = 0.85f),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                Text(
                    text = currentDateText.uppercase(Locale.US),
                    style = MonoLabelStyle,
                    color = Color(0xFF7D8C9F),
                    maxLines = 1,
                    softWrap = false,
                )
            }

            // Selected AOD Visualization Style
            when (settings.aodStyle) {
                AodVisualStyle.SPEEDOMETER_RING -> AodSegmentedRingGauge(
                    state = state,
                    accent = accent,
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

            // Live Wattage & Estimate Pill
            Surface(
                shape = CircleShape,
                color = Color(0xFF0A0F16),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f W", state.wattage),
                        style = MonoMetricStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = accent,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = "•",
                        style = MonoLabelStyle,
                        color = Color(0xFF475569),
                    )
                    Text(
                        text = state.estimatedTimeLabel,
                        style = MonoLabelStyle.copy(fontSize = 12.sp),
                        color = Color(0xFFD6E2EE),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // Compact 3-Card Telemetry Row: Temp, Voltage, Estimate
            Row(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AodTelemetryCompactCard(
                    icon = Icons.Rounded.Thermostat,
                    label = "TEMP",
                    value = settings.tempUnit.format(state.temperatureCelsius),
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
                AodTelemetryCompactCard(
                    icon = Icons.Rounded.ElectricBolt,
                    label = if (state.effectiveDualCellActive) "VOLT 2S" else "VOLTAGE",
                    value = String.format(Locale.US, "%.2f V", state.effectiveVoltageMv / 1000f),
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
                AodTelemetryCompactCard(
                    icon = Icons.Rounded.Schedule,
                    label = "ESTIMATE",
                    value = state.estimatedTimeShort,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Fixed Bottom Quick Actions (Flashlight & Camera) + Subtle Style/Accent Chips
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AnimatedVisibility(
                visible = showStyleControls,
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(450)),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = {
                            val styles = AodVisualStyle.entries
                            val next = styles[(styles.indexOf(settings.aodStyle) + 1) % styles.size]
                            PowerTelemetryEngine.updateSettings { it.copy(aodStyle = next) }
                        },
                        shape = CircleShape,
                        color = Color(0xFF0E131B),
                        border = BorderStroke(1.dp, Color(0xFF232E3F)),
                        modifier = Modifier.testTag("aod_cycle_style_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Style,
                                contentDescription = "Switch AOD style",
                                tint = accent,
                                modifier = Modifier.size(16.dp),
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
                        color = Color(0xFF0E131B),
                        border = BorderStroke(1.dp, Color(0xFF232E3F)),
                        modifier = Modifier.testTag("aod_cycle_accent_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = "Switch AOD accent",
                                tint = accent,
                                modifier = Modifier.size(16.dp),
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

            // Bottom Lockscreen-Style Quick Action Row: Flashlight | Exit Hint | Camera
            Row(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    onClick = { flashlightOn = onToggleFlashlight(!flashlightOn) },
                    shape = CircleShape,
                    color = if (flashlightOn) accent.copy(alpha = 0.22f) else Color(0xFF0E131B),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (flashlightOn) accent else Color(0xFF222C3B),
                    ),
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("aod_flashlight_button"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (flashlightOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = "Toggle Flashlight",
                            tint = if (flashlightOn) accent else Color(0xFFA8B5C6),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Text(
                    text = "DOUBLE-TAP TO EXIT",
                    style = MonoLabelStyle,
                    color = Color(0xFF5D6A7B),
                    maxLines = 1,
                    softWrap = false,
                )

                Surface(
                    onClick = onLaunchCamera,
                    shape = CircleShape,
                    color = Color(0xFF0E131B),
                    border = BorderStroke(1.dp, Color(0xFF222C3B)),
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("aod_camera_button"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "Open Camera",
                            tint = Color(0xFFA8B5C6),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AodSegmentedRingGauge(
    state: PowerTelemetryState,
    accent: Color,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (state.exactLevelPercent / 100f).coerceIn(0.01f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "aod_segmented_progress",
    )

    val formattedExact = String.format(Locale.US, "%.2f", state.exactLevelPercent)
    val integerPart = formattedExact.substringBefore(".")
    val decimalPart = "." + formattedExact.substringAfter(".")

    Box(
        modifier = Modifier.size(236.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f - 6.dp.toPx()
            val innerRadius = outerRadius - 17.dp.toPx()
            val segmentCount = 60
            val activeSegments = (animatedProgress * segmentCount).toInt().coerceIn(0, segmentCount)

            for (i in 0 until segmentCount) {
                val angleDeg = -90f + (360f * i / segmentCount)
                val rad = angleDeg * PI / 180.0
                val isFilled = i < activeSegments
                val isLeadingEdge = i == (activeSegments - 1).coerceAtLeast(0) && activeSegments > 0

                val start = Offset(
                    center.x + (innerRadius * cos(rad)).toFloat(),
                    center.y + (innerRadius * sin(rad)).toFloat(),
                )
                val end = Offset(
                    center.x + (outerRadius * cos(rad)).toFloat(),
                    center.y + (outerRadius * sin(rad)).toFloat(),
                )

                val segColor = when {
                    isLeadingEdge -> Color.White
                    isFilled -> accent.copy(alpha = 0.45f + 0.55f * (i.toFloat() / segmentCount.coerceAtLeast(1)))
                    else -> Color(0xFF17202D)
                }

                drawLine(
                    color = segColor,
                    start = start,
                    end = end,
                    strokeWidth = if (isLeadingEdge) 4.dp.toPx() else 3.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Inner hairline ring
            drawCircle(
                color = Color(0xFF141C27),
                radius = innerRadius - 8.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = integerPart,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                    letterSpacing = (-1.5).sp,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = "$decimalPart%",
                    style = MonoMetricStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                    color = accent,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Text(
                text = "${state.statusLabel.uppercase(Locale.US)} • ${state.pluggedLabel.uppercase(Locale.US)}",
                style = MonoLabelStyle,
                color = Color(0xFF7D8C9F),
                maxLines = 1,
                softWrap = false,
            )
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
        modifier = Modifier.size(230.dp),
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
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = state.pluggedLabel.uppercase(Locale.US),
                style = MonoLabelStyle,
                color = Color(0xFF9FB0C4),
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
        modifier = Modifier.padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = String.format(Locale.US, "%.2f%%", state.exactLevelPercent),
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 58.sp,
            letterSpacing = (-2).sp,
            color = accent,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = "${state.statusLabel.uppercase(Locale.US)} • ${state.pluggedLabel.uppercase(Locale.US)}",
            style = MonoLabelStyle,
            color = Color(0xFF6E7B8C),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun AodTelemetryCompactCard(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0A0E14),
        border = BorderStroke(1.dp, Color(0xFF1A2230)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    color = Color(0xFF7B899C),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                text = value,
                style = MonoMetricStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = Color(0xFFE6EEF7),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private fun formatClock12HourNow(): String =
    SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())

private fun formatMeridiemNow(): String =
    SimpleDateFormat("a", Locale.US).format(Date()).uppercase(Locale.US)

private fun formatDateNow(): String =
    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
