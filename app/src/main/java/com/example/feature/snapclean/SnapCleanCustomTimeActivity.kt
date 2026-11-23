package com.example.feature.snapclean

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.ThemePreferencesRepository
import com.example.ui.theme.AKitTheme
import com.example.ui.theme.MonoLabelStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SnapCleanCustomTimeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screenshotId = intent.getStringExtra(SnapCleanNotifications.EXTRA_SCREENSHOT_ID).orEmpty()
        val screenshotName = intent.getStringExtra(SnapCleanNotifications.EXTRA_SCREENSHOT_NAME)
            ?: "Captured Screenshot"
        val notificationId = intent.getIntExtra(SnapCleanNotifications.EXTRA_NOTIFICATION_ID, -1)

        if (screenshotId.isBlank()) {
            finish()
            return
        }

        val themeRepo = ThemePreferencesRepository(applicationContext)

        setContent {
            val appPrefs by themeRepo.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = com.example.data.AppPreferences(),
            )
            AKitTheme(
                themeMode = appPrefs.themeMode,
                dynamicColor = appPrefs.dynamicColor,
            ) {
                CustomRetentionTimeSheet(
                    screenshotName = screenshotName,
                    onConfirmHours = { hours ->
                        lifecycleScope.launch {
                            SnapCleanCoordinator.repository(applicationContext)
                                .scheduleForDuration(screenshotId, hours)
                            SnapCleanSweepWorker.ensureScheduled(applicationContext)
                            cancelNotification(notificationId)
                            finish()
                        }
                    },
                    onKeepForever = {
                        lifecycleScope.launch {
                            SnapCleanCoordinator.repository(applicationContext)
                                .markKeptForever(screenshotId)
                            cancelNotification(notificationId)
                            finish()
                        }
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    private fun cancelNotification(notificationId: Int) {
        if (notificationId > 0) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(notificationId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRetentionTimeSheet(
    screenshotName: String,
    onConfirmHours: (Int) -> Unit,
    onKeepForever: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var useDaysUnit by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(12f) }

    val quickPresets = remember {
        listOf(
            "1h" to 1,
            "6h" to 6,
            "12h" to 12,
            "24h" to 24,
            "3d" to 72,
            "7d" to 168,
            "14d" to 336,
        )
    }

    val computedHours = if (useDaysUnit) {
        (sliderValue.roundToInt().coerceIn(1, 30)) * 24
    } else {
        sliderValue.roundToInt().coerceIn(1, 72)
    }

    val formattedSelection = if (useDaysUnit) {
        val d = sliderValue.roundToInt().coerceIn(1, 30)
        if (d == 1) "1 Day" else "$d Days"
    } else {
        val h = sliderValue.roundToInt().coerceIn(1, 72)
        if (h == 1) "1 Hour" else "$h Hours"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("snapclean_custom_time_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp),
                    )
                    Column {
                        Text(
                            text = "Custom Auto-Delete Timer",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = screenshotName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                    )
                }
            }

            // Quick Presets Row
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "QUICK PRESETS",
                        style = MonoLabelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        quickPresets.forEach { (label, hours) ->
                            Surface(
                                onClick = { onConfirmHours(hours) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("custom_time_preset_$label"),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fine-Grained Hours / Days Picker
            Surface(
                shape = RoundedCornerShape(22.dp),
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
                        Text(
                            text = "Delete in $formattedSelection",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            UnitModePill(
                                label = "Hours",
                                selected = !useDaysUnit,
                                onClick = {
                                    useDaysUnit = false
                                    sliderValue = sliderValue.coerceIn(1f, 72f)
                                },
                            )
                            UnitModePill(
                                label = "Days",
                                selected = useDaysUnit,
                                onClick = {
                                    useDaysUnit = true
                                    sliderValue = sliderValue.coerceIn(1f, 30f)
                                },
                            )
                        }
                    }

                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = if (useDaysUnit) 1f..30f else 1f..72f,
                        modifier = Modifier.testTag("custom_time_slider"),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onKeepForever,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("custom_time_keep_forever_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Keep Forever")
                }

                Button(
                    onClick = { onConfirmHours(computedHours) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(50.dp)
                        .testTag("custom_time_apply_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoDelete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Schedule ($formattedSelection)")
                }
            }
        }
    }
}

@Composable
private fun UnitModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
