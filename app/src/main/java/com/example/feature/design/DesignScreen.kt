package com.example.feature.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AccentSwatch
import com.example.data.AppPreferences
import com.example.data.ClockNumberStyle
import com.example.data.FloatingPillStyle
import com.example.data.GaugeStyle
import com.example.feature.power.FloatingPillStylePreview
import com.example.feature.power.PowerHeroArcGauge
import com.example.feature.power.PowerViewModel
import com.example.feature.power.formatDynamicPowerBadge
import com.example.ui.components.SectionHeader
import com.example.ui.theme.CyberPeachPrimary
import com.example.ui.theme.ElectricBluePrimary
import com.example.ui.theme.EmeraldSwatchPrimary
import com.example.ui.theme.LocalAKitDesign
import com.example.ui.theme.MintEmerald
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.theme.MonochromePrimary
import com.example.ui.theme.MontserratAlternatesFontFamily
import com.example.ui.theme.RobotoCondensedFontFamily
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DesignScreen(
    preferences: AppPreferences,
    powerViewModel: PowerViewModel,
    onSelectGaugeStyle: (GaugeStyle) -> Unit,
    onSelectFloatingPillStyle: (FloatingPillStyle) -> Unit,
    onSelectClockNumberStyle: (ClockNumberStyle) -> Unit,
    onSelectAccentSwatch: (AccentSwatch) -> Unit,
    onToggleAutoLaunchCharging: (Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val powerState by powerViewModel.uiState.collectAsStateWithLifecycle()
    val design = LocalAKitDesign.current

    val previewPercent = if (powerState.hasLiveReading) powerState.exactLevelPercent else 68f
    val previewCharging = if (powerState.hasLiveReading) powerState.isCharging else true
    val previewWatts = if (powerState.hasLiveReading) powerState.wattage else 18.5f
    val previewCurrentMa = if (powerState.hasLiveReading) powerState.currentNowMa else 3850
    val previewTemp = if (powerState.hasLiveReading) {
        powerState.settings.tempUnit.format(powerState.temperatureCelsius)
    } else {
        "34.2°C"
    }

    val previewAccent by animateColorAsState(
        targetValue = if (previewCharging) design.chargingAccent else design.dischargingAccent,
        animationSpec = tween(320),
        label = "design_live_preview_accent",
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("design_screen_list"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Live Interactive Preview Box at the top
        item(key = "design_live_preview") {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, previewAccent.copy(alpha = 0.45f)),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .testTag("design_live_preview_box"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = previewAccent,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Design Studio Preview",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = previewAccent.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = "LIVE SYNC",
                                style = MonoLabelStyle,
                                color = previewAccent,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            )
                        }
                    }

                    // Live Miniature Gauge + Floating Pill side-by-side preview stage
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Live Floating Overlay Pill preview at top of stage
                            FloatingPillStylePreview(
                                style = preferences.floatingPillStyle,
                                accentColor = previewAccent,
                                wattsText = String.format(Locale.US, "%.1fW", previewWatts),
                                currentText = "${if (previewCurrentMa >= 0) "+" else ""}${previewCurrentMa}mA",
                                tempText = previewTemp,
                                percentText = "${previewPercent.toInt()}%",
                            )

                            // Live 270° Arc Gauge honoring GaugeStyle + ClockNumberStyle + AccentSwatch
                            PowerHeroArcGauge(
                                levelPercent = previewPercent,
                                isCharging = previewCharging,
                                statusText = if (previewCharging) "CHARGING" else "DISCHARGING",
                                powerBadgeText = formatDynamicPowerBadge(previewCharging, previewWatts),
                                estimateText = null,
                                accentColor = previewAccent,
                                gaugeStyle = preferences.gaugeStyle,
                                clockNumberStyle = preferences.clockNumberStyle,
                                gaugeSize = 156.dp,
                                compactMode = true,
                            )

                            Text(
                                text = "${preferences.gaugeStyle.label}  •  ${preferences.clockNumberStyle.label}  •  ${preferences.accentSwatch.label}",
                                style = MonoLabelStyle.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // 2. Trigger Switch Card
        item(key = "design_trigger_switch") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .testTag("design_trigger_switch_card"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bedtime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Show Floating Pill / Charging Screen",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Automatically display when locked & charging",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Switch(
                        checked = preferences.autoLaunchAodOnPlugIn,
                        onCheckedChange = onToggleAutoLaunchCharging,
                        modifier = Modifier.testTag("design_auto_charging_switch"),
                    )
                }
            }
        }

        // 3. Accent Color Swatches Row
        item(key = "design_swatches_header") {
            SectionHeader(
                title = "Accent Color Swatch",
                badgeText = preferences.accentSwatch.label.uppercase(Locale.US),
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "design_swatches_row") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccentSwatch.entries.forEach { swatch ->
                        val isSelected = preferences.accentSwatch == swatch
                        val swatchColor = swatchDisplayColor(swatch)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectAccentSwatch(swatch) }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .testTag("swatch_${swatch.storageKey}"),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = CircleShape,
                                    )
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (swatch == AccentSwatch.DYNAMIC_AUTO) {
                                            Brush.sweepGradient(
                                                listOf(
                                                    MintEmerald,
                                                    ElectricBluePrimary,
                                                    CyberPeachPrimary,
                                                    MintEmerald,
                                                ),
                                            )
                                        } else {
                                            Brush.linearGradient(listOf(swatchColor, swatchColor))
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected ${swatch.label}",
                                        tint = Color(0xFF0A100A),
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else if (swatch == AccentSwatch.DYNAMIC_AUTO) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = swatch.label,
                                        tint = Color(0xFF0A100A),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Text(
                                text = when (swatch) {
                                    AccentSwatch.DYNAMIC_AUTO -> "Auto"
                                    AccentSwatch.EMERALD_GREEN -> "Emerald"
                                    AccentSwatch.CYBER_PEACH -> "Peach"
                                    AccentSwatch.ELECTRIC_BLUE -> "Blue"
                                    AccentSwatch.MONOCHROME -> "Mono"
                                },
                                style = MonoLabelStyle.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        // 4. Gauge / Speedometer Style Horizontal Picker
        item(key = "design_gauge_picker_header") {
            SectionHeader(
                title = "Gauge / Speedometer Style",
                badgeText = "3 STYLES",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "design_gauge_picker_row") {
            LazyRow(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .testTag("design_gauge_picker_row"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(GaugeStyle.entries, key = { it.storageKey }) { style ->
                    val selected = preferences.gaugeStyle == style
                    VisualStylePickerCard(
                        title = style.label,
                        subtitle = style.subtitle,
                        selected = selected,
                        accentColor = previewAccent,
                        onClick = { onSelectGaugeStyle(style) },
                        testTagName = "gauge_style_${style.storageKey}",
                    ) {
                        MiniGaugeThumbnail(
                            style = style,
                            accentColor = previewAccent,
                        )
                    }
                }
            }
        }

        // 5. Floating Overlay Pill Style Horizontal Picker
        item(key = "design_pill_picker_header") {
            SectionHeader(
                title = "Floating Overlay Pill Style",
                badgeText = "LIVE HUD",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "design_pill_picker_row") {
            LazyRow(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .testTag("design_pill_picker_row"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(FloatingPillStyle.entries, key = { it.storageKey }) { style ->
                    val selected = preferences.floatingPillStyle == style
                    VisualStylePickerCard(
                        title = style.label,
                        subtitle = style.subtitle,
                        selected = selected,
                        accentColor = previewAccent,
                        onClick = { onSelectFloatingPillStyle(style) },
                        testTagName = "pill_style_${style.storageKey}",
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FloatingPillStylePreview(
                                style = style,
                                accentColor = previewAccent,
                                wattsText = "18.5W",
                                currentText = "+3.8A",
                                tempText = "34°C",
                                percentText = "68%",
                            )
                        }
                    }
                }
            }
        }

        // 6. Clock / Number Style Horizontal Picker
        item(key = "design_clock_picker_header") {
            SectionHeader(
                title = "Clock / Number Style",
                badgeText = "GAUGE & AOD",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "design_clock_picker_row") {
            LazyRow(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .testTag("design_clock_picker_row"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ClockNumberStyle.entries, key = { it.storageKey }) { style ->
                    val selected = preferences.clockNumberStyle == style
                    VisualStylePickerCard(
                        title = style.label,
                        subtitle = style.subtitle,
                        selected = selected,
                        accentColor = previewAccent,
                        onClick = { onSelectClockNumberStyle(style) },
                        testTagName = "clock_style_${style.storageKey}",
                    ) {
                        MiniClockNumberThumbnail(
                            style = style,
                            accentColor = previewAccent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualStylePickerCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTagName: String,
    previewContent: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .width(194.dp)
            .testTag(testTagName),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    previewContent()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = MontserratAlternatesFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor,
                        modifier = Modifier.size(20.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFF0A100A),
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniGaugeThumbnail(
    style: GaugeStyle,
    accentColor: Color,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = Modifier.size(68.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startAngle = 135f
            val totalSweep = 270f
            val activeSweep = totalSweep * 0.72f
            val center = Offset(size.width / 2f, size.height / 2f)

            when (style) {
                GaugeStyle.MINIMAL_THIN_ARC -> {
                    val strokePx = 4.dp.toPx()
                    val inset = 6.dp.toPx()
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    drawArc(
                        color = trackColor,
                        startAngle = startAngle,
                        sweepAngle = totalSweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = accentColor,
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }

                GaugeStyle.THICK_RING -> {
                    val strokePx = 9.dp.toPx()
                    val inset = 8.dp.toPx()
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    drawArc(
                        color = trackColor,
                        startAngle = startAngle,
                        sweepAngle = totalSweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = accentColor,
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }

                GaugeStyle.SEGMENTED_TICK -> {
                    val ticks = 20
                    val active = 14
                    val outer = size.minDimension / 2f - 3.dp.toPx()
                    val inner = outer - 7.dp.toPx()
                    for (i in 0 until ticks) {
                        val deg = startAngle + totalSweep * (i.toFloat() / (ticks - 1))
                        val rad = deg * PI / 180.0
                        drawLine(
                            color = if (i < active) accentColor else trackColor,
                            start = Offset(
                                center.x + (inner * cos(rad)).toFloat(),
                                center.y + (inner * sin(rad)).toFloat(),
                            ),
                            end = Offset(
                                center.x + (outer * cos(rad)).toFloat(),
                                center.y + (outer * sin(rad)).toFloat(),
                            ),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
        Text(
            text = "72%",
            style = TextStyle(
                fontFamily = MontserratAlternatesFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MiniClockNumberThumbnail(
    style: ClockNumberStyle,
    accentColor: Color,
) {
    val fontFamily = when (style) {
        ClockNumberStyle.MONTSERRAT_BOLD -> MontserratAlternatesFontFamily
        ClockNumberStyle.CONDENSED_TECH -> RobotoCondensedFontFamily
        ClockNumberStyle.OUTLINE_DIGITAL -> MontserratAlternatesFontFamily
    }
    val fontWeight = when (style) {
        ClockNumberStyle.MONTSERRAT_BOLD -> FontWeight.ExtraBold
        ClockNumberStyle.CONDENSED_TECH -> FontWeight.Bold
        ClockNumberStyle.OUTLINE_DIGITAL -> FontWeight.Normal
    }
    val letterSpacing = when (style) {
        ClockNumberStyle.MONTSERRAT_BOLD -> (-1).sp
        ClockNumberStyle.CONDENSED_TECH -> 0.sp
        ClockNumberStyle.OUTLINE_DIGITAL -> 2.5.sp
    }

    Column(
        modifier = Modifier.height(68.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "12:48",
            style = TextStyle(
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontSize = 28.sp,
                letterSpacing = letterSpacing,
            ),
            color = if (style == ClockNumberStyle.OUTLINE_DIGITAL) {
                accentColor
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = "85% • +18.5W",
            style = MonoLabelStyle.copy(fontSize = 10.sp),
            color = accentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun swatchDisplayColor(swatch: AccentSwatch): Color = when (swatch) {
    AccentSwatch.DYNAMIC_AUTO -> MintEmerald
    AccentSwatch.EMERALD_GREEN -> EmeraldSwatchPrimary
    AccentSwatch.CYBER_PEACH -> CyberPeachPrimary
    AccentSwatch.ELECTRIC_BLUE -> ElectricBluePrimary
    AccentSwatch.MONOCHROME -> MonochromePrimary
}
