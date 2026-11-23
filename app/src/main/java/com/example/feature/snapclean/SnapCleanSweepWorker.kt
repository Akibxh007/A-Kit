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

class SnapCleanSweepWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = SnapCleanCoordinator.repository(applicationContext)
        val items = repo.getTrackedScreenshotsOnce()
        val now = System.currentTimeMillis()

        val expired = items.filter { item ->
            item.status == ScreenshotStatus.PENDING_DELETE &&
                item.expiresAtMs > 0L &&
                item.expiresAtMs <= now
        }

        var consentNeededCount = 0
        expired.forEach { item ->
            when (SnapCleanCoordinator.attemptDeleteScreenshot(applicationContext, item)) {
                is DeleteOutcome.RequiresUserConsent -> consentNeededCount += 1
                DeleteOutcome.Deleted, is DeleteOutcome.Failed -> Unit
            }
        }

        repo.recordSweepTimestamp(now)

        if (consentNeededCount > 0) {
            SnapCleanNotifications.showConsentNeededNotification(applicationContext, consentNeededCount)
        }

        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "akit_snapclean_periodic_sweep"
        private const val IMMEDIATE_WORK_NAME = "akit_snapclean_immediate_sweep"

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
