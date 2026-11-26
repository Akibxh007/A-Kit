package com.example.feature.aod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AodDateFormatOption
import com.example.data.AodMetricSlot
import com.example.data.BatteryDetailsCardStyle
import com.example.data.ClockNumberStyle
import com.example.feature.power.PowerTelemetryState
import com.example.ui.theme.BodoniModaFontFamily
import com.example.ui.theme.CinzelDecorativeFontFamily
import com.example.ui.theme.FredokaFontFamily
import com.example.ui.theme.JostFontFamily
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.theme.MontserratAlternatesFontFamily
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.RobotoCondensedFontFamily
import com.example.ui.theme.StardosStencilFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ResolvedAodMetric(
    val slot: AodMetricSlot,
    val label: String,
    val value: String,
    val icon: ImageVector,
)

fun formatClockTime(use24Hour: Boolean, date: Date = Date()): String {
    val pattern = if (use24Hour) "HH:mm" else "h:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}

fun formatScheduleMinutes(minutesFromMidnight: Int, use24Hour: Boolean): String {
    val clamped = minutesFromMidnight.coerceIn(0, 1439)
    val hour24 = clamped / 60
    val minute = clamped % 60
    return if (use24Hour) {
        String.format(Locale.US, "%02d:%02d", hour24, minute)
    } else {
        val amPm = if (hour24 < 12) "AM" else "PM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
    }
}

fun clockFontFamilyFor(style: ClockNumberStyle): FontFamily = when (style) {
    ClockNumberStyle.OFF -> MontserratAlternatesFontFamily
    ClockNumberStyle.EDITORIAL_SERIF -> BodoniModaFontFamily
    ClockNumberStyle.MONTSERRAT_BOLD -> MontserratAlternatesFontFamily
    ClockNumberStyle.SLIM_LIGHT_MODERN -> JostFontFamily
    ClockNumberStyle.RETRO_STENCIL -> StardosStencilFontFamily
    ClockNumberStyle.OUTLINE_DIGITAL -> MontserratAlternatesFontFamily
    ClockNumberStyle.EDITORIAL_DECORATIVE_SERIF -> CinzelDecorativeFontFamily
    ClockNumberStyle.BOLD_ROUNDED_SANS -> FredokaFontFamily
    ClockNumberStyle.GEOMETRIC_TECH_BLOCK -> OrbitronFontFamily
}

@Composable
fun buildClockTextStyle(
    style: ClockNumberStyle,
    fontSize: TextUnit,
    strokeWidthDp: Dp = 2.dp,
): TextStyle {
    val density = LocalDensity.current
    val strokePx = with(density) { strokeWidthDp.toPx() }
    return when (style) {
        ClockNumberStyle.OFF -> TextStyle(
            fontFamily = MontserratAlternatesFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
        )
        ClockNumberStyle.EDITORIAL_SERIF -> TextStyle(
            fontFamily = BodoniModaFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            letterSpacing = (-1.2).sp,
        )
        ClockNumberStyle.MONTSERRAT_BOLD -> TextStyle(
            fontFamily = MontserratAlternatesFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            letterSpacing = (-1.5).sp,
        )
        ClockNumberStyle.SLIM_LIGHT_MODERN -> TextStyle(
            fontFamily = JostFontFamily,
            fontWeight = FontWeight.ExtraLight,
            fontSize = fontSize,
            letterSpacing = 2.sp,
        )
        ClockNumberStyle.RETRO_STENCIL -> TextStyle(
            fontFamily = StardosStencilFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = 0.8.sp,
        )
        ClockNumberStyle.OUTLINE_DIGITAL -> TextStyle(
            fontFamily = MontserratAlternatesFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = 1.6.sp,
            drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
        )
        ClockNumberStyle.EDITORIAL_DECORATIVE_SERIF -> TextStyle(
            fontFamily = CinzelDecorativeFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = 0.4.sp,
        )
        ClockNumberStyle.BOLD_ROUNDED_SANS -> TextStyle(
            fontFamily = FredokaFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            letterSpacing = (-0.6).sp,
        )
        ClockNumberStyle.GEOMETRIC_TECH_BLOCK -> TextStyle(
            fontFamily = OrbitronFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            letterSpacing = 1.6.sp,
        )
    }
}

/**
 * Shared digit renderer supporting layered vintage inline stroke accents for
 * [ClockNumberStyle.EDITORIAL_DECORATIVE_SERIF] and crisp hollow outlines for
 * [ClockNumberStyle.OUTLINE_DIGITAL].
 */
@Composable
fun StyledClockDigitsText(
    clockStyle: ClockNumberStyle,
    timeText: String,
    fontSize: TextUnit,
    strokeWidthDp: Dp,
    primaryColor: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val baseStyle = buildClockTextStyle(
        style = clockStyle,
        fontSize = fontSize,
        strokeWidthDp = strokeWidthDp,
    )
    if (clockStyle == ClockNumberStyle.EDITORIAL_DECORATIVE_SERIF) {
        val density = LocalDensity.current
        val inlineStrokePx = with(density) { (strokeWidthDp * 0.62f).toPx() }
        val outerHaloPx = with(density) { (strokeWidthDp * 1.15f).toPx() }
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            // Subtle outer vintage sheen outline
            Text(
                text = timeText,
                style = baseStyle.copy(
                    drawStyle = Stroke(width = outerHaloPx, join = StrokeJoin.Miter),
                ),
                color = accent.copy(alpha = 0.24f),
                maxLines = 1,
                softWrap = false,
            )
            // Primary high-contrast vintage display serif fill
            Text(
                text = timeText,
                style = baseStyle,
                color = primaryColor,
                maxLines = 1,
                softWrap = false,
            )
            // Delicate inline engraved accent stroke
            Text(
                text = timeText,
                style = baseStyle.copy(
                    drawStyle = Stroke(width = inlineStrokePx, join = StrokeJoin.Miter),
                ),
                color = Color(0xFF0A1216).copy(alpha = 0.62f),
                maxLines = 1,
                softWrap = false,
            )
        }
    } else {
        Text(
            text = timeText,
            style = baseStyle,
            color = if (clockStyle == ClockNumberStyle.OUTLINE_DIGITAL) accent else primaryColor,
            maxLines = 1,
            softWrap = false,
            modifier = modifier,
        )
    }
}

