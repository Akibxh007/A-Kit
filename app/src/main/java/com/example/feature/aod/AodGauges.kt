package com.example.feature.aod

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClockNumberStyle
import com.example.data.GaugeStyle
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.theme.MontserratAlternatesFontFamily
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Full 12-style AmpereFlow Canvas Speedometer / Battery Gauge shared across:
 * - Full-Screen AMOLED Charging AOD (`ChargingAodActivity`)
 * - Design Studio Live Phone Mockup (`DesignScreen`)
 * - Main Power Dashboard Hero (`PowerHeroArcGauge`)
 */
@Composable
fun AodSpeedometerGauge(
    levelPercent: Float,
    wattage: Float,
    currentNowMa: Int,
    isCharging: Boolean,
    gaugeStyle: GaugeStyle,
    clockNumberStyle: ClockNumberStyle,
    accentColor: Color,
    modifier: Modifier = Modifier,
    gaugeSize: Dp = 236.dp,
    compactMode: Boolean = false,
    amoledMode: Boolean = true,
    brightnessAlpha: Float = 1.0f,
    percentagePrimaryDisplay: Boolean = true,
    showWattsInsteadOfMa: Boolean = true,
    powerSavingSlowAnim: Boolean = false,
    statusBadgeText: String? = null,
    bottomPillText: String? = null,
    estimateText: String? = null,
) {
    val clampedBrightness = brightnessAlpha.coerceIn(0.20f, 1.0f)

    val animatedProgress by animateFloatAsState(
        targetValue = (levelPercent / 100f).coerceIn(0.01f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "aod_gauge_progress",
    )

    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 320),
        label = "aod_gauge_accent",
    )

    val activeAccent = animatedAccent.copy(alpha = clampedBrightness)
    val trackColor = if (amoledMode) {
        Color(0xFF1A222C).copy(alpha = (0.55f + 0.45f * clampedBrightness).coerceAtMost(1f))
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val primaryTextColor = if (amoledMode) {
        Color.White.copy(alpha = clampedBrightness)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryTextColor = if (amoledMode) {
        Color(0xFF8C9BAE).copy(alpha = clampedBrightness)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Continuous phase for animated styles (Vertical Pill wave, Atomic Swirl, Sparkline Wave, Orbit dot)
    val needsMotion = gaugeStyle == GaugeStyle.VERTICAL_BATTERY_PILL ||
        gaugeStyle == GaugeStyle.ATOMIC_SWIRL ||
        gaugeStyle == GaugeStyle.LIVE_SPARKLINE_WAVE ||
        gaugeStyle == GaugeStyle.CONCENTRIC_ORBIT

    val animPhase = if (needsMotion && !powerSavingSlowAnim) {
        val infiniteTransition = rememberInfiniteTransition(label = "aod_gauge_motion")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "aod_gauge_phase",
        )
        phase
    } else {
        0.65f
    }

    val effectiveFontStyle = if (clockNumberStyle == ClockNumberStyle.OFF) {
        ClockNumberStyle.MONTSERRAT_BOLD
    } else {
        clockNumberStyle
    }
    val numberFontFamily = clockFontFamilyFor(effectiveFontStyle)

    val formattedExact = String.format(Locale.US, "%.2f", levelPercent.coerceIn(0f, 100f))
    val integerPart = formattedExact.substringBefore(".")
    val decimalPart = "." + formattedExact.substringAfter(".")

    val powerReadoutValue = if (showWattsInsteadOfMa) {
        String.format(Locale.US, "%.1f", abs(wattage))
    } else {
        "${abs(currentNowMa)}"
    }
    val powerReadoutUnit = if (showWattsInsteadOfMa) "W" else "mA"

    val mainNumber = if (percentagePrimaryDisplay) integerPart else powerReadoutValue
    val mainSuffix = if (percentagePrimaryDisplay) {
        if (compactMode) "%" else "$decimalPart%"
    } else {
        " $powerReadoutUnit"
    }
    val subLineText = if (percentagePrimaryDisplay) {
        if (showWattsInsteadOfMa) {
            String.format(Locale.US, "%s%.1f W", if (isCharging) "+" else "-", abs(wattage))
        } else {
            "${if (currentNowMa >= 0) "+" else ""}$currentNowMa mA"
        }
    } else {
        String.format(Locale.US, "%.1f%%", levelPercent.coerceIn(0f, 100f))
    }

    // Special horizontal layout for Vertical Battery Pill so percentage sits cleanly beside the liquid capsule
    if (gaugeStyle == GaugeStyle.VERTICAL_BATTERY_PILL) {
        Row(
            modifier = modifier.size(width = gaugeSize, height = gaugeSize * 0.84f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compactMode) 12.dp else 20.dp, Alignment.CenterHorizontally),
        ) {
            Canvas(
                modifier = Modifier.size(
                    width = gaugeSize * 0.34f,
                    height = gaugeSize * 0.72f,
                ),
            ) {
                drawVerticalBatteryPill(
                    progress = animatedProgress,
                    accent = activeAccent,
                    trackColor = trackColor,
                    phase = animPhase,
                    compactMode = compactMode,
                )
            }

            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 6.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = mainNumber,
                        style = TextStyle(
                            fontFamily = numberFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compactMode) 28.sp else 50.sp,
                            letterSpacing = (-1.2).sp,
                        ),
                        color = primaryTextColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.testTag("power_hero_percent_text"),
                    )
                    Text(
                        text = mainSuffix,
                        style = TextStyle(
                            fontFamily = numberFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compactMode) 12.sp else 18.sp,
                        ),
                        color = activeAccent,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = if (compactMode) 4.dp else 8.dp),
                    )
                }
                Text(
                    text = bottomPillText ?: subLineText,
                    style = MonoLabelStyle.copy(fontSize = if (compactMode) 9.sp else 13.sp),
                    color = activeAccent,
                    maxLines = 1,
                    softWrap = false,
                )
                if (!statusBadgeText.isNullOrBlank()) {
                    StatusChipPill(
                        text = statusBadgeText,
                        isCharging = isCharging,
                        accent = activeAccent,
                        compactMode = compactMode,
                    )
                }
            }
        }
        return
    }

    Box(
        modifier = modifier.size(gaugeSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGaugeCanvasStyle(
                style = gaugeStyle,
                progress = animatedProgress,
                accent = activeAccent,
                trackColor = trackColor,
                phase = animPhase,
                compactMode = compactMode,
            )
        }

        // Center readout positioned harmoniously for each style
        val verticalOffsetDp = when (gaugeStyle) {
            GaugeStyle.ANALOG_NEEDLE_DIAL -> if (compactMode) 26.dp else 44.dp
            GaugeStyle.HORIZONTAL_BAR_STACK -> if (compactMode) (-24).dp else (-46).dp
            GaugeStyle.INVERTED_FUNNEL_BARS -> if (compactMode) (-24).dp else (-46).dp
            else -> 0.dp
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = if (compactMode) 8.dp else 16.dp)
                .padding(top = verticalOffsetDp.coerceAtLeast(0.dp), bottom = (-verticalOffsetDp).coerceAtLeast(0.dp)),
        ) {
            val numberFontSize = when {
                compactMode && !percentagePrimaryDisplay -> 22.sp
                compactMode -> 27.sp
                !percentagePrimaryDisplay -> 42.sp
                gaugeStyle == GaugeStyle.ANALOG_NEEDLE_DIAL -> 38.sp
                gaugeStyle == GaugeStyle.HORIZONTAL_BAR_STACK || gaugeStyle == GaugeStyle.INVERTED_FUNNEL_BARS -> 38.sp
                else -> 50.sp
            }
            val suffixFontSize = if (compactMode) 11.sp else 18.sp

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = mainNumber,
                    style = TextStyle(
                        fontFamily = numberFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = numberFontSize,
                        letterSpacing = (-1.2).sp,
                    ),
                    color = primaryTextColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("power_hero_percent_text"),
                )
                Text(
                    text = mainSuffix,
                    style = TextStyle(
                        fontFamily = numberFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = suffixFontSize,
                    ),
                    color = activeAccent,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = if (compactMode) 4.dp else 8.dp),
                )
            }

            if (!compactMode && gaugeStyle != GaugeStyle.ANALOG_NEEDLE_DIAL &&
                gaugeStyle != GaugeStyle.HORIZONTAL_BAR_STACK &&
                gaugeStyle != GaugeStyle.INVERTED_FUNNEL_BARS
            ) {
                Text(
                    text = subLineText,
                    style = MonoLabelStyle.copy(fontSize = 11.5.sp),
                    color = secondaryTextColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("power_decimal_percent_text"),
                )
            }

            if (!statusBadgeText.isNullOrBlank() &&
                gaugeStyle != GaugeStyle.HORIZONTAL_BAR_STACK &&
                gaugeStyle != GaugeStyle.INVERTED_FUNNEL_BARS
            ) {
                StatusChipPill(
                    text = statusBadgeText,
                    isCharging = isCharging,
                    accent = activeAccent,
                    compactMode = compactMode,
                    modifier = Modifier.padding(top = if (compactMode) 2.dp else 5.dp),
                )
            }
        }

        // Bottom pill badge for Power Dashboard hero when supplied
        if (!bottomPillText.isNullOrBlank()) {
            Surface(
                shape = CircleShape,
                color = if (amoledMode) Color(0xFF0E141B) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.45f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (compactMode) 2.dp else 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (compactMode) 8.dp else 13.dp,
                        vertical = if (compactMode) 3.dp else 5.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = bottomPillText,
                        style = TextStyle(
                            fontFamily = MontserratAlternatesFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compactMode) 10.sp else 14.sp,
                        ),
                        color = activeAccent,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.testTag("power_live_wattage_text"),
                    )
                    if (!estimateText.isNullOrBlank() && !compactMode) {
                        Text(
                            text = "•",
                            style = MonoLabelStyle,
                            color = secondaryTextColor,
                        )
                        Text(
                            text = estimateText,
                            style = MonoLabelStyle.copy(fontSize = 11.5.sp),
                            color = primaryTextColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("power_estimate_time_badge"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChipPill(
    text: String,
    isCharging: Boolean,
    accent: Color,
    compactMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(0.9.dp, accent.copy(alpha = 0.36f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compactMode) 6.dp else 10.dp,
                vertical = if (compactMode) 1.5.dp else 3.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (isCharging) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(if (compactMode) 9.dp else 12.dp),
                )
            }
            Text(
                text = text.uppercase(Locale.US),
                style = MonoLabelStyle.copy(
                    fontSize = if (compactMode) 7.5.sp else 10.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = accent,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Crisp miniature Canvas preview thumbnail for all 12 [GaugeStyle]s in the Design Studio picker.
 */
@Composable
fun MiniGaugeStyleThumbnail(
    style: GaugeStyle,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = modifier.size(70.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (style == GaugeStyle.VERTICAL_BATTERY_PILL) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Canvas(modifier = Modifier.size(width = 24.dp, height = 52.dp)) {
                    drawVerticalBatteryPill(
                        progress = 0.72f,
                        accent = accentColor,
                        trackColor = trackColor,
                        phase = 0.8f,
                        compactMode = true,
                    )
                }
                Text(
                    text = "72%",
                    style = TextStyle(
                        fontFamily = MontserratAlternatesFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawGaugeCanvasStyle(
                    style = style,
                    progress = 0.72f,
                    accent = accentColor,
                    trackColor = trackColor,
                    phase = 0.9f,
                    compactMode = true,
                )
            }
            if (style != GaugeStyle.HORIZONTAL_BAR_STACK && style != GaugeStyle.INVERTED_FUNNEL_BARS) {
                Text(
                    text = "72%",
                    style = TextStyle(
                        fontFamily = MontserratAlternatesFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (style == GaugeStyle.ANALOG_NEEDLE_DIAL) 11.sp else 13.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        top = if (style == GaugeStyle.ANALOG_NEEDLE_DIAL) 24.dp else 0.dp,
                    ),
                )
            }
        }
    }
}

private fun DrawScope.drawGaugeCanvasStyle(
    style: GaugeStyle,
    progress: Float,
    accent: Color,
    trackColor: Color,
    phase: Float,
    compactMode: Boolean,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val startAngle = 135f
    val totalSweep = 270f
    val activeSweep = totalSweep * progress

    when (style) {
        GaugeStyle.MINIMAL_THIN_ARC -> {
            val strokePx = if (compactMode) 4.dp.toPx() else 9.dp.toPx()
            val inset = strokePx + if (compactMode) 3.dp.toPx() else 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)

            drawArc(trackColor, startAngle, totalSweep, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
            drawArc(accent.copy(alpha = 0.18f), startAngle, activeSweep, false, topLeft, arcSize, style = Stroke(strokePx * 2.0f, cap = StrokeCap.Round))
            drawArc(accent, startAngle, activeSweep, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))

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
            val strokePx = if (compactMode) 8.5.dp.toPx() else 18.dp.toPx()
            val inset = strokePx / 2f + if (compactMode) 3.dp.toPx() else 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)

            drawArc(trackColor, startAngle, totalSweep, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to accent.copy(alpha = 0.68f),
                    0.55f to accent,
                    1.0f to accent.copy(alpha = 0.92f),
                    center = center,
                ),
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
        }

        GaugeStyle.SEGMENTED_TICK -> {
            val tickCount = if (compactMode) 22 else 44
            val activeTicks = (progress * tickCount).roundToInt().coerceIn(0, tickCount)
            val outerRadius = size.minDimension / 2f - if (compactMode) 3.dp.toPx() else 4.dp.toPx()
            val tickLen = if (compactMode) 7.dp.toPx() else 15.dp.toPx()
            val innerRadius = outerRadius - tickLen

            for (i in 0 until tickCount) {
                val fraction = i.toFloat() / (tickCount - 1).coerceAtLeast(1)
                val rad = (startAngle + totalSweep * fraction) * PI / 180.0
                val isFilled = i < activeTicks
                val isLead = i == activeTicks - 1 && activeTicks > 0
                drawLine(
                    color = when {
                        isLead -> Color.White
                        isFilled -> accent.copy(alpha = 0.50f + 0.50f * fraction)
                        else -> trackColor
                    },
                    start = Offset(center.x + (innerRadius * cos(rad)).toFloat(), center.y + (innerRadius * sin(rad)).toFloat()),
                    end = Offset(center.x + (outerRadius * cos(rad)).toFloat(), center.y + (outerRadius * sin(rad)).toFloat()),
                    strokeWidth = if (isLead) (if (compactMode) 2.5.dp.toPx() else 4.dp.toPx()) else (if (compactMode) 1.9.dp.toPx() else 3.dp.toPx()),
                    cap = StrokeCap.Round,
                )
            }
        }

        GaugeStyle.DOTTED_VORTEX -> {
            // Halftone concentric spiral matrix illuminating proportionally
            val rings = if (compactMode) 3 else 4
            val dotsPerRing = if (compactMode) 16 else 26
            val outerR = size.minDimension / 2f - if (compactMode) 3.dp.toPx() else 6.dp.toPx()
            val ringStep = if (compactMode) 4.2.dp.toPx() else 9.5.dp.toPx()
            val activeDots = (progress * dotsPerRing).roundToInt().coerceIn(0, dotsPerRing)

            for (r in 0 until rings) {
                val radius = (outerR - r * ringStep).coerceAtLeast(size.minDimension * 0.28f)
                val dotRadius = ((if (compactMode) 2.0f else 3.6f) - r * 0.45f).coerceAtLeast(1.1f).dp.toPx()
                val swirlOffsetDeg = r * 9f
                for (i in 0 until dotsPerRing) {
                    val fraction = i.toFloat() / dotsPerRing
                    val angleDeg = -90f + fraction * 360f + swirlOffsetDeg
                    val rad = angleDeg * PI / 180.0
                    val lit = i < activeDots
                    val alpha = if (lit) (1f - r * 0.16f).coerceIn(0.35f, 1f) else 0.42f
                    drawCircle(
                        color = if (lit) accent.copy(alpha = alpha) else trackColor.copy(alpha = alpha),
                        radius = dotRadius,
                        center = Offset(
                            center.x + (radius * cos(rad)).toFloat(),
                            center.y + (radius * sin(rad)).toFloat(),
                        ),
                    )
                }
            }
        }

        GaugeStyle.VERTICAL_BATTERY_PILL -> {
            drawVerticalBatteryPill(progress, accent, trackColor, phase, compactMode)
        }

        GaugeStyle.ANALOG_NEEDLE_DIAL -> {
            // 180-degree semi-circular speedometer arc with tick marks & live rotating needle
            val strokePx = if (compactMode) 4.5.dp.toPx() else 10.dp.toPx()
            val inset = strokePx + if (compactMode) 3.dp.toPx() else 8.dp.toPx()
            val dialCenter = Offset(size.width / 2f, size.height * 0.58f)
            val radius = (size.minDimension / 2f - inset).coerceAtLeast(12f)
            val arcTopLeft = Offset(dialCenter.x - radius, dialCenter.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            drawArc(trackColor, 180f, 180f, false, arcTopLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
            drawArc(accent, 180f, 180f * progress, false, arcTopLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))

            // Dial tick marks along 180-degree perimeter
            val ticks = if (compactMode) 9 else 19
            for (i in 0 until ticks) {
                val frac = i.toFloat() / (ticks - 1)
                val rad = (180f + 180f * frac) * PI / 180.0
                val isMajor = i % 3 == 0
                val tOuter = radius - strokePx * 0.7f
                val tInner = tOuter - (if (isMajor) (if (compactMode) 5.dp.toPx() else 11.dp.toPx()) else (if (compactMode) 3.dp.toPx() else 6.dp.toPx()))
                drawLine(
                    color = if (frac <= progress) accent else trackColor,
                    start = Offset(dialCenter.x + (tInner * cos(rad)).toFloat(), dialCenter.y + (tInner * sin(rad)).toFloat()),
                    end = Offset(dialCenter.x + (tOuter * cos(rad)).toFloat(), dialCenter.y + (tOuter * sin(rad)).toFloat()),
                    strokeWidth = if (isMajor) (if (compactMode) 1.8.dp.toPx() else 2.5.dp.toPx()) else (if (compactMode) 1.dp.toPx() else 1.5.dp.toPx()),
                    cap = StrokeCap.Round,
                )
            }

            // Live rotating needle pointer
            val needleAngleRad = (180f + 180f * progress) * PI / 180.0
            val needleLen = radius * 0.72f
            val needleTip = Offset(
                dialCenter.x + (needleLen * cos(needleAngleRad)).toFloat(),
                dialCenter.y + (needleLen * sin(needleAngleRad)).toFloat(),
            )
            drawLine(
                color = Color.White,
                start = dialCenter,
                end = needleTip,
                strokeWidth = if (compactMode) 2.2.dp.toPx() else 3.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(color = accent, radius = if (compactMode) 4.dp.toPx() else 7.dp.toPx(), center = dialCenter)
            drawCircle(color = Color.Black, radius = if (compactMode) 1.8.dp.toPx() else 3.dp.toPx(), center = dialCenter)
        }

        GaugeStyle.CONCENTRIC_ORBIT -> {
            // Outer crisp track ring + glowing inner progress ring + orbiting endpoint dot
            val outerStroke = if (compactMode) 2.dp.toPx() else 3.5.dp.toPx()
            val innerStroke = if (compactMode) 4.dp.toPx() else 9.dp.toPx()
            val outerInset = if (compactMode) 4.dp.toPx() else 8.dp.toPx()
            val innerInset = if (compactMode) 10.dp.toPx() else 22.dp.toPx()

            val outerRadius = size.minDimension / 2f - outerInset
            drawCircle(
                color = trackColor,
                radius = outerRadius,
                center = center,
                style = Stroke(width = outerStroke),
            )

            // Orbiting satellite dot on outer track
            val orbitRad = (-90f + (phase * 180f / PI.toFloat())) * PI / 180.0
            drawCircle(
                color = accent.copy(alpha = 0.9f),
                radius = if (compactMode) 2.6.dp.toPx() else 4.5.dp.toPx(),
                center = Offset(
                    center.x + (outerRadius * cos(orbitRad)).toFloat(),
                    center.y + (outerRadius * sin(orbitRad)).toFloat(),
                ),
            )

            // Glowing inner progress ring
            val innerSize = Size(size.width - innerInset * 2f, size.height - innerInset * 2f)
            val innerTopLeft = Offset(innerInset, innerInset)
            drawArc(trackColor.copy(alpha = 0.65f), -90f, 360f, false, innerTopLeft, innerSize, style = Stroke(innerStroke))
            drawArc(accent.copy(alpha = 0.22f), -90f, 360f * progress, false, innerTopLeft, innerSize, style = Stroke(innerStroke * 1.9f, cap = StrokeCap.Round))
            drawArc(accent, -90f, 360f * progress, false, innerTopLeft, innerSize, style = Stroke(innerStroke, cap = StrokeCap.Round))

            val endRad = (-90f + 360f * progress) * PI / 180.0
            val innerRadius = innerSize.width / 2f
            drawCircle(
                color = Color.White,
                radius = if (compactMode) 2.4.dp.toPx() else 4.6.dp.toPx(),
                center = Offset(
                    center.x + (innerRadius * cos(endRad)).toFloat(),
                    center.y + (innerRadius * sin(endRad)).toFloat(),
                ),
            )
        }

        GaugeStyle.ATOMIC_SWIRL -> {
            // Rotating multi-petal elliptical orbital rings surrounding the center percentage
            val strokePx = if (compactMode) 1.6.dp.toPx() else 2.8.dp.toPx()
            val rx = size.width * 0.44f
            val ry = size.height * 0.26f
            val baseRotationDeg = (phase * 180f / PI.toFloat()) * 0.5f

            for (petal in 0 until 3) {
                val deg = baseRotationDeg + petal * 60f
                val alpha = 0.45f + 0.55f * progress
                rotate(degrees = deg, pivot = center) {
                    drawOval(
                        color = accent.copy(alpha = alpha * 0.22f),
                        topLeft = Offset(center.x - rx, center.y - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = strokePx * 2.2f),
                    )
                    drawOval(
                        color = accent.copy(alpha = alpha),
                        topLeft = Offset(center.x - rx, center.y - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = strokePx),
                    )
                    // Electron node travelling along each orbital ellipse
                    val eAngle = phase + petal * 2.094f
                    val ex = center.x + rx * cos(eAngle)
                    val ey = center.y + ry * sin(eAngle)
                    drawCircle(
                        color = Color.White,
                        radius = if (compactMode) 2.dp.toPx() else 3.8.dp.toPx(),
                        center = Offset(ex, ey),
                    )
                }
            }
            // Subtle progress ring inside orbitals
            val coreR = size.minDimension * 0.30f
            drawCircle(color = trackColor.copy(alpha = 0.55f), radius = coreR, center = center, style = Stroke(strokePx))
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(center.x - coreR, center.y - coreR),
                size = Size(coreR * 2f, coreR * 2f),
                style = Stroke(width = strokePx * 1.4f, cap = StrokeCap.Round),
            )
        }

        GaugeStyle.CORNER_BRACKETS_HUD -> {
            // 4-corner minimalist viewfinder brackets framing the percentage + perimeter fill
            val inset = if (compactMode) 7.dp.toPx() else 16.dp.toPx()
            val len = if (compactMode) 13.dp.toPx() else 34.dp.toPx()
            val strokePx = if (compactMode) 2.5.dp.toPx() else 4.5.dp.toPx()
            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset

            val cornersLit = (progress * 4f).coerceIn(0f, 4f)
            fun drawCorner(idx: Int, path: Path) {
                val lit = cornersLit > idx
                val col = if (lit) accent else trackColor
                drawPath(path, col, style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            drawCorner(0, Path().apply { moveTo(left, top + len); lineTo(left, top); lineTo(left + len, top) })
            drawCorner(1, Path().apply { moveTo(right - len, top); lineTo(right, top); lineTo(right, top + len) })
            drawCorner(2, Path().apply { moveTo(right, bottom - len); lineTo(right, bottom); lineTo(right - len, bottom) })
            drawCorner(3, Path().apply { moveTo(left + len, bottom); lineTo(left, bottom); lineTo(left, bottom - len) })

            // Subtle horizontal progress bar tucked inside bottom bracket
            val barLeft = left + len * 0.35f
            val barRight = right - len * 0.35f
            val barY = bottom - if (compactMode) 5.dp.toPx() else 12.dp.toPx()
            drawLine(trackColor, Offset(barLeft, barY), Offset(barRight, barY), strokeWidth = if (compactMode) 2.5.dp.toPx() else 5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(accent, Offset(barLeft, barY), Offset(barLeft + (barRight - barLeft) * progress, barY), strokeWidth = if (compactMode) 2.5.dp.toPx() else 5.dp.toPx(), cap = StrokeCap.Round)
        }

        GaugeStyle.LIVE_SPARKLINE_WAVE -> {
            // Animated dual-sine waveform oscillating behind the center percentage inside a sleek rim
            val radius = size.minDimension / 2f - if (compactMode) 3.dp.toPx() else 6.dp.toPx()
            val circlePath = Path().apply {
                addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
            }
            clipPath(circlePath) {
                val fillTopY = center.y + radius - (radius * 2f * progress.coerceIn(0.10f, 0.94f))
                val amp = if (compactMode) 3.5.dp.toPx() else 9.dp.toPx()

                val backPath = Path().apply {
                    moveTo(0f, size.height)
                    var x = 0f
                    while (x <= size.width) {
                        val y = fillTopY + sin((x / size.width) * 2.4f * PI + phase + 1.3f).toFloat() * amp
                        lineTo(x, y)
                        x += 4f
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(backPath, color = accent.copy(alpha = 0.14f))

                val waveLine = Path().apply {
                    var x = 0f
                    var started = false
                    while (x <= size.width) {
                        val y = fillTopY + sin((x / size.width) * 2.8f * PI - phase).toFloat() * (amp * 0.85f)
                        if (!started) {
                            moveTo(x, y)
                            started = true
                        } else {
                            lineTo(x, y)
                        }
                        x += 4f
                    }
                }
                drawPath(
                    path = waveLine,
                    color = accent.copy(alpha = 0.88f),
                    style = Stroke(width = if (compactMode) 1.8.dp.toPx() else 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            drawCircle(
                color = accent.copy(alpha = 0.55f),
                radius = radius,
                center = center,
                style = Stroke(width = if (compactMode) 1.5.dp.toPx() else 2.5.dp.toPx()),
            )
        }

        GaugeStyle.HORIZONTAL_BAR_STACK -> {
            // Stacked horizontal signal/power bars illuminated from bottom to top
            val barCount = if (compactMode) 6 else 10
            val activeBars = (progress * barCount).roundToInt().coerceIn(0, barCount)
            val areaTop = if (compactMode) size.height * 0.16f else size.height * 0.44f
            val areaBottom = size.height * 0.88f
            val step = (areaBottom - areaTop) / barCount
            val barHeight = (step * 0.58f).coerceAtLeast(2.dp.toPx())
            val horizontalPad = size.width * 0.14f
            val barWidth = size.width - horizontalPad * 2f

            for (i in 0 until barCount) {
                val fromBottomIndex = barCount - 1 - i
                val isLit = fromBottomIndex < activeBars
                val y = areaTop + i * step
                drawRoundRect(
                    color = if (isLit) accent.copy(alpha = 0.55f + 0.45f * (fromBottomIndex.toFloat() / barCount)) else trackColor,
                    topLeft = Offset(horizontalPad, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f),
                )
            }
        }

        GaugeStyle.INVERTED_FUNNEL_BARS -> {
            // Tapered inverted-triangle / funnel bars narrowing downwards
            val barCount = if (compactMode) 6 else 10
            val activeBars = (progress * barCount).roundToInt().coerceIn(0, barCount)
            val areaTop = if (compactMode) size.height * 0.16f else size.height * 0.44f
            val areaBottom = size.height * 0.88f
            val step = (areaBottom - areaTop) / barCount
            val barHeight = (step * 0.58f).coerceAtLeast(2.dp.toPx())
            val maxBarWidth = size.width * 0.78f
            val minBarWidth = size.width * 0.22f

            for (i in 0 until barCount) {
                val fromBottomIndex = barCount - 1 - i
                val taperFraction = 1f - (i.toFloat() / (barCount - 1).coerceAtLeast(1))
                val width = minBarWidth + (maxBarWidth - minBarWidth) * taperFraction
                val isLit = fromBottomIndex < activeBars
                val y = areaTop + i * step
                drawRoundRect(
                    color = if (isLit) accent.copy(alpha = 0.55f + 0.45f * (fromBottomIndex.toFloat() / barCount)) else trackColor,
                    topLeft = Offset(center.x - width / 2f, y),
                    size = Size(width, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f),
                )
            }
        }
    }
}

private fun DrawScope.drawVerticalBatteryPill(
    progress: Float,
    accent: Color,
    trackColor: Color,
    phase: Float,
    compactMode: Boolean,
) {
    val capHeight = if (compactMode) 3.5.dp.toPx() else 7.dp.toPx()
    val capWidth = size.width * 0.42f
    val bodyTop = capHeight + if (compactMode) 2.dp.toPx() else 4.dp.toPx()
    val bodyHeight = (size.height - bodyTop).coerceAtLeast(10f)
    val corner = size.width * 0.28f
    val strokePx = if (compactMode) 2.dp.toPx() else 3.2.dp.toPx()

    // Battery terminal cap
    drawRoundRect(
        color = accent.copy(alpha = 0.75f),
        topLeft = Offset((size.width - capWidth) / 2f, 0f),
        size = Size(capWidth, capHeight),
        cornerRadius = CornerRadius(capHeight / 2f, capHeight / 2f),
    )

    // Capsule interior clip
    val innerPad = strokePx + if (compactMode) 1.5.dp.toPx() else 3.dp.toPx()
    val innerRect = Rect(
        left = innerPad,
        top = bodyTop + innerPad,
        right = size.width - innerPad,
        bottom = size.height - innerPad,
    )
    val clipCapsule = Path().apply {
        addRoundRect(
            RoundRect(
                rect = innerRect,
                cornerRadius = CornerRadius((corner - innerPad).coerceAtLeast(4f)),
            ),
        )
    }

    // Track fill inside battery
    drawRoundRect(
        color = trackColor.copy(alpha = 0.45f),
        topLeft = Offset(strokePx / 2f, bodyTop + strokePx / 2f),
        size = Size(size.width - strokePx, bodyHeight - strokePx),
        cornerRadius = CornerRadius(corner, corner),
    )

    // Liquid animated wave level fill
    clipPath(clipCapsule) {
        val liquidHeight = innerRect.height * progress.coerceIn(0.06f, 1f)
        val liquidTop = innerRect.bottom - liquidHeight
        val amp = if (compactMode) 2.dp.toPx() else 5.dp.toPx()

        val wavePath = Path().apply {
            moveTo(innerRect.left, innerRect.bottom)
            var x = innerRect.left
            while (x <= innerRect.right + 4f) {
                val frac = (x - innerRect.left) / innerRect.width.coerceAtLeast(1f)
                val y = liquidTop + sin(frac * 2f * PI + phase).toFloat() * amp
                lineTo(x, y)
                x += 3f
            }
            lineTo(innerRect.right, innerRect.bottom)
            close()
        }

        drawPath(
            path = wavePath,
            brush = Brush.verticalGradient(
                colors = listOf(accent, accent.copy(alpha = 0.65f)),
                startY = liquidTop,
                endY = innerRect.bottom,
            ),
        )
    }

    // Crisp capsule border
    drawRoundRect(
        color = accent.copy(alpha = 0.85f),
        topLeft = Offset(strokePx / 2f, bodyTop + strokePx / 2f),
        size = Size(size.width - strokePx, bodyHeight - strokePx),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = strokePx),
    )
}
