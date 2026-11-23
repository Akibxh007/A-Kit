package com.example.feature.drop

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared thread-safe session hub synchronizing the embedded HTTP ServerSocket service
 * with the native Android Drop UI in real time.
 *
 * All file transfers stream directly disk-to-socket / socket-to-disk in 64KB chunks
 * (`STREAM_BUFFER_SIZE = 65_536`) with zero in-memory file buffering, supporting 300MB–2GB+ files
 * while emitting live transfer progress updates every ~150ms.
 */
object DropHubCoordinator {

    const val STREAM_BUFFER_SIZE = 65_536
    const val PROGRESS_EMIT_INTERVAL_MS = 150L

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _boundPort = MutableStateFlow(8080)
    val boundPort: StateFlow<Int> = _boundPort.asStateFlow()

    private val _snippets = MutableStateFlow<List<DropSnippet>>(emptyList())
    val snippets: StateFlow<List<DropSnippet>> = _snippets.asStateFlow()

    private val _sharedFiles = MutableStateFlow<List<DropFileItem>>(emptyList())
    val sharedFiles: StateFlow<List<DropFileItem>> = _sharedFiles.asStateFlow()

    private val _activePeerCount = MutableStateFlow(0)
    val activePeerCount: StateFlow<Int> = _activePeerCount.asStateFlow()

    private val _activeHostTransfer = MutableStateFlow<DropTransferProgress?>(null)
    val activeHostTransfer: StateFlow<DropTransferProgress?> = _activeHostTransfer.asStateFlow()

    private val _idleShutdownMessage = MutableStateFlow<String?>(null)
    val idleShutdownMessage: StateFlow<String?> = _idleShutdownMessage.asStateFlow()

    @Volatile
    var lastActivityMs: Long = System.currentTimeMillis()
        private set

    private val recentPeers = ConcurrentHashMap<String, Long>()

    fun touchActivity() {
        lastActivityMs = System.currentTimeMillis()
    }

    fun updateHostTransfer(progress: DropTransferProgress?) {
        _activeHostTransfer.value = progress
    }

    fun clearHostTransfer() {
        _activeHostTransfer.value = null
    }

    fun notifyIdleShutdown(message: String) {
        _idleShutdownMessage.value = message
    }

    fun clearIdleShutdownMessage() {
        _idleShutdownMessage.value = null
    }

    fun setServerStatus(running: Boolean, port: Int = 8080) {
        _isServerRunning.value = running
        _boundPort.value = port
        if (running) {
            touchActivity()
            _idleShutdownMessage.value = null
        } else {
            recentPeers.clear()
            _activePeerCount.value = 0
            _activeHostTransfer.value = null
        }
    }

    fun recordClientActivity(remoteIp: String?) {
        val now = System.currentTimeMillis()
        lastActivityMs = now
        if (!remoteIp.isNullOrBlank()) {
            recentPeers[remoteIp] = now
        }
        val cutoff = now - 120_000L
        recentPeers.entries.removeIf { it.value < cutoff }
        _activePeerCount.value = recentPeers.size
    }

    fun addSnippetFromPhone(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        touchActivity()
        val item = DropSnippet(
            id = "snip_${UUID.randomUUID().toString().take(8)}",
            text = trimmed,
            senderLabel = "This Phone",
            direction = DropDirection.FROM_PHONE,
            timestampMs = System.currentTimeMillis(),
        )
        _snippets.update { (listOf(item) + it).take(50) }
    }

    fun addSnippetFromBrowser(text: String, clientIp: String?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        touchActivity()
        val label = if (!clientIp.isNullOrBlank()) "Peer ($clientIp)" else "Peer Device"
        val item = DropSnippet(
            id = "snip_${UUID.randomUUID().toString().take(8)}",
            text = trimmed,
            senderLabel = label,
            direction = DropDirection.FROM_BROWSER,
            timestampMs = System.currentTimeMillis(),
        )
        _snippets.update { (listOf(item) + it).take(50) }
    }

    fun shareFileFromPhone(context: Context, uri: Uri): DropFileItem? {
        touchActivity()
        return runCatching {
            val resolver = context.contentResolver
            var displayName = "Shared_${System.currentTimeMillis()}"
            var sizeBytes = 0L

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
            if (sizeBytes <= 0L) {
                sizeBytes = runCatching {
                    resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                }.getOrDefault(0L).coerceAtLeast(0L)
            }

            val cleanName = sanitizeFileName(displayName)
            val mimeType = resolveProperMimeType(cleanName, resolver.getType(uri))
            val item = DropFileItem(
                id = "file_${UUID.randomUUID().toString().take(8)}",
                fileName = cleanName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                uriString = uri.toString(),
                savedLocationLabel = "Shared from Phone",
                direction = DropDirection.FROM_PHONE,
                timestampMs = System.currentTimeMillis(),
            )
            _sharedFiles.update { (listOf(item) + it).take(60) }
            item
        }.getOrNull()
    }

