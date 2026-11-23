package com.example.feature.power

import android.os.Bundle
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
import androidx.compose.animation.core.animateIntOffsetAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Palette
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class ChargingAodActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PowerTelemetryEngine.ensureInitialized(applicationContext)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        setContent {
            AKitTheme(themeMode = ThemeMode.AMOLED, dynamicColor = false) {
                ChargingAodScreen(onExit = { finish() })
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
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
) {
    val state by PowerTelemetryEngine.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val accent = settings.aodAccent.composeColor

    // Live digital clock
    var currentTimeText by remember { mutableStateOf(formatClockNow()) }
    var currentDateText by remember { mutableStateOf(formatDateNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeText = formatClockNow()
            currentDateText = formatDateNow()
            delay(1_000L)
        }
    }

    // OLED Burn-in Protection: gentle 8-point pixel orbit every 18 seconds
    var shiftStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(18_000L)
            shiftStep = (shiftStep + 1) % 8
        }
    }

    val density = LocalDensity.current
    val targetOffsetPx = remember(shiftStep, density) {
        val orbitPointsDp = listOf(
            0 to 0,
            12 to -18,
            16 to 8,
            8 to 22,
            -10 to 16,
            -16 to -4,
            -12 to -22,
            4 to -10,
        )
        val (dx, dy) = orbitPointsDp[shiftStep]
        with(density) { IntOffset(dx.dp.roundToPx(), dy.dp.roundToPx()) }
    }

    val animatedBurnInOffset by animateIntOffsetAsState(
        targetValue = targetOffsetPx,
        animationSpec = tween(durationMillis = 1800, easing = LinearEasing),
        label = "oled_burn_in_shift",
    )

    var showControlsHint by remember { mutableStateOf(true) }
    LaunchedEffect(showControlsHint) {
        if (showControlsHint) {
            delay(4_500L)
            showControlsHint = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onExit() },
                    onTap = { showControlsHint = true },
                )
            }
            .testTag("charging_aod_root"),
        contentAlignment = Alignment.Center,
    ) {
        // Burn-in shifted primary AOD cluster
        Column(
            modifier = Modifier
                .offset { animatedBurnInOffset }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Clock + Date header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTimeText,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Light,
                    fontSize = 62.sp,
                    letterSpacing = (-2).sp,
                    color = Color(0xFFE6EDF6),
                )
                Text(
                    text = currentDateText.uppercase(Locale.US),
                    style = MonoLabelStyle,
                    color = Color(0xFF7D8B9E),
                )
            }

            // Selected AOD Visualization Style
            when (settings.aodStyle) {
                AodVisualStyle.SPEEDOMETER_RING -> AodSpeedometerRing(
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

            // Live Telemetry Readout Pill Strip
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AodMiniMetricBadge(
                    icon = Icons.Rounded.Bolt,
                    text = "${abs(state.currentNowMa)} mA",
                    accent = accent,
                )
                AodMiniMetricBadge(
                    icon = Icons.Rounded.Thermostat,
                    text = settings.tempUnit.format(state.temperatureCelsius),
                    accent = accent,
                )
                if (settings.dualCellMode) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                    ) {
                        Text(
                            text = "2S",
                            style = MonoLabelStyle,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }

        // Subtle interactive bottom bar + double-tap exit prompt
        AnimatedVisibility(
            visible = showControlsHint,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(500)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 26.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = {
                            val styles = AodVisualStyle.entries
                            val next = styles[(styles.indexOf(settings.aodStyle) + 1) % styles.size]
                            PowerTelemetryEngine.updateSettings { it.copy(aodStyle = next) }
                        },
                        shape = CircleShape,
                        color = Color(0xFF10151D),
                        border = BorderStroke(1.dp, Color(0xFF283244)),
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
                        color = Color(0xFF10151D),
                        border = BorderStroke(1.dp, Color(0xFF283244)),
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
                            )
                        }
                    }
                }

                Text(
                    text = "OLED SHIFT ACTIVE • DOUBLE-TAP ANYWHERE TO EXIT",
                    style = MonoLabelStyle,
                    color = Color(0xFF6B7788),
                )
            }
        }
    }
}

