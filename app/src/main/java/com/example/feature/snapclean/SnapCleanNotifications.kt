package com.example.feature.snapclean

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlin.math.abs

object SnapCleanNotifications {

    const val CHANNEL_WATCHER_ID = "akit_snapclean_watcher_channel"
    const val CHANNEL_CAPTURE_ID = "akit_snapclean_capture_channel"
    const val WATCHER_NOTIFICATION_ID = 5201
    const val SWEEP_CONSENT_NOTIFICATION_ID = 5202

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val watcherChannel = NotificationChannel(
            CHANNEL_WATCHER_ID,
            "SnapClean Screenshot Watcher",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps on-device screenshot detection active in the background."
            setShowBadge(false)
        }

        val captureChannel = NotificationChannel(
            CHANNEL_CAPTURE_ID,
            "Instant Screenshot Actions",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Quick actions when a new screenshot is captured (24h, Custom Time, Keep Forever)."
            enableVibration(true)
        }

        nm.createNotificationChannel(watcherChannel)
        nm.createNotificationChannel(captureChannel)
    }

    fun buildWatcherForegroundNotification(
        context: Context,
        defaultRetentionHours: Int,
        pendingCount: Int,
    ): Notification {
        ensureChannels(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            500,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getBroadcast(
            context,
            501,
            Intent(context, SnapCleanActionReceiver::class.java).apply {
                action = SnapCleanActionReceiver.ACTION_STOP_WATCHER
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val policyLabel = RetentionPolicyOption.fromHours(defaultRetentionHours).shortLabel
        return NotificationCompat.Builder(context, CHANNEL_WATCHER_ID)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("SnapClean Auto-Watch Active")
            .setContentText("Default: $policyLabel • $pendingCount queued for auto-clean")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, "Pause Watch", stopIntent)
            .build()
    }

    fun showScreenshotCapturedNotification(
        context: Context,
        screenshot: TrackedScreenshot,
        defaultRetentionHours: Int,
    ) {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notificationId = notificationIdFor(screenshot.id)
        val baseRequestCode = abs(screenshot.id.hashCode() % 100_000) * 10

        val delete24hPending = PendingIntent.getBroadcast(
            context,
            baseRequestCode + 1,
            Intent(context, SnapCleanActionReceiver::class.java).apply {
                action = SnapCleanActionReceiver.ACTION_DELETE_24H
                putExtra(EXTRA_SCREENSHOT_ID, screenshot.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val customTimePending = PendingIntent.getActivity(
            context,
            baseRequestCode + 2,
            Intent(context, SnapCleanCustomTimeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SCREENSHOT_ID, screenshot.id)
                putExtra(EXTRA_SCREENSHOT_NAME, screenshot.displayName)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val keepForeverPending = PendingIntent.getBroadcast(
            context,
            baseRequestCode + 3,
            Intent(context, SnapCleanActionReceiver::class.java).apply {
                action = SnapCleanActionReceiver.ACTION_KEEP_FOREVER
                putExtra(EXTRA_SCREENSHOT_ID, screenshot.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissedPending = PendingIntent.getBroadcast(
            context,
            baseRequestCode + 4,
            Intent(context, SnapCleanActionReceiver::class.java).apply {
                action = SnapCleanActionReceiver.ACTION_DISMISSED
                putExtra(EXTRA_SCREENSHOT_ID, screenshot.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openAppPending = PendingIntent.getActivity(
            context,
            baseRequestCode + 5,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val policyLabel = RetentionPolicyOption.fromHours(defaultRetentionHours).shortLabel
        val builder = NotificationCompat.Builder(context, CHANNEL_CAPTURE_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("New Screenshot Captured")
            .setContentText("${screenshot.displayName} • Auto-deletes in $policyLabel if ignored")
            .setSubText(screenshot.formatSize())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppPending)
            .setDeleteIntent(dismissedPending)
            .addAction(0, "Delete in 24h", delete24hPending)
            .addAction(0, "Custom Time", customTimePending)
            .addAction(0, "Keep Forever", keepForeverPending)

        // Attach crisp screenshot preview bitmap when available on device
        if (!screenshot.isTestEntry) {
            val previewBitmap = loadThumbnailSafely(context, screenshot.uriString)
            if (previewBitmap != null) {
                builder.setLargeIcon(previewBitmap)
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(previewBitmap)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText("${screenshot.displayName} • Auto-deletes in $policyLabel"),
                )
            }
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${screenshot.displayName} (${screenshot.formatSize()})\nChoose an action below, or dismiss to keep your $policyLabel default schedule."),
            )
        }

        nm.notify(notificationId, builder.build())
    }

    fun showConsentNeededNotification(context: Context, pendingConsentCount: Int) {
        if (pendingConsentCount <= 0) return
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val openPending = PendingIntent.getActivity(
            context,
            510,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_CAPTURE_ID)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("SnapClean Sweep Ready")
            .setContentText("$pendingConsentCount expired screenshot(s) ready for 1-tap system cleanup.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()
        nm.notify(SWEEP_CONSENT_NOTIFICATION_ID, notification)
    }

    fun cancelScreenshotNotification(context: Context, screenshotId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(notificationIdFor(screenshotId))
    }

    fun notificationIdFor(screenshotId: String): Int =
        6000 + abs(screenshotId.hashCode() % 30_000)

    fun loadThumbnailSafely(context: Context, uriString: String, widthPx: Int = 512, heightPx: Int = 320): Bitmap? {
        return runCatching {
            val uri = Uri.parse(uriString)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(widthPx, heightPx), null)
            } else {
                null
            }
        }.getOrNull()
    }

    const val EXTRA_SCREENSHOT_ID = "extra_screenshot_id"
    const val EXTRA_SCREENSHOT_NAME = "extra_screenshot_name"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
}