    /**
     * Streams an incoming Host upload (`POST /api/upload`) in 64KB chunks directly to a
     * FileProvider-backed file inside `externalFilesDir(Download)/A-Kit` so the Host UI can:
     * 1. Emit live electric-cyan transfer progress every ~150ms.
     * 2. Open PDFs, APKs, images, and videos immediately via `FileProvider.getUriForFile(...)`.
     * 3. Export/confirm to public `Downloads/A-Kit` via MediaStore when the user taps **Save**.
     */
    fun saveIncomingHostUpload(
        context: Context,
        rawFileName: String,
        mimeType: String,
        inputStream: InputStream,
        expectedBytes: Long,
    ): DropFileItem? {
        val cleanName = sanitizeFileName(rawFileName)
        val safeMime = resolveProperMimeType(cleanName, mimeType)
        val totalKnown = if (expectedBytes in 1L until Long.MAX_VALUE) expectedBytes else -1L

        updateHostTransfer(
            DropTransferProgress(
                fileName = cleanName,
                isSending = false,
                bytesTransferred = 0L,
                totalBytes = totalKnown,
                speedBytesPerSec = 0f,
            ),
        )

        return try {
            saveViaAppExternalDownloads(
                context = context,
                fileName = cleanName,
                mimeType = safeMime,
                inputStream = inputStream,
                expectedBytes = expectedBytes,
                registerInHostHub = true,
                locationLabel = "Received from Peer",
                markSavedToDownloads = false,
                onProgress = { transferred, total, speedBps ->
                    updateHostTransfer(
                        DropTransferProgress(
                            fileName = cleanName,
                            isSending = false,
                            bytesTransferred = transferred,
                            totalBytes = total,
                            speedBytesPerSec = speedBps,
                        ),
                    )
                },
            )
        } finally {
            clearHostTransfer()
        }
    }

    /**
     * Exports/confirms a Host-received peer file into public `Environment.DIRECTORY_DOWNLOADS/A-Kit`
     * via `MediaStore.Downloads` on Android 10+ (using 64KB chunked streaming) and marks the item
     * with `isSavedToDownloads = true` so the Host UI displays the `"Saved to Downloads/A-Kit"` badge.
     */
    fun exportHostFileToPublicDownloads(
        context: Context,
        fileId: String,
    ): Result<DropFileItem> = runCatching {
        val item = findFileById(fileId) ?: error("Received file not found")
        if (item.isSavedToDownloads) return@runCatching item

        val safeMime = resolveProperMimeType(item.fileName, item.mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, item.fileName)
                put(MediaStore.Downloads.MIME_TYPE, safeMime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/A-Kit")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val destUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create entry in Downloads/A-Kit")

            try {
                val sourceStream = item.localFilePath
                    ?.let { File(it) }
                    ?.takeIf { it.exists() && it.canRead() }
                    ?.let { FileInputStream(it) }
                    ?: openFileInputStream(context, item)
                    ?: error("Cannot read received file")

                sourceStream.use { input ->
                    resolver.openOutputStream(destUri)?.use { out ->
                        streamToDiskChunked(
                            input = input,
                            output = out,
                            expectedBytes = -1L,
                            onProgress = null,
                        )
                    } ?: error("Cannot open Downloads/A-Kit output stream")
                }

                val publishValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(destUri, publishValues, null, null)
            } catch (t: Throwable) {
                runCatching { resolver.delete(destUri, null, null) }
                throw t
            }
        }

