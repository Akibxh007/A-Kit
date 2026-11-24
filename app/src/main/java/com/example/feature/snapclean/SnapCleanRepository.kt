package com.example.feature.snapclean

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import java.io.File
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.snapCleanDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "akit_snapclean_local_store",
)

class SnapCleanRepository(private val context: Context) {

    private object Keys {
        val AUTO_WATCH_ENABLED = booleanPreferencesKey("auto_watch_enabled")
        val DEFAULT_RETENTION_HOURS = intPreferencesKey("default_retention_hours")
        val TOTAL_DELETED_COUNT = intPreferencesKey("total_deleted_count")
        val TOTAL_RECLAIMED_BYTES = longPreferencesKey("total_reclaimed_bytes")
        val LAST_SWEEP_AT_MS = longPreferencesKey("last_sweep_at_ms")
        val TRACKED_ITEMS_RAW = stringPreferencesKey("tracked_items_raw")
    }

    val settingsFlow: Flow<SnapCleanSettings> = context.snapCleanDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p ->
            SnapCleanSettings(
                autoWatchEnabled = p[Keys.AUTO_WATCH_ENABLED] ?: false,
                defaultRetentionHours = p[Keys.DEFAULT_RETENTION_HOURS]
                    ?: RetentionPolicyOption.THREE_DAYS.hours,
                totalDeletedCount = p[Keys.TOTAL_DELETED_COUNT] ?: 0,
                totalReclaimedBytes = p[Keys.TOTAL_RECLAIMED_BYTES] ?: 0L,
                lastSweepAtMs = p[Keys.LAST_SWEEP_AT_MS] ?: 0L,
            )
        }

    val trackedScreenshotsFlow: Flow<List<TrackedScreenshot>> = context.snapCleanDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p -> decodeItems(p[Keys.TRACKED_ITEMS_RAW].orEmpty()) }

    suspend fun getSettingsOnce(): SnapCleanSettings = settingsFlow.first()

    suspend fun getTrackedScreenshotsOnce(): List<TrackedScreenshot> = trackedScreenshotsFlow.first()

    suspend fun setAutoWatchEnabled(enabled: Boolean) {
        context.snapCleanDataStore.edit { p ->
            p[Keys.AUTO_WATCH_ENABLED] = enabled
        }
    }

    suspend fun setDefaultRetentionHours(hours: Int) {
        context.snapCleanDataStore.edit { p ->
            p[Keys.DEFAULT_RETENTION_HOURS] = hours.coerceAtLeast(1)
        }
    }

    suspend fun registerScreenshotIfNew(item: TrackedScreenshot): Boolean {
        var added = false
        context.snapCleanDataStore.edit { p ->
            val current = decodeItems(p[Keys.TRACKED_ITEMS_RAW].orEmpty())
            val exists = current.any { it.id == item.id || it.uriString == item.uriString }
            if (!exists) {
                added = true
                val updated = (listOf(item) + current).take(200)
                p[Keys.TRACKED_ITEMS_RAW] = encodeItems(updated)
            }
        }
        return added
    }

    suspend fun upsertScreenshot(item: TrackedScreenshot) {
        context.snapCleanDataStore.edit { p ->
            val current = decodeItems(p[Keys.TRACKED_ITEMS_RAW].orEmpty())
            val filtered = current.filterNot { it.id == item.id || it.uriString == item.uriString }
            val updated = (listOf(item) + filtered).take(200)
            p[Keys.TRACKED_ITEMS_RAW] = encodeItems(updated)
        }
    }

    suspend fun scheduleForDuration(id: String, hoursFromNow: Int) {
        val now = System.currentTimeMillis()
        val expiresAt = now + hoursFromNow.coerceAtLeast(1) * 3_600_000L
        updateItem(id) { existing ->
            existing.copy(
                status = ScreenshotStatus.PENDING_DELETE,
                expiresAtMs = expiresAt,
            )
        }
    }

    suspend fun markKeptForever(id: String) {
        updateItem(id) { existing ->
            existing.copy(
                status = ScreenshotStatus.KEPT_FOREVER,
                expiresAtMs = 0L,
            )
        }
    }

    suspend fun unpinToDefaultQueue(id: String) {
        val retentionHours = getSettingsOnce().defaultRetentionHours
        scheduleForDuration(id, retentionHours)
    }

    suspend fun markNeedsUserConfirmation(id: String) {
        updateItem(id) { existing ->
            existing.copy(status = ScreenshotStatus.NEEDS_USER_CONFIRMATION)
        }
    }

    suspend fun removeItemsAfterDelete(ids: Collection<String>, reclaimedBytes: Long) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        context.snapCleanDataStore.edit { p ->
            val current = decodeItems(p[Keys.TRACKED_ITEMS_RAW].orEmpty())
            val remaining = current.filterNot { it.id in idSet }
            p[Keys.TRACKED_ITEMS_RAW] = encodeItems(remaining)
            p[Keys.TOTAL_DELETED_COUNT] = (p[Keys.TOTAL_DELETED_COUNT] ?: 0) + idSet.size
            p[Keys.TOTAL_RECLAIMED_BYTES] = (p[Keys.TOTAL_RECLAIMED_BYTES] ?: 0L) + reclaimedBytes.coerceAtLeast(0L)
        }
    }

    suspend fun recordSweepTimestamp(nowMs: Long = System.currentTimeMillis()) {
        context.snapCleanDataStore.edit { p ->
            p[Keys.LAST_SWEEP_AT_MS] = nowMs
        }
    }

    suspend fun findById(id: String): TrackedScreenshot? =
        getTrackedScreenshotsOnce().firstOrNull { it.id == id }

    private suspend fun updateItem(
        id: String,
        transform: (TrackedScreenshot) -> TrackedScreenshot,
    ) {
        context.snapCleanDataStore.edit { p ->
            val current = decodeItems(p[Keys.TRACKED_ITEMS_RAW].orEmpty())
            val updated = current.map { item -> if (item.id == id) transform(item) else item }
            p[Keys.TRACKED_ITEMS_RAW] = encodeItems(updated)
        }
    }

    private fun encodeItems(items: List<TrackedScreenshot>): String =
        items.joinToString("\n") { item ->
            listOf(
                b64(item.id),
                b64(item.uriString),
                b64(item.displayName),
                b64(item.relativePath),
                item.sizeBytes.toString(),
                item.capturedAtMs.toString(),
                item.expiresAtMs.toString(),
                item.status.key,
                item.isTestEntry.toString(),
                b64(item.filePath),
            ).joinToString("|")
        }

    private fun decodeItems(raw: String): List<TrackedScreenshot> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 9) return@mapNotNull null
            runCatching {
                TrackedScreenshot(
                    id = unb64(parts[0]),
                    uriString = unb64(parts[1]),
                    displayName = unb64(parts[2]),
                    relativePath = unb64(parts[3]),
                    sizeBytes = parts[4].toLong(),
                    capturedAtMs = parts[5].toLong(),
                    expiresAtMs = parts[6].toLong(),
                    status = ScreenshotStatus.fromKey(parts[7]),
                    isTestEntry = parts[8].toBoolean(),
                    filePath = parts.getOrNull(9)?.let(::unb64).orEmpty(),
                )
            }.getOrNull()
        }.toList()
    }

    private fun b64(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun unb64(encoded: String): String =
        String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
}

