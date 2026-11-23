package com.example.feature.snapclean

import android.net.Uri
import java.util.Locale
import kotlin.math.max

enum class RetentionPolicyOption(
    val hours: Int,
    val shortLabel: String,
    val description: String,
) {
    ONE_DAY(24, "24h", "Auto-delete unpinned screenshots after 24 hours"),
    THREE_DAYS(72, "3d", "Default: auto-delete screenshots after 3 days"),
    SEVEN_DAYS(168, "7d", "Keep recent captures for a full week"),
    THIRTY_DAYS(720, "30d", "Monthly screenshot cleanup window");

    companion object {
        fun fromHours(hours: Int): RetentionPolicyOption =
            entries.firstOrNull { it.hours == hours } ?: THREE_DAYS
    }
}

enum class ScreenshotStatus(val key: String) {
    PENDING_DELETE("pending"),
    KEPT_FOREVER("kept"),
    NEEDS_USER_CONFIRMATION("confirm");

    companion object {
        fun fromKey(key: String?): ScreenshotStatus =
            entries.firstOrNull { it.key == key } ?: PENDING_DELETE
    }
}

data class TrackedScreenshot(
    val id: String,
    val uriString: String,
    val displayName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val capturedAtMs: Long,
    val expiresAtMs: Long,
    val status: ScreenshotStatus,
    val isTestEntry: Boolean = false,
) {
    fun formatRemainingCountdown(nowMs: Long = System.currentTimeMillis()): String {
        if (status == ScreenshotStatus.KEPT_FOREVER) return "Protected"
        if (status == ScreenshotStatus.NEEDS_USER_CONFIRMATION) return "Tap to confirm delete"
        val remainingMs = expiresAtMs - nowMs
        if (remainingMs <= 0L) return "Expired — Ready"
        val totalMinutes = max(1L, remainingMs / 60_000L)
        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val minutes = totalMinutes % 60L
        return when {
            days > 0L -> "Deletes in ${days}d ${hours}h"
            hours > 0L -> "Deletes in ${hours}h ${minutes}m"
            else -> "Deletes in ${minutes}m"
        }
    }

    fun formatSize(): String {
        val kb = sizeBytes / 1024.0
        return when {
            sizeBytes <= 0L -> "—"
            kb < 1024.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> String.format(Locale.US, "%.2f MB", kb / 1024.0)
        }
    }
}

data class SnapCleanSettings(
    val autoWatchEnabled: Boolean = false,
    val defaultRetentionHours: Int = RetentionPolicyOption.THREE_DAYS.hours,
    val totalDeletedCount: Int = 0,
    val totalReclaimedBytes: Long = 0L,
    val lastSweepAtMs: Long = 0L,
) {
    val selectedRetention: RetentionPolicyOption
        get() = RetentionPolicyOption.fromHours(defaultRetentionHours)

    fun formatReclaimedStorage(): String {
        val kb = totalReclaimedBytes / 1024.0
        return when {
            totalReclaimedBytes <= 0L -> "0 MB"
            kb < 1024.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> String.format(Locale.US, "%.1f MB", kb / 1024.0)
        }
    }
}

sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome
    data class RequiresUserConsent(val uri: Uri, val screenshotId: String) : DeleteOutcome
    data class Failed(val reason: String) : DeleteOutcome
}

data class SnapCleanUiState(
    val settings: SnapCleanSettings = SnapCleanSettings(),
    val mediaPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val isWatcherServiceRunning: Boolean = false,
    val pendingQueue: List<TrackedScreenshot> = emptyList(),
    val protectedVault: List<TrackedScreenshot> = emptyList(),
    val statusBanner: String? = null,
) {
    val allRequiredPermissionsReady: Boolean
        get() = mediaPermissionGranted && notificationPermissionGranted

    val needsConsentItems: List<TrackedScreenshot>
        get() = pendingQueue.filter { it.status == ScreenshotStatus.NEEDS_USER_CONFIRMATION }
}