/**
 * Renders the AOD Clock & Date header in any of the 9 [ClockNumberStyle]s (including true hollow
 * stroke-outlined numerals, vintage inline decorative serif, and OFF mode) for both Full-Screen AOD
 * and the Live Phone Mockup.
 */
@Composable
fun AodClockDisplay(
    clockStyle: ClockNumberStyle,
    timeText: String,
    dateText: String,
    accent: Color,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
) {
    if (clockStyle == ClockNumberStyle.OFF) {
        return
    }

    val clockSize = when (clockStyle) {
        ClockNumberStyle.GEOMETRIC_TECH_BLOCK -> if (compactMode) 30.sp else 64.sp
        ClockNumberStyle.EDITORIAL_DECORATIVE_SERIF -> if (compactMode) 33.sp else 72.sp
        else -> if (compactMode) 36.sp else 78.sp
    }
    val strokeDp = if (compactMode) 1.3.dp else 2.4.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 6.dp),
    ) {
        StyledClockDigitsText(
            clockStyle = clockStyle,
            timeText = timeText,
            fontSize = clockSize,
            strokeWidthDp = strokeDp,
            primaryColor = Color(0xFFF6F9FD),
            accent = accent,
            modifier = Modifier.testTag("aod_clock_text"),
        )
        Text(
            text = dateText,
            style = TextStyle(
                fontFamily = RobotoCondensedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = if (compactMode) 9.5.sp else 15.sp,
                letterSpacing = if (compactMode) 0.6.sp else 1.1.sp,
            ),
            color = Color(0xFF8A99AA),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag("aod_date_text"),
        )
    }
}

