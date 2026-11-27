package com.example.feature.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AccentSwatch
import com.example.data.AppPreferences
import com.example.data.BatteryDetailsCardStyle
import com.example.data.ClockNumberStyle
import com.example.data.FloatingPillStyle
import com.example.data.GaugeStyle
import com.example.feature.aod.AodBatteryDetailsRow
import com.example.feature.aod.AodClockDisplay
import com.example.feature.aod.AodMediaControlsBar
import com.example.feature.aod.AodMediaSnapshot
import com.example.feature.aod.AodNotificationBadge
import com.example.feature.aod.AodNotificationBridgeService
import com.example.feature.aod.AodNotificationIconsRow
import com.example.feature.aod.AodSpeedometerGauge
import com.example.feature.aod.MiniGaugeStyleThumbnail
import com.example.feature.aod.ResolvedAodMetric
import com.example.feature.aod.StyledClockDigitsText
import com.example.feature.aod.formatAodDate
import com.example.feature.aod.formatClockTime
import com.example.feature.aod.resolveAodMetrics
import com.example.feature.power.FloatingPillStylePreview
import com.example.feature.power.PowerMonitorService
import com.example.feature.power.PowerTelemetryEngine
import com.example.feature.power.PowerTelemetryState
import com.example.feature.power.PowerViewModel
import com.example.ui.components.SectionHeader
import com.example.ui.theme.LocalAKitDesign
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.theme.MontserratAlternatesFontFamily
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DesignScreen(
    preferences: AppPreferences,
    powerViewModel: PowerViewModel,
    onUpdatePreferences: ((AppPreferences) -> AppPreferences) -> Unit,
    onToggleAutoLaunchCharging: (Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val powerState by powerViewModel.uiState.collectAsStateWithLifecycle()
    val mediaSnapshot by AodNotificationBridgeService.mediaSnapshot.collectAsStateWithLifecycle()
    val activeNotifications by AodNotificationBridgeService.notifications.collectAsStateWithLifecycle()
    val design = LocalAKitDesign.current

    val previewAccent by animateColorAsState(
        targetValue = when (preferences.accentSwatch) {
            AccentSwatch.DYNAMIC_AUTO -> if (powerState.isCharging) design.chargingAccent else design.dischargingAccent
            else -> Color(preferences.accentSwatch.argbInt)
        },
        animationSpec = tween(280),
        label = "design_studio_accent",
    )

    var nowDate by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowDate = Date()
            delay(15_000L)
        }
    }

    val previewTimeText = remember(nowDate, preferences.use24HourTime) {
        formatClockTime(preferences.use24HourTime, nowDate)
    }
    val previewDateText = remember(nowDate, preferences.dateFormatOption) {
        formatAodDate(preferences.dateFormatOption, nowDate)
    }
    val resolvedMetrics = remember(preferences.aodSelectedMetrics, powerState) {
        resolveAodMetrics(preferences.aodSelectedMetrics, powerState)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("design_screen_list"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Interactive Live Phone Mockup Preview (Pure AMOLED #000000 Bezel) + Action Bar
        item(key = "design_phone_mockup") {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LivePhoneMockupPreview(
                    preferences = preferences,
                    powerState = powerState,
                    accentColor = previewAccent,
                    timeText = previewTimeText,
                    dateText = previewDateText,
                    metrics = resolvedMetrics,
                    media = mediaSnapshot,
                    notifications = activeNotifications,
                )

                // Action Bar: Preview Full-Screen AOD Button + Always On Display Activation Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val darkTextOnAccent = preferences.accentSwatch == AccentSwatch.PURE_WHITE ||
                        preferences.accentSwatch == AccentSwatch.LIME ||
                        preferences.accentSwatch == AccentSwatch.AMBER ||
                        preferences.accentSwatch == AccentSwatch.ELECTRIC_BLUE ||
                        preferences.accentSwatch == AccentSwatch.CYAN ||
                        preferences.accentSwatch == AccentSwatch.MINT ||
                        preferences.accentSwatch == AccentSwatch.DYNAMIC_AUTO

                    Button(
                        onClick = {
                            PowerTelemetryEngine.updateDesignPreferences(preferences)
                            PowerMonitorService.launchChargingAodImmediately(
                                context = context,
                                isManualPreview = true,
                            )
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = previewAccent,
                            contentColor = if (darkTextOnAccent) Color(0xFF070C10) else Color.White,
                        ),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("design_launch_preview_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (preferences.autoLaunchAodOnPlugIn) {
                                previewAccent.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("design_trigger_switch_card"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bedtime,
                                    contentDescription = null,
                                    tint = previewAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column {
                                    Text(
                                        text = "Always On Display",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "Show when locked & charging",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
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
            }
        }

        // 2. Lockscreen Wake & HyperOS Background Pop-up Permissions Helper
        item(key = "design_lockscreen_permission_helper") {
            LockscreenLaunchPermissionsCard(
                accentColor = previewAccent,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
            )
        }

        // 3. Clock Style Picker (9 Styles: Off + 8 distinct typography styles)
        item(key = "design_clock_header") {
            SectionHeader(
                title = "Clock Style",
                badgeText = "9 STYLES",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "design_clock_row") {
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
                        onClick = { onUpdatePreferences { it.copy(clockNumberStyle = style) } },
                        testTagName = "clock_style_${style.storageKey}",
                    ) {
                        MiniClockStyleThumbnail(
                            style = style,
                            accentColor = previewAccent,
                        )
                    }
                }
            }
        }

        // 4. Speedometer / Gauge Style Picker (14 Custom Canvas Styles)
        item(key = "design_gauge_header") {
            SectionHeader(
                title = "Speedometer & Gauge Style",
                badgeText = "14 STYLES",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "design_gauge_row") {
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
                        onClick = { onUpdatePreferences { it.copy(gaugeStyle = style) } },
                        testTagName = "gauge_style_${style.storageKey}",
                    ) {
                        MiniGaugeStyleThumbnail(
                            style = style,
                            accentColor = previewAccent,
                        )
                    }
                }
            }
        }

        // 5. Battery Details Card Style Picker (7 Layouts)
        item(key = "design_details_style_header") {
            SectionHeader(
                title = "Battery Details Style",
                badgeText = "7 LAYOUTS",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "design_details_style_row") {
            LazyRow(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .testTag("design_details_style_row"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(BatteryDetailsCardStyle.entries, key = { it.storageKey }) { style ->
                    val selected = preferences.batteryDetailsStyle == style
                    VisualStylePickerCard(
                        title = style.label,
                        subtitle = style.subtitle,
                        selected = selected,
                        accentColor = previewAccent,
                        onClick = { onUpdatePreferences { it.copy(batteryDetailsStyle = style) } },
                        testTagName = "details_style_${style.storageKey}",
                        cardWidth = 216.dp,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AodBatteryDetailsRow(
                                style = style,
                                metrics = resolvedMetrics,
                                accent = previewAccent,
                                compactMode = true,
                            )
                        }
                    }
                }
            }
        }

        // 6. Speedometer Color Swatches (10 Swatches) & Brightness Slider (20%-100%)
        item(key = "design_color_brightness_header") {
            SectionHeader(
                title = "Speedometer Color & Brightness",
                badgeText = "${preferences.accentSwatch.label.uppercase(Locale.US)} • ${(preferences.speedometerBrightness * 100f).roundToInt()}%",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "design_color_brightness_card") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.testTag("design_swatches_row"),
                    ) {
                        items(AccentSwatch.entries, key = { it.storageKey }) { swatch ->
                            val isSelected = preferences.accentSwatch == swatch
                            val swatchColor = Color(swatch.argbInt)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onUpdatePreferences { it.copy(accentSwatch = swatch) }
                                    }
                                    .padding(horizontal = 3.dp, vertical = 3.dp)
                                    .testTag("swatch_${swatch.storageKey}"),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
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
                                                        Color(0xFF38BDF8),
                                                        Color(0xFF10B981),
                                                        Color(0xFFF59E0B),
                                                        Color(0xFFD946EF),
                                                        Color(0xFF38BDF8),
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
                                            tint = if (swatch == AccentSwatch.INDIGO || swatch == AccentSwatch.CORAL_RED) {
                                                Color.White
                                            } else {
                                                Color(0xFF0A1014)
                                            },
                                            modifier = Modifier.size(18.dp),
                                        )
                                    } else if (swatch == AccentSwatch.DYNAMIC_AUTO) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = swatch.label,
                                            tint = Color(0xFF0A1014),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = swatch.label,
                                    style = MonoLabelStyle.copy(
                                        fontSize = 10.sp,
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

                    // Speedometer Brightness Slider (20% - 100%)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Brightness6,
                                    contentDescription = null,
                                    tint = previewAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Speedometer Brightness",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = "${(preferences.speedometerBrightness * 100f).roundToInt()}%",
                                style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
                                color = previewAccent,
                            )
                        }
                        Slider(
                            value = preferences.speedometerBrightness,
                            onValueChange = { level ->
                                onUpdatePreferences { it.copy(speedometerBrightness = level.coerceIn(0.20f, 1.0f)) }
                            },
                            valueRange = 0.20f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = previewAccent,
                                activeTrackColor = previewAccent,
                            ),
                            modifier = Modifier.testTag("speedometer_brightness_slider"),
                        )
                    }
                }
            }
        }

        // 7. Floating Overlay Pill Style Picker (Preserved Live HUD Customization)
        item(key = "design_pill_header") {
            SectionHeader(
                title = "Floating Overlay Pill Style",
                badgeText = "LIVE HUD",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "design_pill_row") {
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
                        onClick = { onUpdatePreferences { it.copy(floatingPillStyle = style) } },
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
                                percentText = "72%",
                            )
                        }
                    }
                }
            }
        }

        // 8. What Appears on Screen
        item(key = "design_what_appears_header") {
            SectionHeader(
                title = "What Appears on Screen",
                badgeText = "ELEMENTS",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "design_what_appears_section") {
            WhatAppearsOnScreenCard(
                preferences = preferences,
                accentColor = previewAccent,
                onUpdatePreferences = onUpdatePreferences,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
            )
        }

        // 9. Behavior Controls
        item(key = "design_behavior_header") {
            SectionHeader(
                title = "Behavior & Protection",
                badgeText = "TIMERS & OLED",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "design_behavior_section") {
            BehaviorControlsCard(
                preferences = preferences,
                accentColor = previewAccent,
                onUpdatePreferences = onUpdatePreferences,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
            )
        }

        // 10. Format Controls
        item(key = "design_format_header") {
            SectionHeader(
                title = "Format",
                badgeText = if (preferences.use24HourTime) "24-HOUR" else "12-HOUR",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "design_format_section") {
            FormatControlsCard(
                preferences = preferences,
                accentColor = previewAccent,
                onUpdatePreferences = onUpdatePreferences,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LivePhoneMockupPreview(
    preferences: AppPreferences,
    powerState: PowerTelemetryState,
    accentColor: Color,
    timeText: String,
    dateText: String,
    metrics: List<ResolvedAodMetric>,
    media: AodMediaSnapshot?,
    notifications: List<AodNotificationBadge>,
) {
    val previewPercent = if (powerState.hasLiveReading) powerState.exactLevelPercent else 74.2f
    val previewCharging = if (powerState.hasLiveReading) powerState.isCharging else true
    val previewWatts = if (powerState.hasLiveReading) powerState.wattage else 18.5f
    val previewMa = if (powerState.hasLiveReading) powerState.currentNowMa else 3820

    val bottomPowerLabel = if (preferences.showWattsInsteadOfMa) {
        String.format(Locale.US, "%s%.2f W", if (previewCharging) "" else "-", abs(previewWatts))
    } else {
        "${if (previewMa >= 0) "+" else ""}$previewMa mA"
    }

    // Sleek Phone Bezel Mockup Frame with Pure AMOLED #000000 Canvas
    Surface(
        shape = RoundedCornerShape(34.dp),
        color = Color(0xFF151920),
        border = BorderStroke(1.6.dp, accentColor.copy(alpha = 0.55f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .width(238.dp)
            .testTag("design_live_preview_box"),
    ) {
        Column(
            modifier = Modifier
                .padding(7.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Punch-hole camera / speaker cutout
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2530)),
            )

            // Live Clock + Date + Optional Notification Icons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                AodClockDisplay(
                    clockStyle = preferences.clockNumberStyle,
                    timeText = timeText,
                    dateText = dateText,
                    accent = accentColor,
                    compactMode = true,
                )
                if (preferences.showAodNotifications) {
                    AodNotificationIconsRow(
                        notifications = notifications,
                        accent = accentColor,
                        compactMode = true,
                        showMockFallback = true,
                    )
                }
            }

            // Live 12-Style Speedometer Gauge with Brightness Alpha
            AodSpeedometerGauge(
                levelPercent = previewPercent,
                wattage = previewWatts,
                currentNowMa = previewMa,
                isCharging = previewCharging,
                gaugeStyle = preferences.gaugeStyle,
                clockNumberStyle = preferences.clockNumberStyle,
                accentColor = accentColor,
                gaugeSize = 118.dp,
                compactMode = true,
                amoledMode = true,
                brightnessAlpha = preferences.speedometerBrightness,
                percentagePrimaryDisplay = preferences.percentagePrimaryDisplay,
                showWattsInsteadOfMa = preferences.showWattsInsteadOfMa,
                powerSavingSlowAnim = preferences.powerSavingMode,
                statusBadgeText = if (previewCharging) "CHARGING" else "IDLE",
            )

            // Live 4-Style Battery Details Card Row
            AodBatteryDetailsRow(
                style = preferences.batteryDetailsStyle,
                metrics = metrics,
                accent = accentColor,
                compactMode = true,
            )

            // Optional Live Media Bar
            if (preferences.showMediaControls) {
                AodMediaControlsBar(
                    media = media,
                    accent = accentColor,
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
                    compactMode = true,
                    showMockFallback = true,
                )
            }

            if (preferences.showMoreInfoButton) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "More Info",
                        style = MonoLabelStyle.copy(fontSize = 8.sp),
                        color = Color(0xFF7B8A9B),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.CallMade,
                        contentDescription = null,
                        tint = Color(0xFF7B8A9B),
                        modifier = Modifier.size(9.dp),
                    )
                }
            }

            // Bottom Row: Optional Torch | Live Power Readout | Optional Camera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (preferences.showTorchButton) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black,
                        border = BorderStroke(0.8.dp, Color(0xFF283340)),
                        modifier = Modifier.size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.FlashlightOff,
                                contentDescription = null,
                                tint = Color(0xFF8C9BAE),
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(22.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = bottomPowerLabel,
                        style = MonoLabelStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = accentColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                if (preferences.showCameraButton) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black,
                        border = BorderStroke(0.8.dp, Color(0xFF283340)),
                        modifier = Modifier.size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                tint = Color(0xFF8C9BAE),
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(22.dp))
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
    cardWidth: Dp = 176.dp,
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
            .width(cardWidth)
            .testTag(testTagName),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 6.dp),
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
                            fontSize = 12.5.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor,
                        modifier = Modifier.size(19.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFF0A1014),
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniClockStyleThumbnail(
    style: ClockNumberStyle,
    accentColor: Color,
) {
    Column(
        modifier = Modifier.height(64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (style == ClockNumberStyle.OFF) {
            Icon(
                imageVector = Icons.Rounded.VisibilityOff,
                contentDescription = "Clock off",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "HIDDEN",
                style = MonoLabelStyle.copy(fontSize = 9.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val previewFontSize = when (style) {
                ClockNumberStyle.GEOMETRIC_TECH_BLOCK -> 22.sp
                ClockNumberStyle.EDITORIAL_DECORATIVE_SERIF -> 30.sp
                else -> 27.sp
            }
            val previewDigits = when (style) {
                ClockNumberStyle.EDITORIAL_DECORATIVE_SERIF -> "7:19"
                ClockNumberStyle.BOLD_ROUNDED_SANS -> "7:20"
                ClockNumberStyle.GEOMETRIC_TECH_BLOCK -> "7:21"
                else -> "7:20"
            }
            StyledClockDigitsText(
                clockStyle = style,
                timeText = previewDigits,
                fontSize = previewFontSize,
                strokeWidthDp = 1.3.dp,
                primaryColor = MaterialTheme.colorScheme.onSurface,
                accent = accentColor,
            )
            Text(
                text = "Thu, Nov 26",
                style = MonoLabelStyle.copy(fontSize = 9.5.sp),
                color = accentColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
