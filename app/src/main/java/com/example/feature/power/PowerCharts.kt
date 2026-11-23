package com.example.feature.power

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MonoLabelStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun ChargingPowerByLevelCard(
    brackets: List<LevelBracketStat>,
    currentLevelPercent: Int,
    currentWatts: Float,
    modifier: Modifier = Modifier,
) {
    val maxWattScale = max(
        10f,
        max(
            brackets.maxOfOrNull { max(it.avgWatts, it.peakWatts) } ?: 0f,
            currentWatts,
        ) * 1.18f,
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("power_level_bar_chart_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Charging Power by Level",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Average & peak wattage across SOC brackets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = String.format(Locale.US, "MAX %.0fW", maxWattScale),
                        style = MonoLabelStyle,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }

            // Animated bar fractions
            val animatedFractions = brackets.map { b ->
                val displayWatts = if (b.sampleCount > 0) {
                    b.avgWatts
                } else if (currentLevelPercent in b.minPercent..b.maxPercent) {
                    currentWatts
                } else {
                    0f
                }
                animateFloatAsState(
                    targetValue = (displayWatts / maxWattScale).coerceIn(0f, 1f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                    label = "bracket_bar_${b.id}",
                ).value
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val y = size.height * (i.toFloat() / gridSteps)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (i < gridSteps) dashEffect else null,
                        )
                    }

                    val count = brackets.size.coerceAtLeast(1)
                    val slotWidth = size.width / count
                    val barWidth = (slotWidth * 0.44f).coerceAtMost(44.dp.toPx())
                    val corner = CornerRadius(12.dp.toPx(), 12.dp.toPx())

                    brackets.forEachIndexed { index, bracket ->
                        val centerX = slotWidth * index + slotWidth / 2f
                        val left = centerX - barWidth / 2f
                        val isActiveBracket = currentLevelPercent in bracket.minPercent..bracket.maxPercent

                        // Background capsule track
                        drawRoundRect(
                            color = trackColor.copy(alpha = 0.7f),
                            topLeft = Offset(left, 6.dp.toPx()),
                            size = Size(barWidth, size.height - 6.dp.toPx()),
                            cornerRadius = corner,
                        )

                        val barFraction = animatedFractions[index]
                        if (barFraction > 0.01f) {
                            val usableHeight = size.height - 6.dp.toPx()
                            val barHeight = (usableHeight * barFraction).coerceAtLeast(10.dp.toPx())
                            val topY = size.height - barHeight
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = if (isActiveBracket) {
                                        listOf(primaryColor, secondaryColor)
                                    } else {
                                        listOf(
                                            primaryColor.copy(alpha = 0.65f),
                                            primaryColor.copy(alpha = 0.30f),
                                        )
                                    },
                                    startY = topY,
                                    endY = size.height,
                                ),
                                topLeft = Offset(left, topY),
                                size = Size(barWidth, barHeight),
                                cornerRadius = corner,
                            )
                        }
                    }
                }
            }

            // Bracket labels & live measured wattages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                brackets.forEach { bracket ->
                    val isActive = currentLevelPercent in bracket.minPercent..bracket.maxPercent
                    val displayWatts = if (bracket.sampleCount > 0) {
                        bracket.avgWatts
                    } else if (isActive) {
                        currentWatts
                    } else {
                        0f
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (displayWatts > 0.05f) {
                                String.format(Locale.US, "%.1fW", displayWatts)
                            } else {
                                "—"
                            },
                            style = MonoLabelStyle.copy(
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = bracket.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primary
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

@Composable
fun LiveThermalSpeedChartCard(
    samples: List<TelemetrySample>,
    tempUnit: TempUnit,
    peakWatts: Float,
    sessionHistory: List<ChargingSessionRecord>,
    onSaveSnapshot: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var historyExpanded by rememberSaveable { mutableStateOf(false) }

    val wattsColor = MaterialTheme.colorScheme.primary
    val tempColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    val latestSample = samples.lastOrNull()
    val maxTempC = samples.maxOfOrNull { it.tempCelsius } ?: 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("power_thermal_speed_line_chart_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live Thermal & Speed Profile",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Real-time wattage (W) & cell temperature (${tempUnit.symbol})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = onSaveSnapshot,
                    modifier = Modifier.testTag("save_session_snapshot_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = "Save session snapshot",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Legend Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChartLegendChip(
                    color = wattsColor,
                    label = String.format(
                        Locale.US,
                        "Power: %.1fW (Peak %.1fW)",
                        latestSample?.watts ?: 0f,
                        peakWatts,
                    ),
                )
                ChartLegendChip(
                    color = tempColor,
                    label = "Temp: ${tempUnit.format(latestSample?.tempCelsius ?: 0f)}",
                )
            }

            // Custom Smooth Dual-Curve Bezier Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                    for (i in 0..3) {
                        val y = size.height * (i / 3f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dash,
                        )
                    }

                    if (samples.size < 2) {
                        // Draw subtle baseline indicator while first 2 samples arrive
                        val midY = size.height * 0.55f
                        drawLine(
                            color = wattsColor.copy(alpha = 0.5f),
                            start = Offset(0f, midY),
                            end = Offset(size.width, midY),
                            strokeWidth = 2.dp.toPx(),
                        )
                        return@Canvas
                    }

                    val maxW = max(5f, (samples.maxOf { it.watts }) * 1.25f)
                    val minTemp = (samples.minOf { it.tempCelsius } - 4f).coerceAtMost(20f)
                    val maxTemp = max(minTemp + 10f, samples.maxOf { it.tempCelsius } + 4f)

                    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)

                    val wattPoints = samples.mapIndexed { idx, s ->
                        val x = idx * stepX
                        val normY = (s.watts / maxW).coerceIn(0f, 1f)
                        val y = size.height - (normY * (size.height - 10.dp.toPx())) - 4.dp.toPx()
                        Offset(x, y)
                    }

                    val tempPoints = samples.mapIndexed { idx, s ->
                        val x = idx * stepX
                        val normY = ((s.tempCelsius - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
                        val y = size.height - (normY * (size.height - 10.dp.toPx())) - 4.dp.toPx()
                        Offset(x, y)
                    }

                    val wattLinePath = buildSmoothPath(wattPoints)
                    val wattAreaPath = Path().apply {
                        addPath(wattLinePath)
                        lineTo(wattPoints.last().x, size.height)
                        lineTo(wattPoints.first().x, size.height)
                        close()
                    }

                    // Gradient area under Wattage curve
                    drawPath(
                        path = wattAreaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(wattsColor.copy(alpha = 0.28f), Color.Transparent),
                            startY = 0f,
                            endY = size.height,
                        ),
                    )

                    // Wattage curve stroke
                    drawPath(
                        path = wattLinePath,
                        color = wattsColor,
                        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
                    )

                    // Temperature smooth curve stroke
                    val tempPath = buildSmoothPath(tempPoints)
                    drawPath(
                        path = tempPath,
                        color = tempColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )

                    // Live tip indicator dots
                    drawCircle(
                        color = wattsColor,
                        radius = 4.dp.toPx(),
                        center = wattPoints.last(),
                    )
                    drawCircle(
                        color = tempColor,
                        radius = 3.5.dp.toPx(),
                        center = tempPoints.last(),
                    )
                }
            }

            // Local Session History Toggle Row
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Surface(
                onClick = { historyExpanded = !historyExpanded },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("toggle_session_history_button"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Local Session History (${sessionHistory.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MAX ${tempUnit.format(maxTempC)}",
                            style = MonoLabelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (historyExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "Toggle session history",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = historyExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sessionHistory.isEmpty()) {
                        Text(
                            text = "No saved sessions yet. Sessions auto-save when unplugging a charger, or tap the bookmark icon above to log a snapshot now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    } else {
                        sessionHistory.take(5).forEach { record ->
                            SessionHistoryRow(record = record, tempUnit = tempUnit)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Surface(
                                onClick = onClearHistory,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                modifier = Modifier.testTag("clear_session_history_button"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteSweep,
                                        contentDescription = "Clear history",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Text(
                                        text = "Reset Analytics",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
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

@Composable
private fun SessionHistoryRow(
    record: ChargingSessionRecord,
    tempUnit: TempUnit,
) {
    val timeLabel = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(record.endedAtMs))
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "${record.startPercent}% → ${record.endPercent}%${if (record.isDualCell) " (2S)" else ""}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "Peak %.1fW • Avg %.1fW", record.peakWatts, record.avgWatts),
                    style = MonoLabelStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Max ${tempUnit.format(record.maxTempCelsius)}",
                    style = MonoLabelStyle,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun ChartLegendChip(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MonoLabelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val midX = (prev.x + curr.x) / 2f
        path.cubicTo(
            midX,
            prev.y,
            midX,
            curr.y,
            curr.x,
            curr.y,
        )
    }
    return path
}