fun resolveAodMetrics(
    slots: List<AodMetricSlot>,
    state: PowerTelemetryState,
): List<ResolvedAodMetric> {
    val activeSlots = if (slots.size == 3) slots else AodMetricSlot.DEFAULT_THREE
    return activeSlots.map { slot ->
        when (slot) {
            AodMetricSlot.TEMP -> ResolvedAodMetric(
                slot = slot,
                label = "TEMP",
                value = if (state.hasLiveReading) state.settings.tempUnit.format(state.temperatureCelsius) else "36.8°C",
                icon = Icons.Rounded.Thermostat,
            )
            AodMetricSlot.VOLTAGE -> ResolvedAodMetric(
                slot = slot,
                label = "VOLTAGE",
                value = if (state.hasLiveReading) {
                    String.format(Locale.US, "%.3f V", state.rawVoltageMv / 1000f)
                } else {
                    "3.702 V"
                },
                icon = Icons.Rounded.ElectricBolt,
            )
            AodMetricSlot.ESTIMATE -> ResolvedAodMetric(
                slot = slot,
                label = "ESTIMATE",
                value = if (state.hasLiveReading) state.estimatedTimeShort else "24 min",
                icon = Icons.Rounded.Schedule,
            )
            AodMetricSlot.CURRENT -> ResolvedAodMetric(
                slot = slot,
                label = "CURRENT",
                value = if (state.hasLiveReading) {
                    val sign = if (state.currentNowMa >= 0) "+" else ""
                    "$sign${state.currentNowMa} mA"
                } else {
                    "+3820 mA"
                },
                icon = Icons.Rounded.Speed,
            )
            AodMetricSlot.WATTAGE -> ResolvedAodMetric(
                slot = slot,
                label = "WATTAGE",
                value = if (state.hasLiveReading) {
                    String.format(Locale.US, "%.1f W", state.wattage)
                } else {
                    "18.5 W"
                },
                icon = Icons.Rounded.Bolt,
            )
            AodMetricSlot.HEALTH -> ResolvedAodMetric(
                slot = slot,
                label = "HEALTH",
                value = if (state.hasLiveReading) {
                    state.healthEstimatePercent?.let { "$it%" } ?: state.healthLabel
                } else {
                    "98%"
                },
                icon = Icons.Rounded.FavoriteBorder,
            )
        }
    }
}

/**
 * Renders the 3 chosen Battery Details metrics in all 4 AmpereFlow card layouts:
 * 1. [BatteryDetailsCardStyle.OUTLINED_CARDS]
 * 2. [BatteryDetailsCardStyle.MINIMAL_BORDERLESS]
 * 3. [BatteryDetailsCardStyle.FILLED_DARK_PILLS]
 * 4. [BatteryDetailsCardStyle.COMPACT_SINGLE_BAR]
 */
