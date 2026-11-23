package com.example.feature.drop

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared thread-safe session hub synchronizing the embedded HTTP ServerSocket service
 * with the native Android Drop UI in real time.
 */
object DropHubCoordinator {

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

    private val _idleShutdownMessage = MutableStateFlow<String?>(null)
    val idleShutdownMessage: StateFlow<String?> = _idleShutdownMessage.asStateFlow()

    @Volatile
    var lastActivityMs: Long = System.currentTimeMillis()
        private set

    private val recentPeers = ConcurrentHashMap<String, Long>()

    fun touchActivity() {
        lastActivityMs = System.currentTimeMillis()
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

            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val item = DropFileItem(
                id = "file_${UUID.randomUUID().toString().take(8)}",
                fileName = sanitizeFileName(displayName),
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
     * Streams an incoming HTTP upload directly into Downloads/A-Kit via MediaStore.Downloads
     * on Android 10+ (zero storage permissions needed) or FileProvider Downloads folder on Android 8-9.
     */
    fun saveUploadedStreamToDownloads(
        context: Context,
        rawFileName: String,
        mimeType: String,
        inputStream: InputStream,
        expectedBytes: Long,
    ): DropFileItem? {
        val cleanName = sanitizeFileName(rawFileName)
        val safeMime = mimeType.ifBlank { "application/octet-stream" }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStoreDownloads(context, cleanName, safeMime, inputStream, expectedBytes)
        } else {
            saveViaAppExternalDownloads(context, cleanName, safeMime, inputStream, expectedBytes)
        }
    }

    private fun saveViaMediaStoreDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        inputStream: InputStream,
        expectedBytes: Long,
    ): DropFileItem? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
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
                written = copyExactBytes(inputStream, out, expectedBytes)
            }
            val publishValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, publishValues, null, null)
            registerReceivedFile(fileName, mimeType, written, uri, "Downloads/A-Kit")
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
    ): DropFileItem? {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val targetDir = File(baseDir, "A-Kit").apply { mkdirs() }
        val uniqueFile = resolveNonCollidingFile(targetDir, fileName)
        var written = 0L
        return try {
            FileOutputStream(uniqueFile).use { out ->
                written = copyExactBytes(inputStream, out, expectedBytes)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.drop.fileprovider",
                uniqueFile,
            )
            registerReceivedFile(uniqueFile.name, mimeType, written, uri, "Downloads/A-Kit")
        } catch (_: Throwable) {
            uniqueFile.delete()
            null
        }
    }

    private fun registerReceivedFile(
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        uri: Uri,
        locationLabel: String,
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
        )
        _sharedFiles.update { (listOf(item) + it).take(60) }
        return item
    }

    fun findFileById(id: String): DropFileItem? =
        _sharedFiles.value.firstOrNull { it.id == id }

    fun openFileInputStream(context: Context, item: DropFileItem): InputStream? {
        return runCatching {
            val uri = Uri.parse(item.uriString)
            context.contentResolver.openInputStream(uri)
        }.getOrNull()
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
                        if (f.direction == DropDirection.FROM_PHONE) "From Phone" else "Saved to Downloads/A-Kit",
                    )
                },
            )
        }

        return JSONObject().apply {
            put("snippets", snippetsArray)
            put("files", filesArray)
        }.toString()
    }

    private fun copyExactBytes(
        input: InputStream,
        output: java.io.OutputStream,
        expectedBytes: Long,
    ): Long {
        val buffer = ByteArray(16 * 1024)
        var remaining = expectedBytes
        var totalWritten = 0L
        while (remaining > 0L) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            output.write(buffer, 0, read)
            totalWritten += read
            remaining -= read
        }
        output.flush()
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
}