@Composable
private fun AodSpeedometerRing(
    state: PowerTelemetryState,
    accent: Color,
) {
    val levelSweep by animateFloatAsState(
        targetValue = (state.levelPercent / 100f).coerceIn(0.02f, 1f) * 260f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "aod_ring_sweep",
    )

    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 10.dp.toPx()
            val inset = 18.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Outer precision speedometer tick marks
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f - 4.dp.toPx()
            val tickCount = 36
            for (i in 0..tickCount) {
                val angleDeg = 140f + (260f * i / tickCount)
                val rad = angleDeg * PI / 180.0
                val isMajor = i % 6 == 0
                val tickLen = if (isMajor) 9.dp.toPx() else 4.5.dp.toPx()
                val start = Offset(
                    center.x + ((outerRadius - tickLen) * cos(rad)).toFloat(),
                    center.y + ((outerRadius - tickLen) * sin(rad)).toFloat(),
                )
                val end = Offset(
                    center.x + (outerRadius * cos(rad)).toFloat(),
                    center.y + (outerRadius * sin(rad)).toFloat(),
                )
                val filled = (260f * i / tickCount) <= levelSweep
                drawLine(
                    color = if (filled) accent.copy(alpha = 0.85f) else Color(0xFF1F2836),
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Background arc track
            drawArc(
                color = Color(0xFF151C26),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // Active glowing charge arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(accent.copy(alpha = 0.55f), accent, Color.White.copy(alpha = 0.9f)),
                    center = center,
                ),
                startAngle = 140f,
                sweepAngle = levelSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${state.levelPercent}%",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 46.sp,
                color = Color.White,
            )
            Text(
                text = String.format(Locale.US, "%.1f W", state.wattage),
                style = MonoMetricStyle.copy(fontSize = 20.sp),
                color = accent,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.statusLabel.uppercase(Locale.US),
                style = MonoLabelStyle,
                color = Color(0xFF7D8B9E),
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
                    androidx.compose.ui.geometry.Rect(
                        left = center.x - radius,
                        top = center.y - radius,
                        right = center.x + radius,
                        bottom = center.y + radius,
                    ),
                )
            }

            clipPath(circlePath) {
                val fillFraction = (state.levelPercent / 100f).coerceIn(0.08f, 0.96f)
                val baseY = center.y + radius - (radius * 2f * fillFraction)
                val amplitude = 9.dp.toPx()

                // Secondary subtle wave behind
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

                // Primary energy wave
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

            // Crisp outer capsule border
            drawCircle(
                color = accent.copy(alpha = 0.55f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${state.levelPercent}%",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                color = Color.White,
            )
            Text(
                text = String.format(Locale.US, "%.1f W", state.wattage),
                style = MonoMetricStyle.copy(fontSize = 19.sp),
                color = accent,
            )
            Text(
                text = state.pluggedLabel.uppercase(Locale.US),
                style = MonoLabelStyle,
                color = Color(0xFF9FB0C4),
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
            text = "${state.levelPercent}%",
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 72.sp,
            letterSpacing = (-2).sp,
            color = accent,
        )
        Text(
            text = String.format(
                Locale.US,
                "%.1f W  •  %.2f V",
                state.wattage,
                state.effectiveVoltageMv / 1000f,
            ),
            style = MonoMetricStyle.copy(fontSize = 17.sp),
            color = Color(0xFFD6E0EC),
        )
        Text(
            text = "${state.statusLabel.uppercase(Locale.US)} • ${state.pluggedLabel.uppercase(Locale.US)}",
            style = MonoLabelStyle,
            color = Color(0xFF6E7B8C),
        )
    }
}

@Composable
private fun AodMiniMetricBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    accent: Color,
) {
    Surface(
        shape = CircleShape,
        color = Color(0xFF0C1118),
        border = BorderStroke(1.dp, Color(0xFF1F2937)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = text,
                style = MonoLabelStyle.copy(fontSize = 12.sp),
                color = Color(0xFFDCE5F0),
            )
        }
    }
}

private fun formatClockNow(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun formatDateNow(): String =
    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