@Composable
fun AodBatteryDetailsRow(
    style: BatteryDetailsCardStyle,
    metrics: List<ResolvedAodMetric>,
    accent: Color,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
) {
    val safeMetrics = metrics.take(3)
    if (safeMetrics.isEmpty()) return

    val iconSize = if (compactMode) 11.dp else 17.dp
    val valueSize = if (compactMode) 9.5.sp else 14.5.sp
    val labelSize = if (compactMode) 7.sp else 10.sp

    when (style) {
        BatteryDetailsCardStyle.OUTLINED_CARDS -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 10.dp),
            ) {
                safeMetrics.forEach { metric ->
                    Surface(
                        shape = RoundedCornerShape(if (compactMode) 10.dp else 16.dp),
                        color = Color.Black,
                        border = BorderStroke(1.dp, Color(0xFF1F2730)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (compactMode) 4.dp else 8.dp,
                                    vertical = if (compactMode) 6.dp else 12.dp,
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 5.dp),
                        ) {
                            Icon(
                                imageVector = metric.icon,
                                contentDescription = metric.label,
                                tint = accent,
                                modifier = Modifier.size(iconSize),
                            )
                            Text(
                                text = metric.value,
                                style = TextStyle(
                                    fontFamily = MontserratAlternatesFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = valueSize,
                                ),
                                color = Color(0xFFF3F6FA),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = metric.label,
                                style = MonoLabelStyle.copy(
                                    fontSize = labelSize,
                                    letterSpacing = if (compactMode) 0.4.sp else 0.8.sp,
                                ),
                                color = Color(0xFF7B8A9B),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        BatteryDetailsCardStyle.MINIMAL_BORDERLESS -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = if (compactMode) 4.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                safeMetrics.forEachIndexed { idx, metric ->
                    if (idx > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(if (compactMode) 24.dp else 38.dp)
                                .background(Color(0xFF202933)),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = if (compactMode) 3.dp else 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (compactMode) 1.5.dp else 4.dp),
                    ) {
                        Icon(
                            imageVector = metric.icon,
                            contentDescription = metric.label,
                            tint = accent.copy(alpha = 0.85f),
                            modifier = Modifier.size(iconSize),
                        )
                        Text(
                            text = metric.value,
                            style = TextStyle(
                                fontFamily = MontserratAlternatesFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = valueSize,
                            ),
                            color = Color(0xFFF3F6FA),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = metric.label,
                            style = MonoLabelStyle.copy(fontSize = labelSize),
                            color = Color(0xFF7B8A9B),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }

        BatteryDetailsCardStyle.FILLED_DARK_PILLS -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 9.dp),
            ) {
                safeMetrics.forEach { metric ->
                    Surface(
                        shape = RoundedCornerShape(if (compactMode) 12.dp else 20.dp),
                        color = Color(0xFF141714),
                        border = BorderStroke(0.8.dp, accent.copy(alpha = 0.22f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (compactMode) 4.dp else 8.dp,
                                    vertical = if (compactMode) 6.dp else 11.dp,
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 5.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accent.copy(alpha = 0.16f),
                                modifier = Modifier.size(if (compactMode) 16.dp else 26.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = metric.icon,
                                        contentDescription = metric.label,
                                        tint = accent,
                                        modifier = Modifier.size(if (compactMode) 9.5.dp else 14.dp),
                                    )
                                }
                            }
                            Text(
                                text = metric.value,
                                style = TextStyle(
                                    fontFamily = MontserratAlternatesFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = valueSize,
                                ),
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = metric.label,
                                style = MonoLabelStyle.copy(fontSize = labelSize),
                                color = Color(0xFF8A99AA),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        BatteryDetailsCardStyle.COMPACT_SINGLE_BAR -> {
            Surface(
                shape = CircleShape,
                color = Color(0xFF101417),
                border = BorderStroke(1.dp, Color(0xFF1F2730)),
                modifier = modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (compactMode) 8.dp else 14.dp,
                            vertical = if (compactMode) 6.dp else 10.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    safeMetrics.forEachIndexed { idx, metric ->
                        if (idx > 0) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(if (compactMode) 12.dp else 16.dp)
                                    .background(Color(0xFF25303C)),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp),
                        ) {
                            Icon(
                                imageVector = metric.icon,
                                contentDescription = metric.label,
                                tint = accent,
                                modifier = Modifier.size(if (compactMode) 10.dp else 14.dp),
                            )
                            Text(
                                text = metric.value,
                                style = TextStyle(
                                    fontFamily = MontserratAlternatesFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (compactMode) 8.5.sp else 13.sp,
                                ),
                                color = Color(0xFFF3F6FA),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        BatteryDetailsCardStyle.ICON_TOP_OUTLINED -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 10.dp),
            ) {
                safeMetrics.forEach { metric ->
                    Surface(
                        shape = RoundedCornerShape(if (compactMode) 10.dp else 16.dp),
                        color = Color(0xFF04090B),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.36f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (compactMode) 4.dp else 8.dp,
                                    vertical = if (compactMode) 6.dp else 12.dp,
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.5.dp else 6.dp),
                        ) {
                            Icon(
                                imageVector = metric.icon,
                                contentDescription = metric.slot.label,
                                tint = accent,
                                modifier = Modifier.size(if (compactMode) 12.dp else 19.dp),
                            )
                            Text(
                                text = metric.value,
                                style = TextStyle(
                                    fontFamily = MontserratAlternatesFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = valueSize,
                                ),
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = metric.slot.label,
                                style = TextStyle(
                                    fontFamily = RobotoCondensedFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = if (compactMode) 7.5.sp else 11.sp,
                                    letterSpacing = 0.4.sp,
                                ),
                                color = Color(0xFF7D94A1),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        BatteryDetailsCardStyle.MINIMAL_CIRCULAR_BADGES -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = if (compactMode) {
                    Arrangement.SpaceEvenly
                } else {
                    Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                safeMetrics.forEach { metric ->
                    val circleModifier = if (compactMode) {
                        Modifier.size(56.dp)
                    } else {
                        Modifier
                            .weight(1f)
                            .widthIn(max = 104.dp)
                            .aspectRatio(1f)
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF04080A),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
                        modifier = circleModifier,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (compactMode) 3.dp else 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(
                                space = if (compactMode) 1.5.dp else 4.dp,
                                alignment = Alignment.CenterVertically,
                            ),
                        ) {
                            Text(
                                text = metric.value,
                                style = TextStyle(
                                    fontFamily = MontserratAlternatesFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (compactMode) 9.sp else 14.5.sp,
                                ),
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = metric.slot.label,
                                style = TextStyle(
                                    fontFamily = RobotoCondensedFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = if (compactMode) 7.sp else 10.5.sp,
                                    letterSpacing = 0.4.sp,
                                ),
                                color = Color(0xFF7D94A1),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        BatteryDetailsCardStyle.TOP_GLOW_GRADIENT -> {
            val cardShape = RoundedCornerShape(if (compactMode) 10.dp else 16.dp)
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 10.dp),
            ) {
                safeMetrics.forEach { metric ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(cardShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        accent.copy(alpha = 0.18f),
                                        Color(0xFF070B0D),
                                    ),
                                ),
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        accent.copy(alpha = 0.46f),
                                        accent.copy(alpha = 0.14f),
                                    ),
                                ),
                                shape = cardShape,
                            )
                            .padding(
                                horizontal = if (compactMode) 4.dp else 8.dp,
                                vertical = if (compactMode) 6.dp else 12.dp,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.5.dp else 6.dp),
                        ) {
                            Icon(
                                imageVector = metric.icon,
                                contentDescription = metric.slot.label,
                                tint = accent,
                                modifier = Modifier.size(if (compactMode) 12.dp else 19.dp),
                            )
                            Text(
                                text = metric.value,
                                style = TextStyle(
                                    fontFamily = MontserratAlternatesFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = valueSize,
                                ),
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = metric.slot.label,
                                style = TextStyle(
                                    fontFamily = RobotoCondensedFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = if (compactMode) 7.5.sp else 11.sp,
                                    letterSpacing = 0.4.sp,
                                ),
                                color = Color(0xFF839AA7),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AodNotificationIconsRow(
    notifications: List<AodNotificationBadge>,
    accent: Color,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
    showMockFallback: Boolean = false,
) {
    val badges = if (notifications.isNotEmpty()) {
        notifications
    } else if (showMockFallback) {
        listOf(
            AodNotificationBadge("m1", "mail", "Messages", "M", "New Message"),
            AodNotificationBadge("m2", "cal", "Calendar", "C", "Reminder"),
            AodNotificationBadge("m3", "chat", "Chat", "W", "Unread"),
        )
    } else {
        emptyList()
    }
    if (badges.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Notifications,
            contentDescription = "Active notifications",
            tint = accent.copy(alpha = 0.72f),
            modifier = Modifier.size(if (compactMode) 10.dp else 14.dp),
        )
        badges.take(5).forEach { badge ->
            Surface(
                shape = CircleShape,
                color = Color(0xFF121820),
                border = BorderStroke(0.8.dp, accent.copy(alpha = 0.42f)),
                modifier = Modifier.size(if (compactMode) 16.dp else 24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = badge.initial,
                        style = MonoLabelStyle.copy(
                            fontSize = if (compactMode) 8.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color(0xFFE6EEF8),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
fun AodMediaControlsBar(
    media: AodMediaSnapshot?,
    accent: Color,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
    showMockFallback: Boolean = false,
) {
    val activeMedia = media ?: if (showMockFallback) {
        AodMediaSnapshot(
            title = "Midnight Telemetry",
            artist = "Ambient Audio",
            isPlaying = true,
            packageName = "preview",
        )
    } else {
        null
    } ?: return

    Surface(
        shape = RoundedCornerShape(if (compactMode) 12.dp else 20.dp),
        color = Color(0xFF0E1216),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compactMode) 8.dp else 12.dp,
                    vertical = if (compactMode) 4.dp else 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(if (compactMode) 12.dp else 18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeMedia.title,
                        style = TextStyle(
                            fontFamily = MontserratAlternatesFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (compactMode) 8.5.sp else 12.5.sp,
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = activeMedia.artist,
                        style = MonoLabelStyle.copy(fontSize = if (compactMode) 7.sp else 10.sp),
                        color = Color(0xFF8A99AA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (compactMode) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color(0xFFD0DBE8),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (activeMedia.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color(0xFFD0DBE8),
                        modifier = Modifier.size(13.dp),
                    )
                } else {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("aod_media_prev"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous track",
                            tint = Color(0xFFD6E0EC),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("aod_media_play_pause"),
                    ) {
                        Icon(
                            imageVector = if (activeMedia.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (activeMedia.isPlaying) "Pause" else "Play",
                            tint = accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("aod_media_next"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next track",
                            tint = Color(0xFFD6E0EC),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

fun formatAodDate(option: AodDateFormatOption, date: Date = Date()): String =
    option.formatDate(date)
