package com.example.feature.snapclean

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Reliable offline background WorkManager worker that silently deletes expired screenshots
 * via File.delete() + MediaScannerConnection when All Files Access is granted, handles
 * already-deleted files gracefully without crashing, and schedules the next exact sweep.
 */
class SnapCleanSweepWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = SnapCleanCoordinator.repository(applicationContext)
        val items = repo.getTrackedScreenshotsOnce()
        val now = System.currentTimeMillis()

        val expired = items.filter { item ->
            (item.status == ScreenshotStatus.PENDING_DELETE ||
                (item.status == ScreenshotStatus.NEEDS_USER_CONFIRMATION && SnapCleanCoordinator.hasAllFilesAccess())) &&
                item.expiresAtMs > 0L &&
                item.expiresAtMs <= now
        }

        var consentNeededCount = 0
        expired.forEach { item ->
            val outcome = runCatching {
                SnapCleanCoordinator.attemptDeleteScreenshot(applicationContext, item)
            }.getOrElse { DeleteOutcome.Failed(it.message ?: "Sweep error") }

            if (outcome is DeleteOutcome.RequiresUserConsent) {
                consentNeededCount += 1
            }
        }

        repo.recordSweepTimestamp(now)

        if (consentNeededCount > 0 && !SnapCleanCoordinator.hasAllFilesAccess()) {
            SnapCleanNotifications.showConsentNeededNotification(applicationContext, consentNeededCount)
        }

        // Schedule next exact expiration sweep if there are upcoming pending screenshots
        val nextExpiryMs = repo.getTrackedScreenshotsOnce()
            .filter { it.status == ScreenshotStatus.PENDING_DELETE && it.expiresAtMs > now }
            .minOfOrNull { it.expiresAtMs }
        if (nextExpiryMs != null) {
            scheduleNextSweepFor(applicationContext, nextExpiryMs)
        }

        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "akit_snapclean_periodic_sweep"
        private const val IMMEDIATE_WORK_NAME = "akit_snapclean_immediate_sweep"
        private const val NEXT_EXPIRY_WORK_NAME = "akit_snapclean_next_expiry_sweep"

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<SnapCleanSweepWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            ).build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun scheduleNextSweepFor(context: Context, expiresAtMs: Long) {
            val delayMs = (expiresAtMs - System.currentTimeMillis()).coerceAtLeast(15_000L)
            val request = OneTimeWorkRequestBuilder<SnapCleanSweepWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                NEXT_EXPIRY_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun runImmediateSweep(context: Context) {
            val request = OneTimeWorkRequestBuilder<SnapCleanSweepWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
