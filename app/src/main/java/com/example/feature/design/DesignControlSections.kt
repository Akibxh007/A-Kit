package com.example.feature.design

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AodDateFormatOption
import com.example.data.AodDimTimeout
import com.example.data.AodHideTimeout
import com.example.data.AodMetricSlot
import com.example.data.AppPreferences
import com.example.feature.aod.AodNotificationBridgeService
import com.example.feature.aod.formatScheduleMinutes
import com.example.ui.theme.MonoLabelStyle
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhatAppearsOnScreenCard(
    preferences: AppPreferences,
    accentColor: Color,
    onUpdatePreferences: ((AppPreferences) -> AppPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationAccessGranted = remember(context, refreshTick) {
        AodNotificationBridgeService.isNotificationAccessEnabled(context)
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag("design_what_appears_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            // Battery Details Metric Selector (choose any 3 of 6)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Battery Details Metrics",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = "${preferences.aodSelectedMetrics.size}/3 ACTIVE",
                            style = MonoLabelStyle.copy(fontSize = 10.sp),
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                Text(
                    text = "Tap any metric to swap into your 3 live AOD slots.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AodMetricSlot.entries.forEach { slot ->
                        val selected = slot in preferences.aodSelectedMetrics
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onUpdatePreferences { current ->
                                    val list = current.aodSelectedMetrics.toMutableList()
                                    if (slot in list) {
                                        if (list.size > 1) {
                                            // Cycle replacement to keep 3 slots populated cleanly
                                            val fallback = AodMetricSlot.entries.firstOrNull { it !in list }
                                            if (fallback != null) {
                                                val idx = list.indexOf(slot)
                                                list[idx] = fallback
                                            }
                                        }
                                    } else {
                                        if (list.size >= 3) {
                                            list.removeAt(0)
                                        }
                                        list.add(slot)
                                    }
                                    current.copy(aodSelectedMetrics = list.take(3))
                                }
                            },
                            label = {
                                Text(
                                    text = slot.label,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.20f),
                                selectedLabelColor = accentColor,
                                selectedLeadingIconColor = accentColor,
                            ),
                            modifier = Modifier.testTag("metric_slot_${slot.storageKey}"),
                        )
                    }
                }
            }

            SectionDivider()

            DesignToggleRow(
                icon = Icons.Rounded.Bolt,
                title = "Show Watts instead of mA",
                subtitle = "Display live wattage (W) rather than milliamps (mA)",
                checked = preferences.showWattsInsteadOfMa,
                accentColor = accentColor,
                onCheckedChange = { enabled ->
                    onUpdatePreferences { it.copy(showWattsInsteadOfMa = enabled) }
                },
                testTag = "toggle_show_watts",
            )

            SectionDivider()

            DesignToggleRow(
                icon = Icons.Rounded.Percent,
                title = "Set Percentage as Primary Display",
                subtitle = "Highlight battery % inside gauge; turn off to prioritize W/mA",
                checked = preferences.percentagePrimaryDisplay,
                accentColor = accentColor,
                onCheckedChange = { enabled ->
                    onUpdatePreferences { it.copy(percentagePrimaryDisplay = enabled) }
                },
                testTag = "toggle_percent_primary",
            )

            SectionDivider()

            DesignToggleRow(
                icon = Icons.Rounded.Info,
                title = "Show More Info Button",
                subtitle = "Expandable live telemetry & quick dashboard jump on AOD",
                checked = preferences.showMoreInfoButton,
                accentColor = accentColor,
                onCheckedChange = { enabled ->
                    onUpdatePreferences { it.copy(showMoreInfoButton = enabled) }
                },
                testTag = "toggle_more_info",
            )

            SectionDivider()

            DesignToggleRow(
                icon = Icons.Rounded.MusicNote,
                title = "Show Media Player Controls",
                subtitle = "Track info + play/pause/skip bar while music is playing",
                checked = preferences.showMediaControls,
                accentColor = accentColor,
                onCheckedChange = { enabled ->
                    onUpdatePreferences { it.copy(showMediaControls = enabled) }
                },
                testTag = "toggle_media_controls",
            )

            SectionDivider()

            DesignToggleRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "Show Notifications on AOD",
                subtitle = "Compact app notification icons displayed beneath the clock",
                checked = preferences.showAodNotifications,
                accentColor = accentColor,
                onCheckedChange = { enabled ->
                    onUpdatePreferences { it.copy(showAodNotifications = enabled) }
                },
                testTag = "toggle_aod_notifications",
            )

            // 1-Tap Notification Access prompt whenever Media Controls or AOD Notifications is enabled without permission
            AnimatedVisibility(visible = (preferences.showMediaControls || preferences.showAodNotifications) && !notificationAccessGranted) {
                Surface(
                    onClick = { AodNotificationBridgeService.openNotificationAccessSettings(context) },
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("grant_notification_access_banner"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Grant Notification Access",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = accentColor,
                            )
                            Text(
                                text = "Required for live AOD notification icons & active track metadata",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Open Notification Access Settings",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            SectionDivider()

            DesignToggleRow(
                icon = Icons.Rounded.FlashlightOn,
                title = "Torch (tap & hold)",
                subtitle = "Bottom-left flashlight button requiring long-press or double-tap",
                checked = preferences.showTorchButton,
                accentColor = accentColor,
                onCheckedChange = { enabled ->
                    onUpdatePreferences { it.copy(showTorchButton = enabled) }
                },
                testTag = "toggle_torch_button",
            )

            SectionDivider()

            DesignToggleRow(
                icon = Icons.Rounded.CameraAlt,
                title = "Camera (tap & hold)",
                subtitle = "Bottom-right camera shortcut requiring long-press or double-tap",
                checked = preferences.showCameraButton,
                accentColor = accentColor,
                onCheckedChange = { enabled ->
                    onUpdatePreferences { it.copy(showCameraButton = enabled) }
                },
                testTag = "toggle_camera_button",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BehaviorControlsCard(
    preferences: AppPreferences,
    accentColor: Color,
    onUpdatePreferences: ((AppPreferences) -> AppPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingFromTime by remember { mutableStateOf(false) }
    var editingUntilTime by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag("design_behavior_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            DesignToggleRow(
                icon = Icons.Rounded.BatterySaver,
                title = "Power Saving Mode",
                subtitle = "Slows & pauses AOD gauge animations after 30s of inactivity",
                checked = preferences.powerSavingMode,
                accentColor = accentColor,
                onCheckedChange = { enabled ->
                    onUpdatePreferences { it.copy(powerSavingMode = enabled) }
                },
                testTag = "toggle_power_saving",
            )

            SectionDivider()

            // Dim Full Screen After
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Dim Full Screen After",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AodDimTimeout.entries.forEach { option ->
                        val selected = preferences.dimScreenAfter == option
                        Surface(
                            onClick = { onUpdatePreferences { it.copy(dimScreenAfter = option) } },
                            shape = CircleShape,
                            color = if (selected) accentColor.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = if (selected) 1.4.dp else 1.dp,
                                color = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dim_after_${option.storageKey}"),
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = option.label,
                                    style = MonoLabelStyle.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                                    color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }

            SectionDivider()

            // Hide Full Screen After
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Hide Full Screen After",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AodHideTimeout.entries.forEach { option ->
                        val selected = preferences.hideScreenAfter == option
                        Surface(
                            onClick = { onUpdatePreferences { it.copy(hideScreenAfter = option) } },
                            shape = CircleShape,
                            color = if (selected) accentColor.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = if (selected) 1.4.dp else 1.dp,
                                color = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("hide_after_${option.storageKey}"),
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = option.label,
                                    style = MonoLabelStyle.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                                    color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }

            SectionDivider()

            // Hide On A Schedule + From / Until Time Pickers
            DesignToggleRow(
                icon = Icons.Rounded.Schedule,
                title = "Hide On A Schedule",
                subtitle = "Suppress automatic lockscreen AOD during quiet bedtime hours",
                checked = preferences.hideOnScheduleEnabled,
                accentColor = accentColor,
                onCheckedChange = { enabled ->
                    onUpdatePreferences { it.copy(hideOnScheduleEnabled = enabled) }
                },
                testTag = "toggle_hide_schedule",
            )

            AnimatedVisibility(visible = preferences.hideOnScheduleEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ScheduleTimeChipButton(
                        label = "HIDE FROM",
                        timeText = formatScheduleMinutes(preferences.hideScheduleFromMinutes, preferences.use24HourTime),
                        accentColor = accentColor,
                        onClick = { editingFromTime = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("schedule_from_button"),
                    )
                    ScheduleTimeChipButton(
                        label = "HIDE UNTIL",
                        timeText = formatScheduleMinutes(preferences.hideScheduleUntilMinutes, preferences.use24HourTime),
                        accentColor = accentColor,
                        onClick = { editingUntilTime = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("schedule_until_button"),
                    )
                }
            }

            SectionDivider()

            DesignToggleRow(
                icon = Icons.Rounded.TouchApp,
                title = "Double Tap to Exit",
                subtitle = "Quickly dismiss Charging AOD with a double-tap anywhere",
                checked = preferences.doubleTapToExit,
                accentColor = accentColor,
                onCheckedChange = { enabled ->
                    onUpdatePreferences { it.copy(doubleTapToExit = enabled) }
                },
                testTag = "toggle_double_tap_exit",
            )

            SectionDivider()

            DesignToggleRow(
                icon = Icons.Rounded.Shield,
                title = "Enable OLED Burn-In Protection",
                subtitle = "Shifts AOD layout by 2–4dp every 60s to protect AMOLED pixels",
                checked = preferences.oledBurnInProtection,
                accentColor = accentColor,
                onCheckedChange = { enabled ->
                    onUpdatePreferences { it.copy(oledBurnInProtection = enabled) }
                },
                testTag = "toggle_oled_burn_in",
            )
        }
    }

    if (editingFromTime) {
        ScheduleTimePickerDialog(
            title = "Hide AOD From",
            initialMinutes = preferences.hideScheduleFromMinutes,
            is24Hour = preferences.use24HourTime,
            onDismiss = { editingFromTime = false },
            onConfirm = { minutes ->
                onUpdatePreferences { it.copy(hideScheduleFromMinutes = minutes) }
                editingFromTime = false
            },
        )
    }

    if (editingUntilTime) {
        ScheduleTimePickerDialog(
            title = "Hide AOD Until",
            initialMinutes = preferences.hideScheduleUntilMinutes,
            is24Hour = preferences.use24HourTime,
            onDismiss = { editingUntilTime = false },
            onConfirm = { minutes ->
                onUpdatePreferences { it.copy(hideScheduleUntilMinutes = minutes) }
                editingUntilTime = false
            },
        )
    }
}

@Composable
fun FormatControlsCard(
    preferences: AppPreferences,
    accentColor: Color,
    onUpdatePreferences: ((AppPreferences) -> AppPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sampleDate = remember { Date() }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag("design_format_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Date Formatting",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(AodDateFormatOption.entries, key = { it.storageKey }) { option ->
                        val selected = preferences.dateFormatOption == option
                        Surface(
                            onClick = { onUpdatePreferences { it.copy(dateFormatOption = option) } },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) accentColor.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.testTag("date_format_${option.storageKey}"),
                        ) {
                            Text(
                                text = option.formatDate(sampleDate),
                                style = MonoLabelStyle.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }

            SectionDivider()

            DesignToggleRow(
                icon = Icons.Rounded.AccessTime,
                title = "Use 24-hour time",
                subtitle = "Apply 24-hour formatting across AOD clock & charging history",
                checked = preferences.use24HourTime,
                accentColor = accentColor,
                onCheckedChange = { enabled ->
                    onUpdatePreferences { it.copy(use24HourTime = enabled) }
                },
                testTag = "toggle_24_hour_time",
            )
        }
    }
}

@Composable
private fun DesignToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun ScheduleTimeChipButton(
    label: String,
    timeText: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MonoLabelStyle.copy(fontSize = 9.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accentColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimePickerDialog(
    title: String,
    initialMinutes: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60).coerceIn(0, 23),
        initialMinute = (initialMinutes % 60).coerceIn(0, 59),
        is24Hour = is24Hour,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
fun LockscreenLaunchPermissionsCard(
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlayGranted = remember(context, refreshTick) {
        Settings.canDrawOverlays(context)
    }
    val fullScreenIntentGranted = remember(context, refreshTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.canUseFullScreenIntent() == true
        } else {
            true
        }
    }
    val isXiaomiDevice = remember {
        val brand = Build.BRAND.lowercase(Locale.US)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
        brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ||
            manufacturer.contains("xiaomi")
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (!overlayGranted || !fullScreenIntentGranted) {
                accentColor.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = modifier.testTag("design_lockscreen_permissions_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Lockscreen Wake & Pop-up Permissions",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = if (overlayGranted && fullScreenIntentGranted) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        accentColor.copy(alpha = 0.16f)
                    },
                ) {
                    Text(
                        text = if (overlayGranted && fullScreenIntentGranted) "READY" else "SETUP",
                        style = MonoLabelStyle.copy(fontSize = 10.sp),
                        color = if (overlayGranted && fullScreenIntentGranted) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            accentColor
                        },
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            LockscreenPermissionActionRow(
                icon = Icons.Rounded.Layers,
                title = "Display Over Other Apps",
                subtitle = "Required for Floating HUD & instant plug-in launch",
                statusLabel = if (overlayGranted) "GRANTED" else "GRANT",
                isGranted = overlayGranted,
                onClick = { openOverlayPermissionPage(context) },
                testTag = "design_perm_overlay",
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                SectionDivider()
                LockscreenPermissionActionRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Full-Screen Lockscreen Alerts",
                    subtitle = "Android 14+ wake over lockscreen when screen is off",
                    statusLabel = if (fullScreenIntentGranted) "GRANTED" else "ALLOW",
                    isGranted = fullScreenIntentGranted,
                    onClick = { openFullScreenIntentPermissionPage(context) },
                    testTag = "design_perm_fullscreen_intent",
                )
            }

            SectionDivider()
            LockscreenPermissionActionRow(
                icon = Icons.Rounded.OpenInNew,
                title = if (isXiaomiDevice) "HyperOS Background Pop-up Windows" else "OEM Background Pop-up / Lockscreen",
                subtitle = "Enable \"Display pop-up windows in background\" & \"Show on Lock screen\"",
                statusLabel = "OPEN",
                isGranted = false,
                isNeutralAction = true,
                onClick = { openHyperOsBackgroundPopupSettings(context) },
                testTag = "design_perm_hyperos_popup",
            )
        }
    }
}

@Composable
private fun LockscreenPermissionActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusLabel: String,
    isGranted: Boolean,
    isNeutralAction: Boolean = false,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        isGranted -> MaterialTheme.colorScheme.secondaryContainer
                        isNeutralAction -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isGranted) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                        Text(
                            text = statusLabel,
                            style = MonoLabelStyle.copy(fontSize = 10.sp),
                            color = when {
                                isGranted -> MaterialTheme.colorScheme.onSecondaryContainer
                                isNeutralAction -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun openOverlayPermissionPage(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
}

private fun openFullScreenIntentPermissionPage(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { openOverlayPermissionPage(context) }
    } else {
        openOverlayPermissionPage(context)
    }
}

private fun openHyperOsBackgroundPopupSettings(context: Context) {
    val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
        putExtra("extra_pkgname", context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val miuiSpecific = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
        setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
        )
        putExtra("extra_pkgname", context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(miuiSpecific) }
        .onFailure {
            runCatching { context.startActivity(miuiIntent) }
                .onFailure {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
        }
}