/**
 * Shared coordinator bridging SnapCleanWatcherService, SnapCleanActionReceiver,
 * SnapCleanCustomTimeActivity, SnapCleanSweepWorker, and SnapCleanViewModel.
 */
object SnapCleanCoordinator {

    private val _isWatcherRunning = MutableStateFlow(false)
    val isWatcherRunning: StateFlow<Boolean> = _isWatcherRunning.asStateFlow()

    @Volatile
    private var repoInstance: SnapCleanRepository? = null

    fun repository(context: Context): SnapCleanRepository {
        return repoInstance ?: synchronized(this) {
            repoInstance ?: SnapCleanRepository(context.applicationContext).also { repoInstance = it }
        }
    }

    fun setWatcherRunning(running: Boolean) {
        _isWatcherRunning.value = running
    }

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /**
     * True zero-prompt screenshot deletion:
     * - When MANAGE_EXTERNAL_STORAGE (All Files Access) is granted (or API < 30), resolves the
     *   real filesystem path and deletes silently via File.delete() without ANY OS dialog, then
     *   immediately invokes MediaScannerConnection.scanFile() so Gallery & MediaStore update.
     * - Gracefully cleans up already-deleted files without crashing.
     * - Falls back to ContentResolver / MediaStore consent ONLY if All Files Access is not granted.
     */
    suspend fun attemptDeleteScreenshot(
        context: Context,
        item: TrackedScreenshot,
    ): DeleteOutcome {
        val repo = repository(context)
        if (item.isTestEntry) {
            repo.removeItemsAfterDelete(listOf(item.id), item.sizeBytes)
            SnapCleanNotifications.cancelScreenshotNotification(context, item.id)
            return DeleteOutcome.Deleted
        }

        val uri = runCatching { Uri.parse(item.uriString) }.getOrNull()
        val resolvedFile = resolveScreenshotFile(context, item, uri)

        // 1. Zero-Prompt Silent Direct File Deletion path (when All Files Access is granted)
        if (hasAllFilesAccess()) {
            if (resolvedFile != null) {
                if (!resolvedFile.exists()) {
                    // File was already deleted externally — prune queue & refresh MediaStore cleanly
                    refreshMediaStoreAfterDelete(context, resolvedFile.absolutePath, uri)
                    repo.removeItemsAfterDelete(listOf(item.id), item.sizeBytes)
                    SnapCleanNotifications.cancelScreenshotNotification(context, item.id)
                    return DeleteOutcome.Deleted
                }

                val deletedSilently = runCatching { resolvedFile.delete() }.getOrDefault(false)
                if (deletedSilently || !resolvedFile.exists()) {
                    refreshMediaStoreAfterDelete(context, resolvedFile.absolutePath, uri)
                    repo.removeItemsAfterDelete(listOf(item.id), item.sizeBytes)
                    SnapCleanNotifications.cancelScreenshotNotification(context, item.id)
                    return DeleteOutcome.Deleted
                }
            }
        }

        // 2. Fallback when All Files Access is not granted or file path couldn't be resolved directly
        if (uri == null) {
            repo.removeItemsAfterDelete(listOf(item.id), 0L)
            return DeleteOutcome.Deleted
        }

        return try {
            val deletedRows = context.contentResolver.delete(uri, null, null)
            if (deletedRows > 0) {
                resolvedFile?.absolutePath?.let { path ->
                    MediaScannerConnection.scanFile(context.applicationContext, arrayOf(path), null, null)
                }
                repo.removeItemsAfterDelete(listOf(item.id), item.sizeBytes)
                SnapCleanNotifications.cancelScreenshotNotification(context, item.id)
                DeleteOutcome.Deleted
            } else {
                if (!mediaUriExists(context, uri) && (resolvedFile == null || !resolvedFile.exists())) {
                    repo.removeItemsAfterDelete(listOf(item.id), item.sizeBytes)
                    SnapCleanNotifications.cancelScreenshotNotification(context, item.id)
                    DeleteOutcome.Deleted
                } else {
                    repo.markNeedsUserConfirmation(item.id)
                    DeleteOutcome.RequiresUserConsent(uri, item.id)
                }
            }
        } catch (_: SecurityException) {
            repo.markNeedsUserConfirmation(item.id)
            DeleteOutcome.RequiresUserConsent(uri, item.id)
        } catch (t: Throwable) {
            DeleteOutcome.Failed(t.message ?: "Unable to delete file")
        }
    }

