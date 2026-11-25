package com.example.feature.snapclean

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SnapCleanActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val screenshotId = intent.getStringExtra(SnapCleanNotifications.EXTRA_SCREENSHOT_ID)
        val notificationId = intent.getIntExtra(SnapCleanNotifications.EXTRA_NOTIFICATION_ID, -1)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SnapCleanCoordinator.repository(context)
                when (action) {
                    ACTION_DELETE_24H -> {
                        if (!screenshotId.isNullOrBlank()) {
                            repo.scheduleForDuration(screenshotId, hoursFromNow = 24)
                            SnapCleanSweepWorker.ensureScheduled(context)
                        }
                    }

                    ACTION_KEEP_FOREVER -> {
                        if (!screenshotId.isNullOrBlank()) {
                            repo.markKeptForever(screenshotId)
                        }
                    }

                    ACTION_DISMISSED -> {
                        // Item is already scheduled with Default Retention Policy upon capture;
                        // ensure periodic WorkManager sweeper is active.
                        SnapCleanSweepWorker.ensureScheduled(context)
                    }

                    ACTION_STOP_WATCHER -> {
                        repo.setAutoWatchEnabled(false)
                        SnapCleanWatcherService.stop(context)
                    }

                    ACTION_RESTART_WATCHER -> {
                        val settings = repo.getSettingsOnce()
                        if (settings.autoWatchEnabled) {
                            SnapCleanWatcherService.start(context)
                        }
                    }
                }

                if (notificationId > 0) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.cancel(notificationId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DELETE_24H = "com.example.feature.snapclean.ACTION_DELETE_24H"
        const val ACTION_KEEP_FOREVER = "com.example.feature.snapclean.ACTION_KEEP_FOREVER"
        const val ACTION_DISMISSED = "com.example.feature.snapclean.ACTION_DISMISSED"
        const val ACTION_STOP_WATCHER = "com.example.feature.snapclean.ACTION_STOP_WATCHER"
        const val ACTION_RESTART_WATCHER = "com.example.feature.snapclean.ACTION_RESTART_WATCHER"
    }
}
