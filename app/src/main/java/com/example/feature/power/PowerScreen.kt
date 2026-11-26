package com.example.feature.power

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.SectionHeader
import com.example.ui.theme.LocalAKitDesign
import com.example.ui.theme.MonoLabelStyle
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
    val design = LocalAKitDesign.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var activeSheet by rememberSaveable { mutableStateOf(PowerActiveSheet.NONE) }
    var selectedDetailMetric by rememberSaveable { mutableStateOf<PowerMetricType?>(null) }

    // Lifecycle-aware polling (1500ms ONLY while PowerScreen is visible) & permission refresh
    DisposableEffect(lifecycleOwner) {
        viewModel.setPowerScreenVisible(true)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setPowerScreenVisible(true)
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.setPowerScreenVisible(true)
                    viewModel.refreshPermissions()
                }
                Lifecycle.Event.ON_STOP -> viewModel.setPowerScreenVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setPowerScreenVisible(false)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
    }

    // Dynamic accent: Emerald / Selected Accent when charging, Soft Coral/Peach when discharging
    val heroAccent = if (state.isCharging) design.chargingAccent else design.dischargingAccent

    // Live sparkline series derived from sessionSamples
    val voltageSeries = remember(state.sessionSamples) {
        buildSparklineList(state.sessionSamples) { it.voltageMv.toFloat() }
    }
    val currentSeries = remember(state.sessionSamples) {
        buildSparklineList(state.sessionSamples) { abs(it.currentMa).toFloat() }
    }
    val wattageSeries = remember(state.sessionSamples) {
        buildSparklineList(state.sessionSamples) { it.watts }
    }
    val tempSeries = remember(state.sessionSamples) {
        buildSparklineList(state.sessionSamples) { it.tempCelsius }
    }
    val levelSeries = remember(state.sessionSamples) {
        buildSparklineList(state.sessionSamples) { it.levelPercent.toFloat() }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("power_screen_list"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
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

        // 1. Minimal, Dynamic Power Hero Section (270-Degree Arc Gauge + Power Pill)
        item(key = "power_hero_gauge") {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .testTag("power_hero_card"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Top subtle metadata bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = heroAccent.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = when {
                                    settings.dualCellMode -> "2S DUAL-CELL ACTIVE"
                                    state.isAutoDualCellDetected -> "AUTO 2S DETECTED"
                                    else -> "SINGLE-CELL LIVE"
                                },
                                style = MonoLabelStyle,
                                color = heroAccent,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }

                        Text(
                            text = "${state.pluggedLabel.uppercase(Locale.US)} • PEAK ${String.format(Locale.US, "%.1fW", state.peakSessionWatts)}",
                            style = MonoLabelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    // 12-Style Hero Gauge centered
                    PowerHeroArcGauge(
                        levelPercent = state.exactLevelPercent,
                        isCharging = state.isCharging,
                        statusText = if (state.isCharging) "CHARGING" else "DISCHARGING",
                        powerBadgeText = formatDynamicPowerBadge(state.isCharging, state.wattage),
                        estimateText = state.estimatedTimeShort,
                        accentColor = heroAccent,
                        gaugeStyle = design.gaugeStyle,
                        clockNumberStyle = design.clockNumberStyle,
                        wattage = state.wattage,
                        currentNowMa = state.currentNowMa,
                        gaugeSize = 232.dp,
                    )

                    // Compact Hardware Calibration Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickToggleChip(
                            label = if (state.effectiveDualCellActive) "2S MODE: ON" else "2S MODE: OFF",
                            selected = state.effectiveDualCellActive,
                            onClick = { viewModel.setDualCellMode(!settings.dualCellMode) },
                            modifier = Modifier
                                .weight(1.25f)
                                .testTag("power_dual_cell_toggle"),
                        )
                        QuickToggleChip(
                            label = "UNIT: ${settings.tempUnit.symbol}",
                            selected = settings.tempUnit == TempUnit.FAHRENHEIT,
                            onClick = viewModel::toggleTempUnit,
                            modifier = Modifier
                                .weight(0.85f)
                                .testTag("power_temp_unit_toggle"),
                        )
                        Surface(
                            onClick = { activeSheet = PowerActiveSheet.DIAGNOSTICS },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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

        // 2. Single-Row Status Banner directly below Arc Gauge (Floating Overlay Pill / Monitoring Active)
        item(key = "power_status_banner") {
            val hudActive = settings.hudEnabled && state.overlayPermissionGranted
            Surface(
                onClick = { activeSheet = PowerActiveSheet.FLOATING_HUD },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (hudActive) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .testTag("power_hud_card"),
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
                                    imageVector = Icons.Rounded.Layers,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (hudActive) "Floating Pill Active" else "Floating Overlay Pill",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = when {
                                    !state.overlayPermissionGranted -> "Tap to grant overlay access & style"
                                    hudActive -> "${design.floatingPillStyle.label} • Live HUD monitoring"
                                    else -> "Draggable live W / mA / temp pill"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Switch(
                        checked = hudActive,
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
        }

        // 3. 2-Column Live Telemetry Bento Grid (with animated sparkline backgrounds)
        item(key = "power_bento_header") {
            SectionHeader(
                title = "Live Hardware Metrics",
                badgeText = state.technology.uppercase(Locale.US),
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "power_bento_grid") {
            val voltageText = if (state.rawVoltageMv > 0) "${state.rawVoltageMv} mV" else "-- mV"
            val voltageSub = if (state.rawVoltageMv > 0) {
                String.format(Locale.US, "%.2f V single-cell", state.rawVoltageMv / 1000f)
            } else {
                "Single-cell"
            }

            val currentText = "${if (state.currentNowMa > 0) "+" else ""}${state.currentNowMa} mA"
            val wattageText = String.format(Locale.US, "%.1f W", state.wattage)
            val tempText = if (state.hasLiveReading) {
                settings.tempUnit.format(state.temperatureCelsius)
            } else {
                "--"
            }
            val healthText = state.healthEstimatePercent?.let { "$it%" } ?: state.healthLabel
            val maxCapValue = "${state.estimatedCapacityMah ?: settings.designCapacityMah} mAh"

            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Row 1: VOLTAGE | CURRENT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BentoMetricCard(
                        label = "VOLTAGE",
                        value = voltageText,
                        subtext = voltageSub,
                        icon = Icons.Rounded.ElectricBolt,
                        accentColor = design.chargingAccent,
                        sparklineValues = voltageSeries,
                        onClick = { selectedDetailMetric = PowerMetricType.VOLTAGE },
                        testTagName = "bento_metric_voltage",
                        modifier = Modifier.weight(1f),
                    )
                    BentoMetricCard(
                        label = "CURRENT",
                        value = currentText,
                        subtext = if (state.isCharging) "Net inflow" else "System draw",
                        icon = Icons.Rounded.Bolt,
                        accentColor = heroAccent,
                        sparklineValues = currentSeries,
                        onClick = { selectedDetailMetric = PowerMetricType.CURRENT },
                        testTagName = "bento_metric_current",
                        modifier = Modifier.weight(1f),
                    )
                }

                // Row 2: WATTAGE | TEMPERATURE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BentoMetricCard(
                        label = "WATTAGE",
                        value = wattageText,
                        subtext = String.format(Locale.US, "Peak %.1f W", state.peakSessionWatts),
                        icon = Icons.Rounded.Speed,
                        accentColor = MaterialTheme.colorScheme.primary,
                        sparklineValues = wattageSeries,
                        onClick = { selectedDetailMetric = PowerMetricType.WATTAGE },
                        testTagName = "bento_metric_wattage",
                        modifier = Modifier.weight(1f),
                    )
                    BentoMetricCard(
                        label = "TEMPERATURE",
                        value = tempText,
                        subtext = if (state.temperatureCelsius >= settings.overheatThresholdC) {
                            "Above limit"
                        } else {
                            "Thermal nominal"
                        },
                        icon = Icons.Rounded.Thermostat,
                        accentColor = design.dischargingAccent,
                        sparklineValues = tempSeries,
                        onClick = { selectedDetailMetric = PowerMetricType.TEMPERATURE },
                        testTagName = "bento_metric_temp",
                        modifier = Modifier.weight(1f),
                    )
                }

                // Row 3: HEALTH | PLUGGED
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BentoMetricCard(
                        label = "HEALTH",
                        value = healthText,
                        subtext = state.healthLabel,
                        icon = Icons.Rounded.HealthAndSafety,
                        accentColor = design.chargingAccent,
                        sparklineValues = levelSeries,
                        onClick = { selectedDetailMetric = PowerMetricType.HEALTH },
                        testTagName = "power_health_card",
                        modifier = Modifier.weight(1f),
                    )
                    BentoMetricCard(
                        label = "PLUGGED",
                        value = state.pluggedLabel,
                        subtext = state.statusLabel,
                        icon = Icons.Rounded.Power,
                        accentColor = heroAccent,
                        sparklineValues = wattageSeries,
                        onClick = { selectedDetailMetric = PowerMetricType.PLUGGED },
                        testTagName = "bento_metric_plugged",
                        modifier = Modifier.weight(1f),
                    )
                }

                // Row 4: MAX CAPACITY
                BentoMetricCard(
                    label = "MAX CAPACITY",
                    value = maxCapValue,
                    subtext = "Design ${settings.designCapacityMah} mAh • Tap for capacity insights",
                    icon = Icons.Rounded.BatteryStd,
                    accentColor = MaterialTheme.colorScheme.primary,
                    sparklineValues = levelSeries,
                    onClick = { selectedDetailMetric = PowerMetricType.MAX_CAPACITY },
                    testTagName = "bento_metric_capacity",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 4. Charging AOD & Protection Guards Section
        item(key = "power_tools_header") {
            Spacer(modifier = Modifier.height(2.dp))
            SectionHeader(
                title = "Charging AOD & Guards",
                badgeText = "ALL UNLOCKED",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "power_aod_card") {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .testTag("power_aod_card"),
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Box(
                                    modifier = Modifier.size(42.dp),
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
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${settings.aodStyle.title} • ${design.clockNumberStyle.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
                            .height(48.dp)
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
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        item(key = "power_alarms_card_item") {
            Surface(
                onClick = { activeSheet = PowerActiveSheet.PROTECTION_ALARMS },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (settings.activeAlarmCount > 0) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                modifier = Modifier
                    .widthIn(max = 640.dp)
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
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp),
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "High ${settings.highChargeThreshold}% • Low ${settings.lowBatteryThreshold}% • Thermal ${settings.tempUnit.format(settings.overheatThresholdC.toFloat())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        // 5. Unlocked Pro Charts Section
        item(key = "power_charts_header") {
            Spacer(modifier = Modifier.height(2.dp))
            SectionHeader(
                title = "Live Analytics Charts",
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

    selectedDetailMetric?.let { metric ->
        PowerMetricDetailSheet(
            metricType = metric,
            state = state,
            onToggleTempUnit = viewModel::toggleTempUnit,
            onDismiss = { selectedDetailMetric = null },
        )
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
            MaterialTheme.colorScheme.surfaceContainerHigh
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
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
