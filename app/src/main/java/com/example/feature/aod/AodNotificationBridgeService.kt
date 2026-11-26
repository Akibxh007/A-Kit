package com.example.feature.aod

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

@Immutable
data class AodMediaSnapshot(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val packageName: String,
)

@Immutable
data class AodNotificationBadge(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val initial: String,
    val title: String,
)

/**
 * Local offline NotificationListenerService bridging active OS notifications and
 * MediaSession playback controls directly to the AMOLED Charging AOD screen.
 */
class AodNotificationBridgeService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishMediaState(activeController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishMediaState(activeController)
        }

        override fun onSessionDestroyed() {
            refreshMediaControllers()
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        bindPrimaryController(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val component = ComponentName(this, AodNotificationBridgeService::class.java)
        runCatching {
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
        }
        refreshMediaControllers()
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        _isConnected.value = false
        detachController()
        runCatching {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
        _mediaSnapshot.value = null
        _notifications.value = emptyList()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshNotifications()
        refreshMediaControllers()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshNotifications()
        refreshMediaControllers()
    }

    private fun refreshMediaControllers() {
        val component = ComponentName(this, AodNotificationBridgeService::class.java)
        val controllers = runCatching {
            mediaSessionManager?.getActiveSessions(component).orEmpty()
        }.getOrDefault(emptyList())
        bindPrimaryController(controllers)
    }

    private fun bindPrimaryController(controllers: List<MediaController>) {
        val preferred = controllers.firstOrNull { controller ->
            val state = controller.playbackState?.state
            state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        } ?: controllers.firstOrNull { controller ->
            controller.metadata != null
        } ?: controllers.firstOrNull()

        if (activeController?.sessionToken != preferred?.sessionToken) {
            detachController()
            activeController = preferred
            preferred?.let { ctrl ->
                runCatching { ctrl.registerCallback(controllerCallback) }
            }
        }
        currentControllerRef = activeController
        publishMediaState(activeController)
    }

    private fun detachController() {
        activeController?.let { ctrl ->
            runCatching { ctrl.unregisterCallback(controllerCallback) }
        }
        activeController = null
        currentControllerRef = null
    }

    private fun publishMediaState(controller: MediaController?) {
        if (controller == null) {
            _mediaSnapshot.value = null
            return
        }
        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
            ?: resolveAppLabel(controller.packageName)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.takeIf { it.isNotBlank() }
            ?: "Playing Media"
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING ||
            controller.playbackState?.state == PlaybackState.STATE_BUFFERING

        _mediaSnapshot.value = AodMediaSnapshot(
            title = title,
            artist = artist,
            isPlaying = playing,
            packageName = controller.packageName,
        )
    }

    private fun refreshNotifications() {
        val active = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
        val ownPkg = packageName
        val deduped = linkedMapOf<String, AodNotificationBadge>()

        for (sbn in active) {
            if (sbn.packageName == ownPkg) continue
            if (sbn.isOngoing) continue
            val extras = sbn.notification?.extras
            val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val label = resolveAppLabel(sbn.packageName)
            val initial = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
            if (!deduped.containsKey(sbn.packageName)) {
                deduped[sbn.packageName] = AodNotificationBadge(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = label,
                    initial = initial,
                    title = title.ifEmpty { label },
                )
            }
            if (deduped.size >= 6) break
        }
        _notifications.value = deduped.values.toList()
    }

    private fun resolveAppLabel(pkg: String): String {
        return runCatching {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString().ifBlank { pkg }
        }.getOrElse {
            pkg.substringAfterLast('.').replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
            }
        }
    }

    companion object {
        @Volatile
        private var currentControllerRef: MediaController? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _mediaSnapshot = MutableStateFlow<AodMediaSnapshot?>(null)
        val mediaSnapshot: StateFlow<AodMediaSnapshot?> = _mediaSnapshot.asStateFlow()

        private val _notifications = MutableStateFlow<List<AodNotificationBadge>>(emptyList())
        val notifications: StateFlow<List<AodNotificationBadge>> = _notifications.asStateFlow()

        fun isNotificationAccessEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            val target = ComponentName(context, AodNotificationBridgeService::class.java).flattenToString()
            return flat.split(":").any { entry ->
                entry.equals(target, ignoreCase = true) || entry.contains(context.packageName)
            }
        }

        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }

        fun togglePlayPause(context: Context) {
            val ctrl = currentControllerRef
            if (ctrl != null) {
                val isPlaying = ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
                runCatching {
                    if (isPlaying) {
                        ctrl.transportControls.pause()
                    } else {
                        ctrl.transportControls.play()
                    }
                    return
                }
            }
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }

        fun skipNext(context: Context) {
            val ctrl = currentControllerRef
            if (ctrl != null) {
                runCatching {
                    ctrl.transportControls.skipToNext()
                    return
                }
            }
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        }

        fun skipPrevious(context: Context) {
            val ctrl = currentControllerRef
            if (ctrl != null) {
                runCatching {
                    ctrl.transportControls.skipToPrevious()
                    return
                }
            }
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }

        private fun dispatchMediaKey(context: Context, keyCode: Int) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val now = SystemClock.uptimeMillis()
            runCatching {
                audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
                audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
            }
        }
    }
}