        val updated = item.copy(
            isSavedToDownloads = true,
            savedLocationLabel = "Saved to Downloads/A-Kit",
        )
        _sharedFiles.update { list ->
            list.map { if (it.id == fileId) updated else it }
        }
        touchActivity()
        updated
    }

    /**
     * Streams an incoming HTTP download (fixed-length or chunked, up to multi-GB) directly into
     * `Downloads/A-Kit` using 64KB chunks with zero RAM buffering and ~150ms progress callbacks.
     *
     * Pass [expectedBytes] <= 0L (or [Long.MAX_VALUE]) to stream until EOF (e.g., chunked uploads).
     */
    fun saveUploadedStreamToDownloads(
        context: Context,
        rawFileName: String,
        mimeType: String,
        inputStream: InputStream,
        expectedBytes: Long,
        registerInHostHub: Boolean = true,
        onProgress: ((bytesTransferred: Long, totalBytes: Long, speedBytesPerSec: Float) -> Unit)? = null,
    ): DropFileItem? {
        val cleanName = sanitizeFileName(rawFileName)
        val safeMime = resolveProperMimeType(cleanName, mimeType)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStoreDownloads(
                context = context,
                fileName = cleanName,
                mimeType = safeMime,
                inputStream = inputStream,
                expectedBytes = expectedBytes,
                registerInHostHub = registerInHostHub,
                onProgress = onProgress,
            )
        } else {
            saveViaAppExternalDownloads(
                context = context,
                fileName = cleanName,
                mimeType = safeMime,
                inputStream = inputStream,
                expectedBytes = expectedBytes,
                registerInHostHub = registerInHostHub,
                locationLabel = "Saved to Downloads/A-Kit",
                markSavedToDownloads = true,
                onProgress = onProgress,
            )
        }
    }

    private fun saveViaMediaStoreDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        inputStream: InputStream,
        expectedBytes: Long,
        registerInHostHub: Boolean,
        onProgress: ((bytesTransferred: Long, totalBytes: Long, speedBytesPerSec: Float) -> Unit)?,
    ): DropFileItem? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        // IS_PENDING = 1 stages the transfer in a temporary entry inside Downloads/A-Kit until complete
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/A-Kit")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        var written = 0L
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                written = streamToDiskChunked(inputStream, out, expectedBytes, onProgress)
            } ?: error("Cannot open MediaStore output stream")

            if (written <= 0L) {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }

            val publishValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, publishValues, null, null)
            buildAndMaybeRegisterFile(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = written,
                uri = uri,
                locationLabel = "Saved to Downloads/A-Kit",
                localFilePath = null,
                isSavedToDownloads = true,
                registerInHostHub = registerInHostHub,
            )
        } catch (_: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun saveViaAppExternalDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        inputStream: InputStream,
        expectedBytes: Long,
        registerInHostHub: Boolean,
        locationLabel: String,
        markSavedToDownloads: Boolean,
        onProgress: ((bytesTransferred: Long, totalBytes: Long, speedBytesPerSec: Float) -> Unit)?,
    ): DropFileItem? {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val targetDir = File(baseDir, "A-Kit").apply { mkdirs() }
        // Stream directly to a temporary .tmp file inside Downloads/A-Kit, then atomically rename
        val tempFile = File(targetDir, ".tmp_${UUID.randomUUID().toString().take(8)}_$fileName")
        var written = 0L
        return try {
            FileOutputStream(tempFile).use { out ->
                written = streamToDiskChunked(inputStream, out, expectedBytes, onProgress)
            }
            if (written <= 0L) {
                tempFile.delete()
                return null
            }
            val finalFile = resolveNonCollidingFile(targetDir, fileName)
            val savedFile = if (tempFile.renameTo(finalFile)) finalFile else tempFile
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.drop.fileprovider",
                savedFile,
            )
            buildAndMaybeRegisterFile(
                fileName = savedFile.name,
                mimeType = mimeType,
                sizeBytes = written,
                uri = uri,
                locationLabel = locationLabel,
                localFilePath = savedFile.absolutePath,
                isSavedToDownloads = markSavedToDownloads,
                registerInHostHub = registerInHostHub,
            )
        } catch (_: Throwable) {
            tempFile.delete()
            null
        }
    }

    private fun buildAndMaybeRegisterFile(
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        uri: Uri,
        locationLabel: String,
        localFilePath: String?,
        isSavedToDownloads: Boolean,
        registerInHostHub: Boolean,
    ): DropFileItem {
        val item = DropFileItem(
            id = "file_${UUID.randomUUID().toString().take(8)}",
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            uriString = uri.toString(),
            savedLocationLabel = locationLabel,
            direction = DropDirection.FROM_BROWSER,
            timestampMs = System.currentTimeMillis(),
            localFilePath = localFilePath,
            isSavedToDownloads = isSavedToDownloads,
        )
        if (registerInHostHub) {
            _sharedFiles.update { (listOf(item) + it).take(60) }
        }
        return item
    }

    fun findFileById(id: String): DropFileItem? =
        _sharedFiles.value.firstOrNull { it.id == id }

    fun openFileInputStream(context: Context, item: DropFileItem): InputStream? {
        item.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return runCatching { FileInputStream(file) }.getOrNull()
            }
        }
        return runCatching {
            val uri = Uri.parse(item.uriString)
            context.contentResolver.openInputStream(uri)
        }.getOrNull()
    }

    fun resolveFileSizeBytes(context: Context, item: DropFileItem): Long {
        if (item.sizeBytes > 0L) return item.sizeBytes
        item.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) return file.length()
        }
        return runCatching {
            val uri = Uri.parse(item.uriString)
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)
    }

    fun resolveProperMimeType(fileName: String, rawMime: String?): String {
        val trimmed = rawMime?.trim().orEmpty()
        if (trimmed.isNotBlank() && !trimmed.equals("application/octet-stream", ignoreCase = true)) {
            return trimmed
        }
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "zip" -> "application/zip"
            "txt", "log", "md" -> "text/plain"
            "json" -> "application/json"
            else -> {
                if (ext.isNotBlank()) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                        ?: "application/octet-stream"
                } else {
                    "application/octet-stream"
                }
            }
        }
    }

    fun serializePortalStateJson(): String {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val snippetsArray = JSONArray()
        _snippets.value.forEach { s ->
            snippetsArray.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("text", s.text)
                    put("sender", s.senderLabel)
                    put("time", timeFmt.format(Date(s.timestampMs)))
                },
            )
        }

        val filesArray = JSONArray()
        _sharedFiles.value.forEach { f ->
            filesArray.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("fileName", f.fileName)
                    put("mimeType", f.mimeType)
                    put("sizeLabel", f.formatSize())
                    put("isImage", f.isImage)
                    put(
                        "sourceLabel",
                        when {
                            f.direction == DropDirection.FROM_PHONE -> "From Phone"
                            f.isSavedToDownloads -> "Saved to Downloads/A-Kit"
                            else -> "Received from Peer"
                        },
                    )
                },
            )
        }

        return JSONObject().apply {
            put("snippets", snippetsArray)
            put("files", filesArray)
        }.toString()
    }

    /**
     * Streams socket/input data directly to disk in 64KB chunks (`STREAM_BUFFER_SIZE = 65536`),
     * periodically touching activity and emitting transfer progress every ~150ms.
     */
    private fun streamToDiskChunked(
        input: InputStream,
        output: OutputStream,
        expectedBytes: Long,
        onProgress: ((bytesTransferred: Long, totalBytes: Long, speedBytesPerSec: Float) -> Unit)?,
    ): Long {
        val bounded = expectedBytes in 1L until Long.MAX_VALUE
        val totalKnown = if (bounded) expectedBytes else -1L
        val buffer = ByteArray(STREAM_BUFFER_SIZE)

        var totalWritten = 0L
        var remaining = if (bounded) expectedBytes else Long.MAX_VALUE
        var bytesSinceTouch = 0L

        val startTimeMs = SystemClock.elapsedRealtime()
        var lastEmitTimeMs = startTimeMs

        onProgress?.invoke(0L, totalKnown, 0f)

        while (remaining > 0L) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break

            output.write(buffer, 0, read)
            totalWritten += read.toLong()
            if (bounded) remaining -= read.toLong()

            bytesSinceTouch += read.toLong()
            if (bytesSinceTouch >= ACTIVITY_TOUCH_INTERVAL_BYTES) {
                bytesSinceTouch = 0L
                touchActivity()
            }

            if (onProgress != null) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastEmitTimeMs >= PROGRESS_EMIT_INTERVAL_MS) {
                    val elapsedSec = ((now - startTimeMs).coerceAtLeast(1L)) / 1000f
                    val speedBps = totalWritten / elapsedSec
                    onProgress.invoke(totalWritten, totalKnown, speedBps)
                    lastEmitTimeMs = now
                }
            }
        }

        output.flush()
        touchActivity()

        if (onProgress != null && totalWritten > 0L) {
            val elapsedSec = ((SystemClock.elapsedRealtime() - startTimeMs).coerceAtLeast(1L)) / 1000f
            val finalTotal = if (totalKnown > 0L) totalKnown else totalWritten
            onProgress.invoke(totalWritten, finalTotal, totalWritten / elapsedSec)
        }
        return totalWritten
    }

    private fun resolveNonCollidingFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${stem}_$counter$ext")
            counter++
        }
        return candidate
    }

    private fun sanitizeFileName(raw: String): String {
        val base = raw
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace("..", "")
            .replace(Regex("[\\r\\n\\t\"<>|:*?]"), "_")
            .trim()
        return base.ifBlank { "Drop_${System.currentTimeMillis()}.bin" }
    }

    private const val ACTIVITY_TOUCH_INTERVAL_BYTES = 4L * 1024L * 1024L
}
