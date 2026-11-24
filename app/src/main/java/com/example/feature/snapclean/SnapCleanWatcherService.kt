package com.example.feature.snapclean

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SnapCleanWatcherService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null
    private var watchStartedAtSec: Long = 0L
    private val seenIds = LinkedHashSet<String>()

    private val screenshotObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            scheduleScreenshotCheck()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        watchStartedAtSec = (System.currentTimeMillis() / 1000L) - 5L
        SnapCleanNotifications.ensureChannels(this)

        val initialNotification = SnapCleanNotifications.buildWatcherForegroundNotification(
            context = this,
            defaultRetentionHours = RetentionPolicyOption.THREE_DAYS.hours,
            pendingCount = 0,
        )
        startForegroundSafely(initialNotification)
        SnapCleanCoordinator.setWatcherRunning(true)
        SnapCleanSweepWorker.ensureScheduled(this)

        // Seed seenIds with existing tracked items so we don't re-notify old captures
        val repo = SnapCleanCoordinator.repository(this)
        serviceScope.launch {
            val existing = repo.getTrackedScreenshotsOnce()
            synchronized(seenIds) {
                existing.forEach { seenIds.add(it.id) }
            }
        }

        // Register MediaStore ContentObserver watching External Images
        runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                screenshotObserver,
            )
        }

        // Keep ongoing notification synced with live settings & queue count
        serviceScope.launch {
            combine(repo.settingsFlow, repo.trackedScreenshotsFlow) { settings, items ->
                settings to items.count { it.status != ScreenshotStatus.KEPT_FOREVER }
            }.collect { (settings, pendingCount) ->
                if (!settings.autoWatchEnabled) {
                    stopSelf()
                    return@collect
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(
                    SnapCleanNotifications.WATCHER_NOTIFICATION_ID,
                    SnapCleanNotifications.buildWatcherForegroundNotification(
                        context = this@SnapCleanWatcherService,
                        defaultRetentionHours = settings.defaultRetentionHours,
                        pendingCount = pendingCount,
                    ),
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { contentResolver.unregisterContentObserver(screenshotObserver) }
        SnapCleanCoordinator.setWatcherRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                SnapCleanNotifications.WATCHER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(SnapCleanNotifications.WATCHER_NOTIFICATION_ID, notification)
        }
    }

    private fun scheduleScreenshotCheck() {
        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            // Brief debounce so Android SystemUI finishes writing IS_PENDING screenshot file
            delay(750L)
            val candidates = SnapCleanCoordinator.queryRecentDeviceScreenshots(
                context = applicationContext,
                limit = 5,
                minDateAddedSec = watchStartedAtSec,
            )
            if (candidates.isEmpty()) return@launch

            val repo = SnapCleanCoordinator.repository(applicationContext)
            val settings = repo.getSettingsOnce()
            val now = System.currentTimeMillis()

            candidates.forEach { candidate ->
                val isNewInSession = synchronized(seenIds) { seenIds.add(candidate.mediaId) }
                if (!isNewInSession) return@forEach

                val tracked = TrackedScreenshot(
                    id = candidate.mediaId,
                    uriString = candidate.uri.toString(),
                    displayName = candidate.displayName,
                    relativePath = candidate.relativePath,
                    sizeBytes = candidate.sizeBytes,
                    capturedAtMs = candidate.capturedAtMs,
                    expiresAtMs = now + settings.defaultRetentionHours * 3_600_000L,
                    status = ScreenshotStatus.PENDING_DELETE,
                    isTestEntry = false,
                    filePath = candidate.filePath,
                )

                val inserted = repo.registerScreenshotIfNew(tracked)
                if (inserted) {
                    SnapCleanNotifications.showScreenshotCapturedNotification(
                        context = applicationContext,
                        screenshot = tracked,
                        defaultRetentionHours = settings.defaultRetentionHours,
                    )
                    SnapCleanSweepWorker.ensureScheduled(applicationContext)
                    SnapCleanSweepWorker.scheduleNextSweepFor(applicationContext, tracked.expiresAtMs)
                }
            }
        }
    }

    companion object {
        private const val ACTION_START = "com.example.feature.snapclean.WATCHER_START"
        private const val ACTION_STOP = "com.example.feature.snapclean.WATCHER_STOP"

        fun start(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, SnapCleanWatcherService::class.java).apply {
                action = ACTION_START
            }
            runCatching { app.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            runCatching { app.stopService(Intent(app, SnapCleanWatcherService::class.java)) }
        }
    }
}