    private fun refreshMediaStoreAfterDelete(context: Context, absolutePath: String, uri: Uri?) {
        runCatching {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(absolutePath),
                null,
                null,
            )
        }
        if (uri != null) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    private fun resolveScreenshotFile(
        context: Context,
        item: TrackedScreenshot,
        uri: Uri?,
    ): File? {
        // Priority 1: Stored absolute path
        if (item.filePath.isNotBlank()) {
            val direct = File(item.filePath)
            if (direct.exists()) return direct
        }

        // Priority 2: Query MediaStore DATA column for exact path on device storage
        if (uri != null) {
            val queriedPath = queryDataPathFromUri(context, uri)
            if (!queriedPath.isNullOrBlank()) {
                val fromMediaStore = File(queriedPath)
                if (fromMediaStore.exists()) return fromMediaStore
            }
        }

        // Priority 3: Reconstruct path from relativePath + displayName under primary external storage
        val extRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        if (extRoot != null && item.displayName.isNotBlank()) {
            val relClean = item.relativePath.trim().trimStart('/').trimEnd('/')
            if (relClean.isNotBlank()) {
                val candidate = File(File(extRoot, relClean), item.displayName)
                if (candidate.exists()) return candidate
            }
            val picsCandidate = File(File(extRoot, "Pictures/Screenshots"), item.displayName)
            if (picsCandidate.exists()) return picsCandidate
            val dcimCandidate = File(File(extRoot, "DCIM/Screenshots"), item.displayName)
            if (dcimCandidate.exists()) return dcimCandidate
        }

        // Return stored file reference even if already removed so caller sees !exists() cleanly
        return item.filePath.takeIf { it.isNotBlank() }?.let { File(it) }
    }

