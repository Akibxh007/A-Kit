package com.example.feature.power

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.ModuleHeroCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.TelemetryMetricCard
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.theme.MonoMetricStyle
import java.util.Locale
import kotlin.math.abs

private enum class PowerActiveSheet {
    NONE,
    DIAGNOSTICS,
    FLOATING_HUD,
    CHARGING_AOD,
    PROTECTION_ALARMS,
}

@Composable
fun PowerScreen(
    viewModel: PowerViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var activeSheet by rememberSaveable { mutableStateOf(PowerActiveSheet.NONE) }

    // Refresh overlay & notification permission status when returning from System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
    }

    val animatedBatteryProgress by animateFloatAsState(
        targetValue = (state.levelPercent / 100f).coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "power_battery_level_progress",
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("power_screen_list"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Optional Active Alarm Alert Banner
        if (state.activeAlertMessage != null) {
            item(key = "power_active_alert") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .testTag("power_alert_banner"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = state.activeAlertMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        IconButton(onClick = viewModel::dismissActiveAlert) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss alert",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }

        // 1. Live Wattage, Dual-Cell & Battery Hero Card
        item(key = "power_hero") {
            ModuleHeroCard(
                moduleCode = if (settings.dualCellMode) {
                    "MOD-01 • 2S DUAL-CELL"
                } else {
                    stringResource(R.string.power_module_code)
                },
                title = stringResource(R.string.power_hero_title),
                subtitle = "100% unlocked live wattage, dual-cell math, HUD overlay, Charging AOD & thermal guards.",
                icon = if (state.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.Bolt,
                accentColor = MaterialTheme.colorScheme.primary,
                accentContainerColor = MaterialTheme.colorScheme.primaryContainer,
                onAccentContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .testTag("power_hero_card"),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
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
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = if (state.isCharging) "CHARGING POWER" else "SYSTEM DRAW",
                                    style = MonoLabelStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f W", state.wattage),
                                        style = MonoMetricStyle.copy(
                                            fontSize = MaterialTheme.typography.displaySmall.fontSize,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("power_live_wattage_text"),
                                    )
                                    Text(
                                        text = "${if (state.currentNowMa >= 0) "+" else ""}${state.currentNowMa} mA",
                                        style = MonoLabelStyle.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 5.dp),
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${state.levelPercent}%",
                                    style = MonoMetricStyle.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${state.statusLabel} • ${state.pluggedLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { animatedBatteryProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round,
                        )

                        // Quick Hardware Control Pills: Dual-Cell + °C/°F + Calibration
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            QuickToggleChip(
                                label = if (settings.dualCellMode) "2S Dual-Cell: ON" else "Dual-Cell: OFF",
                                selected = settings.dualCellMode,
                                onClick = { viewModel.setDualCellMode(!settings.dualCellMode) },
                                modifier = Modifier
                                    .weight(1.25f)
                                    .testTag("power_dual_cell_toggle"),
                            )
                            QuickToggleChip(
                                label = "Unit: ${settings.tempUnit.symbol}",
                                selected = settings.tempUnit == TempUnit.FAHRENHEIT,
                                onClick = viewModel::toggleTempUnit,
                                modifier = Modifier
                                    .weight(0.85f)
                                    .testTag("power_temp_unit_toggle"),
                            )
                            Surface(
                                onClick = { activeSheet = PowerActiveSheet.DIAGNOSTICS },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("power_calibration_button"),
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = "Battery calibration",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Live Hardware Diagnostics Grid
        item(key = "power_live_header") {
            SectionHeader(
                title = "Live Hardware Diagnostics",
                badgeText = state.technology.uppercase(Locale.US),
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "power_diagnostics_grid") {
            val healthPctText = state.healthEstimatePercent?.let { "$it%" } ?: state.healthLabel
            val capacitySubText = if (state.estimatedCapacityMah != null) {
                "${state.estimatedCapacityMah} / ${settings.designCapacityMah} mAh"
            } else {
                "Design ${settings.designCapacityMah} mAh"
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TelemetryMetricCard(
                        label = stringResource(R.string.power_metric_temp),
                        value = if (state.hasLiveReading) {
                            settings.tempUnit.format(state.temperatureCelsius)
                        } else {
                            "--"
                        },
                        sublabel = if (state.temperatureCelsius >= settings.overheatThresholdC) {
                            "Above thermal limit"
                        } else {
                            "Thermal nominal"
                        },
                        icon = Icons.Rounded.Thermostat,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    TelemetryMetricCard(
                        label = stringResource(R.string.power_metric_voltage),
                        value = if (state.effectiveVoltageMv > 0) {
                            "${state.effectiveVoltageMv} mV"
                        } else {
                            "-- mV"
                        },
                        sublabel = if (settings.dualCellMode) {
                            "2S × ${state.rawVoltageMv} mV"
                        } else {
                            String.format(Locale.US, "%.2f V single-cell", state.effectiveVoltageMv / 1000f)
                        },
                        icon = Icons.Rounded.ElectricBolt,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        onClick = { activeSheet = PowerActiveSheet.DIAGNOSTICS },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("power_health_card"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Est. Health",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector = Icons.Rounded.HealthAndSafety,
                                    contentDescription = "Battery Health",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = healthPctText,
                                style = MonoMetricStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = capacitySubText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    TelemetryMetricCard(
                        label = "Current Flow",
                        value = "${abs(state.currentNowMa)} mA",
                        sublabel = String.format(Locale.US, "Peak %.1f W session", state.peakSessionWatts),
                        icon = Icons.Rounded.BatteryStd,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 3. Dedicated Pro Tools: Charging AOD, Floating HUD Pill & Smart Protection Alarms
        item(key = "power_tools_header") {
            Spacer(modifier = Modifier.height(2.dp))
            SectionHeader(
                title = "Pro Overlays, AOD & Guards",
                badgeText = "ALL UNLOCKED",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        // Charging AOD Primary Launch Card
        item(key = "power_aod_card") {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .testTag("power_aod_card"),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Box(
                                    modifier = Modifier.size(44.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bedtime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "AMOLED Charging AOD",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${settings.aodStyle.title} • ${settings.aodAccent.label} • Burn-in Shift",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        IconButton(
                            onClick = { activeSheet = PowerActiveSheet.CHARGING_AOD },
                            modifier = Modifier.testTag("power_aod_customize_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Customize Charging AOD",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Button(
                        onClick = { context.startActivity(viewModel.buildChargingAodIntent()) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("launch_charging_aod_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Launch Charging AOD",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }

        // Floating HUD Pill + Smart Protection Alarms Compact Cards
        item(key = "power_hud_and_alarms") {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Floating Overlay HUD Card
                Surface(
                    onClick = { activeSheet = PowerActiveSheet.FLOATING_HUD },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (settings.hudEnabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("power_hud_card"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = settings.hudAccent.composeColor.copy(alpha = 0.16f),
                            ) {
                                Box(
                                    modifier = Modifier.size(44.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Layers,
                                        contentDescription = null,
                                        tint = settings.hudAccent.composeColor,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Floating Overlay HUD Pill",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (!state.overlayPermissionGranted) {
                                        "Tap to grant overlay access & style"
                                    } else if (settings.hudEnabled) {
                                        "Live • ${settings.hudAccent.label} • ${(settings.hudOpacity * 100).toInt()}% opacity"
                                    } else {
                                        "Draggable mini W / mA / temp pill"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Switch(
                            checked = settings.hudEnabled && state.overlayPermissionGranted,
                            onCheckedChange = { enable ->
                                if (enable && !state.overlayPermissionGranted) {
                                    activeSheet = PowerActiveSheet.FLOATING_HUD
                                } else {
                                    viewModel.setHudEnabled(enable)
                                }
                            },
                            modifier = Modifier.testTag("power_hud_quick_switch"),
                        )
                    }
                }

                // Smart Battery Protection Alarms Card
                Surface(
                    onClick = { activeSheet = PowerActiveSheet.PROTECTION_ALARMS },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (settings.activeAlarmCount > 0) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("power_alarms_card"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Box(
                                    modifier = Modifier.size(44.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Smart Protection Alarms",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "High ${settings.highChargeThreshold}% • Low ${settings.lowBatteryThreshold}% • Thermal ${settings.tempUnit.format(settings.overheatThresholdC.toFloat())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (settings.activeAlarmCount > 0) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        ) {
                            Text(
                                text = if (settings.activeAlarmCount > 0) {
                                    "${settings.activeAlarmCount} ACTIVE"
                                } else {
                                    "CONFIGURE"
                                },
                                style = MonoLabelStyle,
                                color = if (settings.activeAlarmCount > 0) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        // 4. Unlocked Pro Charts Section
        item(key = "power_charts_header") {
            Spacer(modifier = Modifier.height(2.dp))
            SectionHeader(
                title = "Unlocked Pro Charts",
                badgeText = "CANVAS LIVE",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "power_level_bar_chart") {
            ChargingPowerByLevelCard(
                brackets = state.bracketStats,
                currentLevelPercent = state.levelPercent,
                currentWatts = state.wattage,
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "power_thermal_speed_chart") {
            LiveThermalSpeedChartCard(
                samples = state.sessionSamples,
                tempUnit = settings.tempUnit,
                peakWatts = state.peakSessionWatts,
                sessionHistory = state.sessionHistory,
                onSaveSnapshot = viewModel::saveSessionSnapshot,
                onClearHistory = viewModel::clearLocalAnalytics,
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }
    }

    // Active Bottom Sheet Router
    when (activeSheet) {
        PowerActiveSheet.DIAGNOSTICS -> {
            PowerDiagnosticsSheet(
                state = state,
                onToggleDualCell = viewModel::setDualCellMode,
                onSelectTempUnit = viewModel::setTempUnit,
                onSelectDesignCapacity = viewModel::setDesignCapacityMah,
                onResetAutoCapacity = viewModel::resetAutoDesignCapacity,
                onDismiss = { activeSheet = PowerActiveSheet.NONE },
            )
        }

        PowerActiveSheet.FLOATING_HUD -> {
            FloatingHudConfigSheet(
                state = state,
                onToggleHud = viewModel::setHudEnabled,
                onRequestOverlayPermission = {
                    runCatching { context.startActivity(viewModel.buildOverlayPermissionIntent()) }
                },
                onChangeOpacity = viewModel::setHudOpacity,
                onSelectAccent = viewModel::setHudAccent,
                onDismiss = { activeSheet = PowerActiveSheet.NONE },
            )
        }

        PowerActiveSheet.CHARGING_AOD -> {
            ChargingAodConfigSheet(
                settings = settings,
                onSelectStyle = viewModel::setAodStyle,
                onSelectAccent = viewModel::setAodAccent,
                onLaunchAod = { context.startActivity(viewModel.buildChargingAodIntent()) },
                onDismiss = { activeSheet = PowerActiveSheet.NONE },
            )
        }

        PowerActiveSheet.PROTECTION_ALARMS -> {
            ProtectionAlarmsSheet(
                state = state,
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onToggleHighCharge = viewModel::setHighChargeAlarm,
                onChangeHighThreshold = viewModel::setHighChargeThreshold,
                onToggleLowBattery = viewModel::setLowBatteryAlarm,
                onChangeLowThreshold = viewModel::setLowBatteryThreshold,
                onToggleOverheat = viewModel::setOverheatAlarm,
                onChangeOverheatThreshold = viewModel::setOverheatThreshold,
                onTestAlert = viewModel::testAlarmSoundAndVibration,
                onDismiss = { activeSheet = PowerActiveSheet.NONE },
            )
        }

        PowerActiveSheet.NONE -> Unit
    }
}

@Composable
private fun QuickToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = modifier.height(38.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MonoLabelStyle.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
