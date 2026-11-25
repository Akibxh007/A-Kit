package com.example.feature.snapclean

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
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
import java.io.File
import java.util.Locale

class SnapCleanWatcherService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null
    private var watchStartedAtSec: Long = 0L
    private val seenIds = LinkedHashSet<String>()
    private val seenFilePaths = LinkedHashSet<String>()
    private val fileObservers = mutableListOf<FileObserver>()

    @Volatile
    private var autoWatchStillEnabled = true

    private val screenshotObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            scheduleScreenshotCheck(detectedFile = null)
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

        // Seed seenIds & seenFilePaths with existing tracked items so we don't re-notify old captures
        val repo = SnapCleanCoordinator.repository(this)
        serviceScope.launch {
            val existing = repo.getTrackedScreenshotsOnce()
            synchronized(seenIds) {
                existing.forEach {
                    seenIds.add(it.id)
                    if (it.filePath.isNotBlank()) seenFilePaths.add(it.filePath)
                }
            }
        }

        // 1. Register MediaStore ContentObserver watching External Images
        runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                screenshotObserver,
            )
        }

        // 2. Register FileObserver on /storage/emulated/0/Pictures/Screenshots & DCIM/Screenshots
        // so captures are caught immediately even after A-Kit is cleared from Recent Apps.
        registerScreenshotFileObservers()

        // Keep ongoing notification synced with live settings & queue count
        serviceScope.launch {
            combine(repo.settingsFlow, repo.trackedScreenshotsFlow) { settings, items ->
                settings to items.count { it.status != ScreenshotStatus.KEPT_FOREVER }
            }.collect { (settings, pendingCount) ->
                autoWatchStillEnabled = settings.autoWatchEnabled
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
            autoWatchStillEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (autoWatchStillEnabled) {
            scheduleImmediateRestart(applicationContext)
        }
    }

    override fun onDestroy() {
        runCatching { contentResolver.unregisterContentObserver(screenshotObserver) }
        unregisterScreenshotFileObservers()
        SnapCleanCoordinator.setWatcherRunning(false)
        serviceScope.cancel()
        if (autoWatchStillEnabled) {
            scheduleImmediateRestart(applicationContext)
        }
        super.onDestroy()
    }

    private fun registerScreenshotFileObservers() {
        unregisterScreenshotFileObservers()
        val extRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        val candidateDirs = listOfNotNull(
            File("/storage/emulated/0/Pictures/Screenshots"),
            extRoot?.let { File(it, "Pictures/Screenshots") },
            extRoot?.let { File(it, "DCIM/Screenshots") },
        ).distinctBy { it.absolutePath }

        val mask = FileObserver.CREATE or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE

        candidateDirs.forEach { dir ->
            if (!dir.exists() || !dir.isDirectory) return@forEach
            runCatching {
                val observer = createDirectoryFileObserver(dir, mask) { relativePath ->
                    if (relativePath.isNullOrBlank()) return@createDirectoryFileObserver
                    val lower = relativePath.lowercase(Locale.US)
                    // Skip Android pending/temporary files until finalized
                    if (lower.startsWith(".pending") || lower.startsWith(".") || lower.endsWith(".tmp")) {
                        return@createDirectoryFileObserver
                    }
                    val isImage = lower.endsWith(".png") ||
                        lower.endsWith(".jpg") ||
                        lower.endsWith(".jpeg") ||
                        lower.endsWith(".webp")
                    if (!isImage) return@createDirectoryFileObserver

                    val capturedFile = File(dir, relativePath)
                    scheduleScreenshotCheck(detectedFile = capturedFile)
                }
                observer.startWatching()
                fileObservers.add(observer)
            }
        }
    }

    private fun createDirectoryFileObserver(
        dir: File,
        mask: Int,
        onImageEvent: (String?) -> Unit,
    ): FileObserver {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(dir, mask) {
                override fun onEvent(event: Int, path: String?) {
                    onImageEvent(path)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(dir.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    onImageEvent(path)
                }
            }
        }
    }

    private fun unregisterScreenshotFileObservers() {
        fileObservers.forEach { obs ->
            runCatching { obs.stopWatching() }
        }
        fileObservers.clear()
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

    private fun scheduleScreenshotCheck(detectedFile: File?) {
        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            // Brief debounce so Android SystemUI finishes writing IS_PENDING screenshot file
            delay(750L)

            val repo = SnapCleanCoordinator.repository(applicationContext)
            val settings = repo.getSettingsOnce()
            if (!settings.autoWatchEnabled) return@launch
            val now = System.currentTimeMillis()

            val candidates = SnapCleanCoordinator.queryRecentDeviceScreenshots(
                context = applicationContext,
                limit = 5,
                minDateAddedSec = watchStartedAtSec,
            )

            var handledDetectedFile = false
            candidates.forEach { candidate ->
                if (detectedFile != null && candidate.filePath == detectedFile.absolutePath) {
                    handledDetectedFile = true
                }
                val isNewInSession = synchronized(seenIds) {
                    val addedId = seenIds.add(candidate.mediaId)
                    val addedPath = if (candidate.filePath.isNotBlank()) {
                        seenFilePaths.add(candidate.filePath)
                    } else {
                        true
                    }
                    addedId && addedPath
                }
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

            // Direct FileObserver fallback if MediaStore indexing lags behind filesystem creation
            if (!handledDetectedFile && detectedFile != null && detectedFile.exists() && detectedFile.length() > 0L) {
                val absPath = detectedFile.absolutePath
                val isNewFile = synchronized(seenIds) { seenFilePaths.add(absPath) }
                if (isNewFile) {
                    runCatching {
                        MediaScannerConnection.scanFile(
                            applicationContext,
                            arrayOf(absPath),
                            null,
                            null,
                        )
                    }
                    val syntheticId = "file_${absPath.hashCode().toUInt()}"
                    val tracked = TrackedScreenshot(
                        id = syntheticId,
                        uriString = Uri.fromFile(detectedFile).toString(),
                        displayName = detectedFile.name,
                        relativePath = "Pictures/Screenshots/",
                        sizeBytes = detectedFile.length(),
                        capturedAtMs = detectedFile.lastModified().takeIf { it > 0L } ?: now,
                        expiresAtMs = now + settings.defaultRetentionHours * 3_600_000L,
                        status = ScreenshotStatus.PENDING_DELETE,
                        isTestEntry = false,
                        filePath = absPath,
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
    }

    companion object {
        private const val ACTION_START = "com.example.feature.snapclean.WATCHER_START"
        private const val ACTION_STOP = "com.example.feature.snapclean.WATCHER_STOP"
        private const val RESTART_REQUEST_CODE = 4290

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

        fun scheduleImmediateRestart(context: Context) {
            val app = context.applicationContext
            val restartIntent = Intent(app, SnapCleanActionReceiver::class.java).apply {
                action = SnapCleanActionReceiver.ACTION_RESTART_WATCHER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                app,
                RESTART_REQUEST_CODE,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val triggerAt = SystemClock.elapsedRealtime() + 1_200L
            runCatching {
                alarmManager?.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            }
        }
    }
}