    @Suppress("DEPRECATION")
    private fun queryDataPathFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DATA),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    if (col >= 0) cursor.getString(col) else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    /**
     * Creates a realistic test screenshot entry and triggers the real 3-button interactive
     * notification so the full SnapClean flow can be verified immediately inside the emulator.
     */
    suspend fun triggerTestDetection(context: Context): TrackedScreenshot {
        val repo = repository(context)
        val settings = repo.getSettingsOnce()
        val now = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        val id = "test_$now"
        val item = TrackedScreenshot(
            id = id,
            uriString = "akit://test-screenshot/$id",
            displayName = "Screenshot_${stamp}_A-Kit.png",
            relativePath = "Pictures/Screenshots/",
            sizeBytes = 640L * 1024L,
            capturedAtMs = now,
            expiresAtMs = now + settings.defaultRetentionHours * 3_600_000L,
            status = ScreenshotStatus.PENDING_DELETE,
            isTestEntry = true,
        )
        repo.upsertScreenshot(item)
        SnapCleanNotifications.showScreenshotCapturedNotification(
            context = context,
            screenshot = item,
            defaultRetentionHours = settings.defaultRetentionHours,
        )
        SnapCleanSweepWorker.ensureScheduled(context)
        return item
    }

    /**
     * Queries MediaStore strictly for recent images inside a path containing "Screenshots"
     * so regular camera photos are never touched.
     */
    fun queryRecentDeviceScreenshots(
        context: Context,
        limit: Int = 8,
        minDateAddedSec: Long = 0L,
    ): List<MediaScreenshotCandidate> {
        val results = mutableListOf<MediaScreenshotCandidate>()
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            @Suppress("DEPRECATION")
            add(MediaStore.Images.Media.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
                add(MediaStore.Images.Media.IS_PENDING)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    -1
                }
                val pendingCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
                } else {
                    -1
                }
                @Suppress("DEPRECATION")
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                var inspected = 0
                while (cursor.moveToNext() && results.size < limit && inspected < 40) {
                    inspected++
                    if (pendingCol >= 0 && cursor.getInt(pendingCol) == 1) continue

                    val dateAddedSec = cursor.getLong(dateCol)
                    if (minDateAddedSec > 0L && dateAddedSec < minDateAddedSec) break

                    val name = cursor.getString(nameCol).orEmpty()
                    val bucket = if (bucketCol >= 0) cursor.getString(bucketCol).orEmpty() else ""
                    val absDataPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty() else ""
                    val checkPath = relPath.ifBlank { absDataPath }

                    val isScreenshotPath = checkPath.contains("screenshots", ignoreCase = true) ||
                        bucket.contains("screenshots", ignoreCase = true)
                    if (!isScreenshotPath) continue

                    val mediaId = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediaId,
                    )
                    val safeName = name.ifBlank { "Screenshot_$mediaId.png" }
                    val safeRelPath = relPath.ifBlank { "Pictures/Screenshots/" }
                    val finalFilePath = absDataPath.ifBlank {
                        val root = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
                        if (root != null) {
                            File(File(root, safeRelPath.trimStart('/').trimEnd('/')), safeName).absolutePath
                        } else {
                            ""
                        }
                    }

                    results.add(
                        MediaScreenshotCandidate(
                            mediaId = "ms_$mediaId",
                            uri = uri,
                            displayName = safeName,
                            relativePath = safeRelPath,
                            sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L),
                            capturedAtMs = if (dateAddedSec > 0L) dateAddedSec * 1000L else System.currentTimeMillis(),
                            filePath = finalFilePath,
                        ),
                    )
                }
            }
        }
        return results
    }

    private fun mediaUriExists(context: Context, uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                null,
            )?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }
}

data class MediaScreenshotCandidate(
    val mediaId: String,
    val uri: Uri,
    val displayName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val capturedAtMs: Long,
    val filePath: String = "",
)
