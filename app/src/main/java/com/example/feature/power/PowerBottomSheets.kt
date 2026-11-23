package com.example.feature.power

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MonoLabelStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerDiagnosticsSheet(
    state: PowerTelemetryState,
    onToggleDualCell: (Boolean) -> Unit,
    onSelectTempUnit: (TempUnit) -> Unit,
    onSelectDesignCapacity: (Int) -> Unit,
    onResetAutoCapacity: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = state.settings
    val capacityPresets = listOf(4000, 4500, 5000, 5500, 6000)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("power_diagnostics_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitleHeader(
                title = "Battery Calibration & Health",
                icon = Icons.Rounded.Tune,
                onDismiss = onDismiss,
            )

            // Dual-Cell Mode Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dual-Cell / Charge-Pump (2×)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Applies 2× calibration to charging current and wattage for fast-charging hardware while keeping displayed voltage as single-cell.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.dualCellMode,
                        onCheckedChange = onToggleDualCell,
                        modifier = Modifier.testTag("sheet_dual_cell_switch"),
                    )
                }
            }

            // Temperature Unit Selector
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Temperature Display Unit",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TempUnit.entries.forEach { unit ->
                            val selected = unit == settings.tempUnit
                            Surface(
                                onClick = { onSelectTempUnit(unit) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (unit == TempUnit.CELSIUS) "Celsius (°C)" else "Fahrenheit (°F)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Capacity & Health Estimator Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Design Capacity Reference",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                text = "Est: ${state.estimatedCapacityMah?.let { "$it mAh" } ?: "Calibrating"} / ${settings.designCapacityMah} mAh",
                                style = MonoLabelStyle,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            onClick = onResetAutoCapacity,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.testTag("power_auto_capacity_button"),
                        ) {
                            Text(
                                text = "AUTO",
                                style = MonoLabelStyle,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        capacityPresets.forEach { mah ->
                            val selected = settings.designCapacityMah == mah
                            Surface(
                                onClick = { onSelectDesignCapacity(mah) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${mah / 1000.0}k".replace(".0k", "k"),
                                        style = MonoLabelStyle,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingHudConfigSheet(
    state: PowerTelemetryState,
    onToggleHud: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onChangeOpacity: (Float) -> Unit,
    onSelectAccent: (PowerAccentPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = state.settings

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("floating_hud_config_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitleHeader(
                title = "Floating Overlay HUD Pill",
                icon = Icons.Rounded.Layers,
                onDismiss = onDismiss,
            )

            // Live Interactive Pill Preview
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "LIVE OVERLAY PREVIEW",
                        style = MonoLabelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF090D13).copy(alpha = settings.hudOpacity),
                        border = BorderStroke(
                            width = 1.3.dp,
                            color = settings.hudAccent.composeColor.copy(alpha = settings.hudOpacity * 0.85f),
                        ),
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "%s %.1fW  %dmA  %s  ×",
                                if (state.isCharging) "⚡" else "•",
                                state.wattage,
                                abs(state.currentNowMa),
                                settings.tempUnit.format(state.temperatureCelsius),
                            ),
                            style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
                            color = settings.hudAccent.composeColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                    Text(
                        text = "Draggable over games & apps • Tap × on pill anytime to hide",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Enable Toggle or Permission Request
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                if (!state.overlayPermissionGranted) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Overlay Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Allow A-Kit to draw its mini wattage pill on top of other apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick = onRequestOverlayPermission,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("grant_overlay_permission_button"),
                        ) {
                            Text("Grant Display Over Other Apps")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Floating HUD Pill",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (settings.hudEnabled) "Active across system screens" else "Disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.hudEnabled,
                            onCheckedChange = onToggleHud,
                            modifier = Modifier.testTag("sheet_hud_enable_switch"),
                        )
                    }
                }
            }

            // Opacity & Accent Customization
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Pill Opacity",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${(settings.hudOpacity * 100).roundToInt()}%",
                            style = MonoLabelStyle,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = settings.hudOpacity,
                        onValueChange = onChangeOpacity,
                        valueRange = 0.4f..1.0f,
                        modifier = Modifier.testTag("hud_opacity_slider"),
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                    Text(
                        text = "Accent Color",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    AccentColorRow(
                        selected = settings.hudAccent,
                        onSelect = onSelectAccent,
                        tagPrefix = "hud_accent",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingAodConfigSheet(
    settings: PowerSettings,
    onSelectStyle: (AodVisualStyle) -> Unit,
    onSelectAccent: (PowerAccentPreset) -> Unit,
    onLaunchAod: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("charging_aod_config_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitleHeader(
                title = "Charging AOD Display",
                icon = Icons.Rounded.Bedtime,
                onDismiss = onDismiss,
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Visual Gauge Style",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    AodVisualStyle.entries.forEach { style ->
                        val selected = style == settings.aodStyle
                        Surface(
                            onClick = { onSelectStyle(style) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("aod_style_${style.key}"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = style.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        text = style.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                    Text(
                        text = "AOD Accent Color",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AccentColorRow(
                        selected = settings.aodAccent,
                        onSelect = onSelectAccent,
                        tagPrefix = "aod_accent",
                    )

                    Text(
                        text = "Includes True #000000 AMOLED canvas, automatic periodic OLED pixel-shift burn-in protection, and Double-Tap to Exit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = {
                    onDismiss()
                    onLaunchAod()
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("sheet_launch_aod_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Launch Charging AOD",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionAlarmsSheet(
    state: PowerTelemetryState,
    onRequestNotificationPermission: () -> Unit,
    onToggleHighCharge: (Boolean) -> Unit,
    onChangeHighThreshold: (Int) -> Unit,
    onToggleLowBattery: (Boolean) -> Unit,
    onChangeLowThreshold: (Int) -> Unit,
    onToggleOverheat: (Boolean) -> Unit,
    onChangeOverheatThreshold: (Int) -> Unit,
    onTestAlert: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = state.settings

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("protection_alarms_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SheetTitleHeader(
                title = "Smart Battery Protection",
                icon = Icons.Rounded.Security,
                onDismiss = onDismiss,
            )

            if (!state.notificationPermissionGranted) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Enable notifications for reliable alarm alerts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        FilledTonalButton(
                            onClick = onRequestNotificationPermission,
                            shape = CircleShape,
                            modifier = Modifier.testTag("grant_alarm_notif_button"),
                        ) {
                            Text("Allow")
                        }
                    }
                }
            }

            // 1. High Charge Limit
            AlarmConfigCard(
                icon = Icons.Rounded.BatteryChargingFull,
                title = "High Charge Limit",
                subtitle = "Sound & vibrate when charging reaches limit",
                valueLabel = "${settings.highChargeThreshold}%",
                enabled = settings.highChargeAlarmEnabled,
                onToggle = onToggleHighCharge,
                sliderValue = settings.highChargeThreshold.toFloat(),
                sliderRange = 70f..100f,
                steps = 5,
                onSliderChange = { onChangeHighThreshold(it.roundToInt()) },
                switchTag = "alarm_high_switch",
            )

            // 2. Low Battery Alert
            AlarmConfigCard(
                icon = Icons.Rounded.BatteryAlert,
                title = "Low Battery Alert",
                subtitle = "Warn before deep discharge degrades cell",
                valueLabel = "${settings.lowBatteryThreshold}%",
                enabled = settings.lowBatteryAlarmEnabled,
                onToggle = onToggleLowBattery,
                sliderValue = settings.lowBatteryThreshold.toFloat(),
                sliderRange = 10f..40f,
                steps = 5,
                onSliderChange = { onChangeLowThreshold(it.roundToInt()) },
                switchTag = "alarm_low_switch",
            )

            // 3. Overheat Thermal Alert
            AlarmConfigCard(
                icon = Icons.Rounded.Thermostat,
                title = "Overheat Thermal Guard",
                subtitle = "Alert when battery exceeds safe temperature",
                valueLabel = settings.tempUnit.format(settings.overheatThresholdC.toFloat()),
                enabled = settings.overheatAlarmEnabled,
                onToggle = onToggleOverheat,
                sliderValue = settings.overheatThresholdC.toFloat(),
                sliderRange = 36f..50f,
                steps = 13,
                onSliderChange = { onChangeOverheatThreshold(it.roundToInt()) },
                switchTag = "alarm_overheat_switch",
            )

            // Foreground Service Status & Sound Test
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = if (state.isGuardServiceRunning) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (state.isGuardServiceRunning) {
                                "Guard Service Active"
                            } else {
                                "Guard Service Idle"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onTestAlert,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(46.dp)
                        .testTag("test_alarm_sound_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Alert")
                }
            }
        }
    }
}

@Composable
private fun AlarmConfigCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    valueLabel: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    sliderValue: Float,
    sliderRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onSliderChange: (Float) -> Unit,
    switchTag: String,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                            ) {
                                Text(
                                    text = valueLabel,
                                    style = MonoLabelStyle,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag(switchTag),
                )
            }

            Slider(
                value = sliderValue,
                onValueChange = onSliderChange,
                valueRange = sliderRange,
                steps = steps,
            )
        }
    }
}

@Composable
private fun AccentColorRow(
    selected: PowerAccentPreset,
    onSelect: (PowerAccentPreset) -> Unit,
    tagPrefix: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PowerAccentPreset.entries.forEach { preset ->
            val isSelected = preset == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(preset) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("${tagPrefix}_${preset.key}"),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(preset.composeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = preset.label,
                            tint = Color(0xFF0A0E14),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun SheetTitleHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
            )
        }
    }
}
