package com.example.feature.snapclean

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ThemePreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SnapCleanViewModel(
    application: Application,
    @Suppress("UNUSED_PARAMETER") preferencesRepository: ThemePreferencesRepository,
) : AndroidViewModel(application) {

    private val repo = SnapCleanCoordinator.repository(application)
    private val permissionTick = MutableStateFlow(0)
    private val statusBanner = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SnapCleanUiState> = combine(
        repo.settingsFlow,
        repo.trackedScreenshotsFlow,
        SnapCleanCoordinator.isWatcherRunning,
        permissionTick,
        statusBanner,
    ) { settings, items, watcherRunning, _, banner ->
        val pending = items
            .filter { it.status != ScreenshotStatus.KEPT_FOREVER }
            .sortedBy { it.expiresAtMs }
        val vault = items
            .filter { it.status == ScreenshotStatus.KEPT_FOREVER }
            .sortedByDescending { it.capturedAtMs }

        SnapCleanUiState(
            settings = settings,
            mediaPermissionGranted = isMediaPermissionGranted(),
            notificationPermissionGranted = isNotificationPermissionGranted(),
            allFilesAccessGranted = isAllFilesAccessGranted(),
            isWatcherServiceRunning = watcherRunning,
            pendingQueue = pending,
            protectedVault = vault,
            statusBanner = banner,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SnapCleanUiState(
            mediaPermissionGranted = isMediaPermissionGranted(),
            notificationPermissionGranted = isNotificationPermissionGranted(),
            allFilesAccessGranted = isAllFilesAccessGranted(),
            isWatcherServiceRunning = SnapCleanCoordinator.isWatcherRunning.value,
        ),
    )

    init {
        // Ensure periodic background sweeper is registered and sync watcher if enabled
        SnapCleanSweepWorker.ensureScheduled(application)
        viewModelScope.launch {
            val settings = repo.getSettingsOnce()
            if (settings.autoWatchEnabled && isMediaPermissionGranted()) {
                SnapCleanWatcherService.start(getApplication())
            }
        }
    }

    fun refreshPermissions() {
        permissionTick.value += 1
    }

    fun dismissBanner() {
        statusBanner.value = null
    }

    fun setAutoWatchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !isMediaPermissionGranted()) {
                statusBanner.value = "Grant media access first so Auto-Watch can monitor Screenshots."
                return@launch
            }
            repo.setAutoWatchEnabled(enabled)
            if (enabled) {
                SnapCleanWatcherService.start(getApplication())
                SnapCleanSweepWorker.ensureScheduled(getApplication())
                statusBanner.value = "Auto-Watch active — monitoring Pictures/Screenshots locally."
            } else {
                SnapCleanWatcherService.stop(getApplication())
                statusBanner.value = "Auto-Watch paused."
            }
        }
    }

    fun selectRetentionPolicy(option: RetentionPolicyOption) {
        viewModelScope.launch {
            repo.setDefaultRetentionHours(option.hours)
            statusBanner.value = "Default retention policy set to ${option.shortLabel}."
        }
    }

    fun triggerTestDetection() {
        viewModelScope.launch(Dispatchers.IO) {
            val item = SnapCleanCoordinator.triggerTestDetection(getApplication())
            statusBanner.value = "Test screenshot queued (${item.displayName}). Check your notification shade for the 3 quick actions!"
        }
    }

    fun scanExistingScreenshots() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isMediaPermissionGranted()) {
                statusBanner.value = "Grant media access first to scan device Screenshots."
                return@launch
            }
            val candidates = SnapCleanCoordinator.queryRecentDeviceScreenshots(getApplication(), limit = 10)
            val settings = repo.getSettingsOnce()
            val now = System.currentTimeMillis()
            var addedCount = 0
            candidates.forEach { c ->
                val tracked = TrackedScreenshot(
                    id = c.mediaId,
                    uriString = c.uri.toString(),
                    displayName = c.displayName,
                    relativePath = c.relativePath,
                    sizeBytes = c.sizeBytes,
                    capturedAtMs = c.capturedAtMs,
                    expiresAtMs = now + settings.defaultRetentionHours * 3_600_000L,
                    status = ScreenshotStatus.PENDING_DELETE,
                    isTestEntry = false,
                    filePath = c.filePath,
                )
                if (repo.registerScreenshotIfNew(tracked)) {
                    addedCount += 1
                    SnapCleanSweepWorker.scheduleNextSweepFor(getApplication(), tracked.expiresAtMs)
                }
            }
            statusBanner.value = if (addedCount > 0) {
                "Imported $addedCount device screenshot(s) into the auto-clean queue."
            } else {
                "No new screenshots found in Pictures/Screenshots."
            }
        }
    }

    fun keepForever(id: String) {
        viewModelScope.launch {
            repo.markKeptForever(id)
            SnapCleanNotifications.cancelScreenshotNotification(getApplication(), id)
            statusBanner.value = "Pinned to Protected Vault."
        }
    }

    fun unpinFromVault(id: String) {
        viewModelScope.launch {
            repo.unpinToDefaultQueue(id)
            statusBanner.value = "Moved back to Pending Auto-Delete Queue."
        }
    }

    fun scheduleCustomHours(id: String, hours: Int) {
        viewModelScope.launch {
            repo.scheduleForDuration(id, hours)
            SnapCleanNotifications.cancelScreenshotNotification(getApplication(), id)
            SnapCleanSweepWorker.ensureScheduled(getApplication())
            SnapCleanSweepWorker.scheduleNextSweepFor(
                getApplication(),
                System.currentTimeMillis() + hours.coerceAtLeast(1) * 3_600_000L,
            )
            val label = if (hours >= 24 && hours % 24 == 0) "${hours / 24}d" else "${hours}h"
            statusBanner.value = "Updated timer — deletes in $label."
        }
    }

    fun deleteNow(
        item: TrackedScreenshot,
        onNeedsSystemDeleteConsent: (PendingIntent, List<TrackedScreenshot>) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val outcome = SnapCleanCoordinator.attemptDeleteScreenshot(getApplication(), item)) {
                DeleteOutcome.Deleted -> {
                    statusBanner.value = "Deleted ${item.displayName}."
                }
                is DeleteOutcome.RequiresUserConsent -> {
                    val intent = buildSystemDeletePendingIntent(listOf(outcome.uri))
                    if (intent != null) {
                        onNeedsSystemDeleteConsent(intent, listOf(item))
                    } else {
                        statusBanner.value = "Android requires manual confirmation to delete this system screenshot."
                    }
                }
                is DeleteOutcome.Failed -> {
                    statusBanner.value = "Could not delete: ${outcome.reason}"
                }
            }
        }
    }

    fun requestBatchConsentDelete(
        items: List<TrackedScreenshot>,
        onLaunchConsent: (PendingIntent, List<TrackedScreenshot>) -> Unit,
    ) {
        val uris = items.filterNot { it.isTestEntry }.mapNotNull { runCatching { Uri.parse(it.uriString) }.getOrNull() }
        if (uris.isEmpty()) return
        val pendingIntent = buildSystemDeletePendingIntent(uris)
        if (pendingIntent != null) {
            onLaunchConsent(pendingIntent, items)
        }
    }

    fun onSystemDeleteConsentGranted(items: List<TrackedScreenshot>) {
        viewModelScope.launch(Dispatchers.IO) {
            val totalBytes = items.sumOf { it.sizeBytes }
            repo.removeItemsAfterDelete(items.map { it.id }, totalBytes)
            items.forEach { SnapCleanNotifications.cancelScreenshotNotification(getApplication(), it.id) }
            statusBanner.value = "Deleted ${items.size} screenshot(s) cleanly."
        }
    }

    fun runSweepNow() {
        SnapCleanSweepWorker.runImmediateSweep(getApplication())
        statusBanner.value = "Running background auto-sweeper now..."
    }

    fun requiredRuntimePermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun buildSystemDeletePendingIntent(uris: List<Uri>): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                MediaStore.createDeleteRequest(getApplication<Application>().contentResolver, uris)
            }.getOrNull()
        } else {
            null
        }
    }

    fun buildAllFilesAccessIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${getApplication<Application>().packageName}"),
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${getApplication<Application>().packageName}")
            }
        }
    }

    fun buildAllFilesFallbackIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${getApplication<Application>().packageName}")
            }
        }
    }

    private fun isAllFilesAccessGranted(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    private fun isMediaPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.READ_MEDIA_IMAGES,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
