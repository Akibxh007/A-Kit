package com.example.feature.power

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClockNumberStyle
import com.example.data.FloatingPillStyle
import com.example.data.GaugeStyle
import com.example.feature.aod.AodSpeedometerGauge
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.theme.MontserratAlternatesFontFamily
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Large Hero Power Gauge supporting all 12 Design Studio [GaugeStyle]s, all 6 [ClockNumberStyle]s,
 * and dynamic charging/discharging colors.
 */
@Composable
fun PowerHeroArcGauge(
    levelPercent: Float,
    isCharging: Boolean,
    statusText: String,
    powerBadgeText: String,
    estimateText: String?,
    accentColor: Color,
    gaugeStyle: GaugeStyle,
    clockNumberStyle: ClockNumberStyle,
    modifier: Modifier = Modifier,
    wattage: Float = 0f,
    currentNowMa: Int = 0,
    gaugeSize: Dp = 236.dp,
    compactMode: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 10.dp),
    ) {
        AodSpeedometerGauge(
            levelPercent = levelPercent,
            wattage = wattage,
            currentNowMa = currentNowMa,
            isCharging = isCharging,
            gaugeStyle = gaugeStyle,
            clockNumberStyle = clockNumberStyle,
            accentColor = accentColor,
            gaugeSize = gaugeSize,
            compactMode = compactMode,
            amoledMode = false,
            brightnessAlpha = 1.0f,
            percentagePrimaryDisplay = true,
            showWattsInsteadOfMa = true,
            statusBadgeText = statusText,
            bottomPillText = powerBadgeText,
            estimateText = estimateText,
        )
    }
}

/**
 * 2-Column Bento Metric Card with:
 * - Top row: small icon on left, uppercase Roboto Condensed label on right.
 * - Bottom area: large Montserrat Alternates value on left, subtle chevron (>) on right.
 * - Background: subtle, low-opacity live animated sparkline wave reacting to real-time data.
 */
@Composable
fun BentoMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    sparklineValues: List<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    testTagName: String? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bento_sparkline_phase_$label")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bento_wave_phase_$label",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .height(114.dp)
            .then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle, low-opacity live animated sparkline wave across lower half
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp)),
            ) {
                val width = size.width
                val height = size.height
                val baselineTop = height * 0.44f
                val baselineBottom = height * 0.90f
                val spanY = (baselineBottom - baselineTop).coerceAtLeast(8f)

                val points = if (sparklineValues.size >= 3) {
                    val minVal = sparklineValues.minOrNull() ?: 0f
                    val maxVal = sparklineValues.maxOrNull() ?: 1f
                    val range = max(0.001f, maxVal - minVal)
                    val stepX = width / (sparklineValues.size - 1).coerceAtLeast(1)
                    sparklineValues.mapIndexed { idx, v ->
                        val normalized = if (maxVal - minVal < 0.0005f) {
                            0.48f + 0.14f * sin(wavePhase + idx * 0.65f)
                        } else {
                            ((v - minVal) / range).coerceIn(0.08f, 0.92f) +
                                0.06f * sin(wavePhase + idx * 0.55f)
                        }
                        val y = baselineBottom - (normalized.coerceIn(0.05f, 0.95f) * spanY)
                        Offset(idx * stepX, y)
                    }
                } else {
                    // Smooth ambient hardware pulse when first samples are warming up
                    val sampleCount = 12
                    val stepX = width / (sampleCount - 1)
                    List(sampleCount) { idx ->
                        val norm = 0.45f + 0.22f * sin(wavePhase + idx * 0.62f)
                        Offset(idx * stepX, baselineBottom - norm * spanY)
                    }
                }

                if (points.size >= 2) {
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            val midX = (prev.x + curr.x) / 2f
                            cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.14f),
                                accentColor.copy(alpha = 0.01f),
                            ),
                            startY = baselineTop,
                            endY = height,
                        ),
                    )
                    drawPath(
                        path = linePath,
                        color = accentColor.copy(alpha = 0.28f),
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }

            // Foreground Bento Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Row: Small Icon Left + Uppercase Roboto Condensed Label Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.14f),
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Text(
                        text = label.uppercase(Locale.US),
                        style = MonoLabelStyle.copy(
                            fontSize = 11.sp,
                            letterSpacing = 0.9.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Bottom Area: Large Montserrat Alternates Value Left + Subtle Chevron Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp),
                    ) {
                        Text(
                            text = value,
                            style = TextStyle(
                                fontFamily = MontserratAlternatesFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                letterSpacing = (-0.4).sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!subtext.isNullOrBlank()) {
                            Text(
                                text = subtext,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.2.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Reusable live visual preview of any FloatingPillStyle for the Design Studio & Live Preview box.
 */
@Composable
fun FloatingPillStylePreview(
    style: FloatingPillStyle,
    accentColor: Color,
    wattsText: String,
    currentText: String,
    tempText: String,
    percentText: String,
    modifier: Modifier = Modifier,
) {
    when (style) {
        FloatingPillStyle.COMPACT_SINGLE_LINE -> {
            Surface(
                shape = CircleShape,
                color = Color(0xFF0B0E0B),
                border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.7f)),
                modifier = modifier,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = "⚡ $wattsText",
                        style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
                        color = accentColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = currentText,
                        style = MonoLabelStyle,
                        color = Color(0xFFE2EAE2),
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = tempText,
                        style = MonoLabelStyle,
                        color = Color(0xFFA7B5A8),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        FloatingPillStyle.TWO_ROW_BOXED -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0E130E),
                border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.6f)),
                modifier = modifier,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "⚡ $wattsText",
                            style = TextStyle(
                                fontFamily = MontserratAlternatesFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            ),
                            color = accentColor,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(
                            text = percentText,
                            style = MonoLabelStyle,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Text(
                        text = "$currentText  •  $tempText",
                        style = MonoLabelStyle.copy(fontSize = 10.sp),
                        color = Color(0xFF9EB0A0),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        FloatingPillStyle.GLASS_PILL -> {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.16f),
                border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.85f)),
                modifier = modifier,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "$wattsText • $tempText",
                        style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

fun formatDynamicPowerBadge(isCharging: Boolean, watts: Float): String {
    val absWatts = abs(watts)
    val sign = if (isCharging) "+" else "-"
    return if (!isCharging && absWatts < 2.0f && absWatts > 0.01f) {
        val mw = (absWatts * 1000f).roundToInt()
        "$sign$mw mW"
    } else {
        String.format(Locale.US, "%s%.1f W", sign, absWatts)
    }
}

fun buildSparklineList(samples: List<TelemetrySample>, extractor: (TelemetrySample) -> Float): List<Float> {
    if (samples.isEmpty()) return emptyList()
    return samples.takeLast(16).map(extractor)
}
