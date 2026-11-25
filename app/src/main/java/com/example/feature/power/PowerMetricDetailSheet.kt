package com.example.feature.power

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAKitDesign
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.theme.MontserratAlternatesFontFamily
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class PowerMetricType(
    val title: String,
    val badge: String,
    val icon: ImageVector,
) {
    VOLTAGE("Voltage", "CELL POTENTIAL", Icons.Rounded.ElectricBolt),
    CURRENT("Current", "AMPERAGE FLOW", Icons.Rounded.Bolt),
    WATTAGE("Wattage", "POWER RATE", Icons.Rounded.Speed),
    TEMPERATURE("Temperature", "THERMAL SENSOR", Icons.Rounded.Thermostat),
    HEALTH("Battery Health", "CAPACITY RATIO", Icons.Rounded.HealthAndSafety),
    PLUGGED("Power Source", "CHARGER STATE", Icons.Rounded.Power),
    MAX_CAPACITY("Max Capacity", "CELL ENERGY", Icons.Rounded.BatteryStd),
}

private enum class MetricTimeRange(
    val label: String,
    val durationMs: Long,
) {
    ONE_MIN("1m", 60_000L),
    TEN_MIN("10m", 600_000L),
    ONE_HOUR("1h", 3_600_000L),
    SIX_HOURS("6h", 21_600_000L),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerMetricDetailSheet(
    metricType: PowerMetricType,
    state: PowerTelemetryState,
    onToggleTempUnit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val design = LocalAKitDesign.current
    val settings = state.settings
    var selectedRange by remember(metricType) { mutableStateOf(MetricTimeRange.TEN_MIN) }

    val accent = when (metricType) {
        PowerMetricType.TEMPERATURE -> design.dischargingAccent
        PowerMetricType.CURRENT -> if (state.isCharging) design.chargingAccent else design.dischargingAccent
        PowerMetricType.VOLTAGE, PowerMetricType.HEALTH -> design.chargingAccent
        PowerMetricType.WATTAGE, PowerMetricType.PLUGGED, PowerMetricType.MAX_CAPACITY ->
            MaterialTheme.colorScheme.primary
    }

    val liveReading = remember(metricType, state) {
        formatLiveReading(metricType, state)
    }
    val liveSubtitle = remember(metricType, state) {
        formatLiveSubtitle(metricType, state)
    }
    val explanation = remember(metricType) {
        metricTechnicalExplanation(metricType)
    }

    // Filter samples by selected time range
    val filteredSamples = remember(state.sessionSamples, selectedRange) {
        val all = state.sessionSamples
        if (all.isEmpty()) {
            emptyList()
        } else {
            val latestTs = all.last().timestampMs
            val cutoff = latestTs - selectedRange.durationMs
            val inWindow = all.filter { it.timestampMs >= cutoff }
            if (inWindow.size >= 2) inWindow else all.takeLast(12)
        }
    }

    val rawSeries = remember(filteredSamples, metricType, settings.tempUnit, state) {
        extractMetricSeries(filteredSamples, metricType, state)
    }

    val chartPoints = remember(rawSeries) {
        downsampleSeries(rawSeries, maxPoints = 64)
    }

    val minVal = rawSeries.minOrNull()
    val avgVal = if (rawSeries.isNotEmpty()) rawSeries.average().toFloat() else null
    val maxVal = rawSeries.maxOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .testTag("power_metric_detail_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top Header Row
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
                        color = accent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = metricType.icon,
                                contentDescription = metricType.title,
                                tint = accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Column {
                        Text(
                            text = metricType.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = metricType.badge,
                            style = MonoLabelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                if (metricType == PowerMetricType.TEMPERATURE) {
                    Surface(
                        onClick = onToggleTempUnit,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                        modifier = Modifier.testTag("metric_detail_temp_unit_chip"),
                    ) {
                        Text(
                            text = "UNIT ${settings.tempUnit.symbol}",
                            style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
                            color = accent,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                    ) {
                        Text(
                            text = if (state.isCharging) "CHARGING" else "DISCHARGING",
                            style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
                            color = accent,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // Large Live Reading Hero Banner
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "LIVE TELEMETRY READING",
                        style = MonoLabelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = liveReading,
                        style = TextStyle(
                            fontFamily = MontserratAlternatesFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 38.sp,
                            letterSpacing = (-1.0).sp,
                        ),
                        color = accent,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.testTag("metric_detail_live_value"),
                    )
                    Text(
                        text = liveSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Time-Range Selector Chips (1m, 10m, 1h, 6h)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricTimeRange.entries.forEach { range ->
                    val selected = range == selectedRange
                    Surface(
                        onClick = { selectedRange = range },
                        shape = CircleShape,
                        color = if (selected) {
                            accent.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("metric_range_${range.label}"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = range.label,
                                style = MonoLabelStyle.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            // Live Detailed Canvas Line + Area Chart
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${metricType.title.uppercase(Locale.US)} TREND (${selectedRange.label.uppercase(Locale.US)})",
                            style = MonoLabelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(
                            text = "${filteredSamples.size} SAMPLES",
                            style = MonoLabelStyle,
                            color = accent,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    MetricDetailLineChart(
                        values = chartPoints,
                        accent = accent,
                        maxLabel = maxVal?.let { formatStatValue(metricType, it, settings.tempUnit) } ?: "--",
                        minLabel = minVal?.let { formatStatValue(metricType, it, settings.tempUnit) } ?: "--",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp),
                    )
                }
            }

            // 3-Column Stat Cards: MINIMUM | AVERAGE | MAXIMUM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricStatCard(
                    label = "MINIMUM",
                    value = minVal?.let { formatStatValue(metricType, it, settings.tempUnit) } ?: "--",
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                MetricStatCard(
                    label = "AVERAGE",
                    value = avgVal?.let { formatStatValue(metricType, it, settings.tempUnit) } ?: "--",
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
                MetricStatCard(
                    label = "MAXIMUM",
                    value = maxVal?.let { formatStatValue(metricType, it, settings.tempUnit) } ?: "--",
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            // Technical Explanation Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = explanation.heading,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = explanation.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MetricDetailLineChart(
    values: List<Float>,
    accent: Color,
    maxLabel: String,
    minLabel: String,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 18.dp, bottom = 18.dp),
        ) {
            val w = size.width
            val h = size.height
            if (w <= 4f || h <= 4f) return@Canvas

            // Horizontal dashed reference lines (top, mid, bottom)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            listOf(0f, h * 0.5f, h).forEach { y ->
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect,
                )
            }

            if (values.isEmpty()) return@Canvas

            val minV = values.minOrNull() ?: 0f
            val maxV = values.maxOrNull() ?: 1f
            val span = max(0.01f, maxV - minV)
            val padY = h * 0.12f
            val usableH = (h - padY * 2f).coerceAtLeast(4f)

            val pts = if (values.size == 1) {
                val midY = h * 0.5f
                listOf(Offset(0f, midY), Offset(w, midY))
            } else {
                val stepX = w / (values.size - 1).coerceAtLeast(1)
                values.mapIndexed { idx, v ->
                    val norm = if (maxV - minV < 0.0005f) 0.5f else ((v - minV) / span).coerceIn(0f, 1f)
                    Offset(idx * stepX, (h - padY) - norm * usableH)
                }
            }

            val linePath = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    val prev = pts[i - 1]
                    val curr = pts[i]
                    val midX = (prev.x + curr.x) / 2f
                    cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                }
            }

            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(pts.last().x, h)
                lineTo(pts.first().x, h)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.01f)),
                    startY = 0f,
                    endY = h,
                ),
            )
            drawPath(
                path = linePath,
                color = accent,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
            )

            // Live terminal glow dot
            val last = pts.last()
            drawCircle(
                color = accent.copy(alpha = 0.28f),
                radius = 7.dp.toPx(),
                center = last,
            )
            drawCircle(
                color = Color.White,
                radius = 3.2.dp.toPx(),
                center = last,
            )
        }

        Text(
            text = "MAX $maxLabel",
            style = MonoLabelStyle.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.TopEnd),
        )

        Text(
            text = "MIN $minLabel",
            style = MonoLabelStyle.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun MetricStatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MonoLabelStyle.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = MontserratAlternatesFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
                color = accent,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class MetricExplanation(
    val heading: String,
    val body: String,
)

private fun formatLiveReading(type: PowerMetricType, state: PowerTelemetryState): String = when (type) {
    PowerMetricType.VOLTAGE -> if (state.rawVoltageMv > 0) "${state.rawVoltageMv} mV" else "-- mV"
    PowerMetricType.CURRENT -> "${if (state.currentNowMa > 0) "+" else ""}${state.currentNowMa} mA"
    PowerMetricType.WATTAGE -> String.format(Locale.US, "%.2f W", state.wattage)
    PowerMetricType.TEMPERATURE -> if (state.hasLiveReading) {
        state.settings.tempUnit.format(state.temperatureCelsius)
    } else {
        "--"
    }
    PowerMetricType.HEALTH -> state.healthEstimatePercent?.let { "$it%" } ?: state.healthLabel
    PowerMetricType.PLUGGED -> state.pluggedLabel
    PowerMetricType.MAX_CAPACITY -> "${state.estimatedCapacityMah ?: state.settings.designCapacityMah} mAh"
}

private fun formatLiveSubtitle(type: PowerMetricType, state: PowerTelemetryState): String = when (type) {
    PowerMetricType.VOLTAGE -> String.format(
        Locale.US,
        "%.3f V single-cell terminal voltage • %s",
        state.rawVoltageMv / 1000f,
        state.technology,
    )
    PowerMetricType.CURRENT -> if (state.effectiveDualCellActive && state.isCharging) {
        "2S Charge-Pump 2× calibration active • Net battery inflow"
    } else if (state.isCharging) {
        "Single-cell calibrated inflow current"
    } else {
        "Live system discharge current draw"
    }
    PowerMetricType.WATTAGE -> String.format(
        Locale.US,
        "Session peak %.1f W • %s",
        state.peakSessionWatts,
        state.estimatedTimeLabel,
    )
    PowerMetricType.TEMPERATURE -> "Thermal alarm guard at ${state.settings.tempUnit.format(state.settings.overheatThresholdC.toFloat())}"
    PowerMetricType.HEALTH -> "OS status: ${state.healthLabel} • Coulomb-counter estimated capacity health"
    PowerMetricType.PLUGGED -> "State: ${state.statusLabel} • ${state.estimatedTimeLabel}"
    PowerMetricType.MAX_CAPACITY -> "Rated design capacity: ${state.settings.designCapacityMah} mAh"
}

private fun extractMetricSeries(
    samples: List<TelemetrySample>,
    type: PowerMetricType,
    state: PowerTelemetryState,
): List<Float> {
    if (samples.isEmpty()) {
        return when (type) {
            PowerMetricType.VOLTAGE -> listOf(state.rawVoltageMv.toFloat())
            PowerMetricType.CURRENT -> listOf(abs(state.currentNowMa).toFloat())
            PowerMetricType.WATTAGE -> listOf(state.wattage)
            PowerMetricType.TEMPERATURE -> listOf(state.settings.tempUnit.convertValue(state.temperatureCelsius))
            PowerMetricType.HEALTH -> listOf((state.healthEstimatePercent ?: 100).toFloat())
            PowerMetricType.PLUGGED -> listOf(state.wattage)
            PowerMetricType.MAX_CAPACITY -> listOf((state.estimatedCapacityMah ?: state.settings.designCapacityMah).toFloat())
        }
    }
    return when (type) {
        PowerMetricType.VOLTAGE -> samples.map { it.voltageMv.toFloat() }
        PowerMetricType.CURRENT -> samples.map { abs(it.currentMa).toFloat() }
        PowerMetricType.WATTAGE -> samples.map { it.watts }
        PowerMetricType.TEMPERATURE -> samples.map { state.settings.tempUnit.convertValue(it.tempCelsius) }
        PowerMetricType.HEALTH -> samples.map { it.levelPercent.toFloat() }
        PowerMetricType.PLUGGED -> samples.map { it.watts }
        PowerMetricType.MAX_CAPACITY -> samples.map { it.levelPercent.toFloat() }
    }
}

private fun formatStatValue(
    type: PowerMetricType,
    value: Float,
    tempUnit: TempUnit,
): String = when (type) {
    PowerMetricType.VOLTAGE -> "${value.roundToInt()} mV"
    PowerMetricType.CURRENT -> "${value.roundToInt()} mA"
    PowerMetricType.WATTAGE -> String.format(Locale.US, "%.1f W", value)
    PowerMetricType.TEMPERATURE -> String.format(Locale.US, "%.1f%s", value, tempUnit.symbol)
    PowerMetricType.HEALTH -> "${value.roundToInt()}%"
    PowerMetricType.PLUGGED -> String.format(Locale.US, "%.1f W", value)
    PowerMetricType.MAX_CAPACITY -> "${value.roundToInt()}%"
}

private fun downsampleSeries(values: List<Float>, maxPoints: Int): List<Float> {
    if (values.size <= maxPoints) return values
    val step = values.size.toFloat() / maxPoints
    return List(maxPoints) { i ->
        val idx = (i * step).toInt().coerceIn(0, values.lastIndex)
        values[idx]
    }
}

private fun metricTechnicalExplanation(type: PowerMetricType): MetricExplanation = when (type) {
    PowerMetricType.VOLTAGE -> MetricExplanation(
        heading = "Single-Cell Voltage & Li-Ion Stress",
        body = "Lithium-ion phone cells typically operate between 3.3V (empty) and 4.40V (100% full). Prolonged time above 4.25V accelerates electrolyte oxidation; stopping charge near 80–85% keeps voltage lower and noticeably extends cycle life.",
    )
    PowerMetricType.CURRENT -> MetricExplanation(
        heading = "Charging Current & C-Rate",
        body = "Current (mA) measures how quickly charge flows into or out of the battery pack. Modern charge-pump fast chargers divide input voltage to push high current safely, tapering down above 75% to prevent lithium plating on the anode.",
    )
    PowerMetricType.WATTAGE -> MetricExplanation(
        heading = "Real-Time Power Delivery (V × A)",
        body = "Wattage combines cell voltage and net current to reflect true energy transfer speed. Peak wattage occurs in the Constant-Current (CC) phase below 50%, then steps down smoothly as cell voltage rises or temperature climbs.",
    )
    PowerMetricType.TEMPERATURE -> MetricExplanation(
        heading = "Thermal Envelope & Longevity",
        body = "Heat is the #1 accelerator of permanent Li-ion capacity loss. Optimal operating and charging temperatures stay between 20°C and 38°C. Sustaining 42°C+ during fast charging increases internal resistance—enable Thermal Guard to stay alerted.",
    )
    PowerMetricType.HEALTH -> MetricExplanation(
        heading = "Estimated State of Health (SoH)",
        body = "State of Health compares your battery's real hardware coulomb-counter charge capacity against its rated factory design capacity. Values above 85% represent strong daily endurance; below 80% indicates noticeable chemical aging.",
    )
    PowerMetricType.PLUGGED -> MetricExplanation(
        heading = "Power Source & Adapter Negotiation",
        body = "Android reports whether power is coming from AC Fast (USB-PD / PPS / QC), standard USB (500–900mA), Wireless Qi pad, or internal Battery. Wired USB-PD adapters run cooler and more efficiently than inductive wireless pads.",
    )
    PowerMetricType.MAX_CAPACITY -> MetricExplanation(
        heading = "Design vs. Usable Capacity (mAh)",
        body = "Design capacity is the factory-rated milliamp-hour specification of your cell, while live estimated capacity tracks how much charge the pack actually holds today based on Android's hardware fuel gauge readings.",
    )
}
