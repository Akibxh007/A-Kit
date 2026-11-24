package com.example.feature.drop

import android.content.Context
import android.net.Network
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder

data class PeerStatePayload(
    val snippets: List<DropSnippet>,
    val files: List<PeerFileItem>,
)

/**
 * 100% offline LAN HTTP client allowing an A-Kit device to connect natively to another
 * A-Kit Drop server via QR scan, offline hotspot auto-connect, or `akit://drop` deep link.
 *
 * Uses direct 64KB disk-to-socket streaming, `setFixedLengthStreamingMode(fileSize)` /
 * `setChunkedStreamingMode(65536)`, `Proxy.NO_PROXY` over the bound `WifiNetworkSpecifier`
 * [Network], `connectTimeout = 15000`, and `readTimeout = 0` (infinite during active stream) so
 * 300MB–2GB+ transfers run at full Wi-Fi speed without OOM or socket timeouts.
 */
object DropPeerClient {

    private const val STREAM_BUFFER_SIZE = 65_536
    private const val PING_TIMEOUT_MS = 2_500
    private const val TRANSFER_CONNECT_TIMEOUT_MS = 15_000
    private const val STATE_READ_TIMEOUT_MS = 12_000

    private fun openConnection(url: URL, boundNetwork: Network?): HttpURLConnection {
        val raw = if (boundNetwork != null) {
            boundNetwork.openConnection(url, Proxy.NO_PROXY)
        } else {
            url.openConnection(Proxy.NO_PROXY)
        }
        return raw as HttpURLConnection
    }

    /**
     * Verifies live reachability against the sender's `GET /api/ping` endpoint over the
     * explicit Wi-Fi [Network] and returns the peer's device display name on 200 OK.
     */
    fun ping(
        baseUrl: String,
        boundNetwork: Network? = null,
    ): Result<String> = runCatching {
        val cleanBase = baseUrl.trimEnd('/')
        val conn = openConnection(URL("$cleanBase/api/ping"), boundNetwork).apply {
            requestMethod = "GET"
            connectTimeout = PING_TIMEOUT_MS
            readTimeout = PING_TIMEOUT_MS
            useCaches = false
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("Ping failed with HTTP $code")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            if (json.optString("status") != "ok") {
                error("Unexpected ping status")
            }
            json.optString("device").ifBlank { "A-Kit Peer" }
        } finally {
            conn.disconnect()
        }
    }

    fun fetchState(
        baseUrl: String,
        boundNetwork: Network? = null,
    ): Result<PeerStatePayload> = runCatching {
        val cleanBase = baseUrl.trimEnd('/')
        val conn = openConnection(URL("$cleanBase/api/state"), boundNetwork).apply {
            requestMethod = "GET"
            connectTimeout = TRANSFER_CONNECT_TIMEOUT_MS
            readTimeout = STATE_READ_TIMEOUT_MS
            useCaches = false
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("Peer responded with HTTP $code")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)

            val snippetsJson = json.optJSONArray("snippets")
            val snippets = buildList {
                if (snippetsJson != null) {
                    for (i in 0 until snippetsJson.length()) {
                        val obj = snippetsJson.optJSONObject(i) ?: continue
                        add(
                            DropSnippet(
                                id = obj.optString("id", "peer_snip_$i"),
                                text = obj.optString("text", ""),
                                senderLabel = obj.optString("sender", "Peer Host"),
                                direction = DropDirection.FROM_BROWSER,
                                timestampMs = System.currentTimeMillis() - i * 1_000L,
                            ),
                        )
                    }
                }
            }

            val filesJson = json.optJSONArray("files")
            val files = buildList {
                if (filesJson != null) {
                    for (i in 0 until filesJson.length()) {
                        val obj = filesJson.optJSONObject(i) ?: continue
                        add(
                            PeerFileItem(
                                id = obj.optString("id", "peer_file_$i"),
                                fileName = obj.optString("fileName", "File"),
                                mimeType = obj.optString("mimeType", "application/octet-stream"),
                                sizeLabel = obj.optString("sizeLabel", "—"),
                                sourceLabel = obj.optString("sourceLabel", "Peer Host"),
                                isImage = obj.optBoolean("isImage", false),
                            ),
                        )
                    }
                }
            }
            PeerStatePayload(snippets = snippets, files = files)
        } finally {
            conn.disconnect()
        }
    }

    fun sendSnippet(
        baseUrl: String,
        text: String,
        boundNetwork: Network? = null,
    ): Result<Unit> = runCatching {
        val cleanBase = baseUrl.trimEnd('/')
        val payload = text.trim().toByteArray(Charsets.UTF_8)
        val conn = openConnection(URL("$cleanBase/api/snippets"), boundNetwork).apply {
            requestMethod = "POST"
            connectTimeout = TRANSFER_CONNECT_TIMEOUT_MS
            readTimeout = STATE_READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setFixedLengthStreamingMode(payload.size.toLong())
            setRequestProperty("Content-Type", "text/plain; charset=utf-8")
        }
        try {
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            if (code !in 200..299) {
                error("Failed to send snippet (HTTP $code)")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Streams any file (300MB–2GB+) directly from ContentResolver disk stream to the peer's
     * HTTP socket in 64KB chunks with zero RAM buffering, infinite active stream timeout,
     * and ~150ms live progress updates.
     */
    fun uploadUri(
        context: Context,
        baseUrl: String,
        uri: Uri,
        boundNetwork: Network? = null,
        onProgress: (DropTransferProgress) -> Unit = {},
    ): Result<String> = runCatching {
        val resolver = context.contentResolver
        var displayName = "Drop_${System.currentTimeMillis()}"
        var sizeBytes = -1L
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
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            }.getOrDefault(-1L)
        }
        val mimeType = DropHubCoordinator.resolveProperMimeType(displayName, resolver.getType(uri))

        onProgress(
            DropTransferProgress(
                fileName = displayName,
                isSending = true,
                bytesTransferred = 0L,
                totalBytes = sizeBytes,
                speedBytesPerSec = 0f,
            ),
        )

        val cleanBase = baseUrl.trimEnd('/')
        val conn = openConnection(URL("$cleanBase/api/upload"), boundNetwork).apply {
            requestMethod = "POST"
            connectTimeout = TRANSFER_CONNECT_TIMEOUT_MS
            readTimeout = 0 // Infinite during active large-file stream
            doOutput = true
            useCaches = false
            if (sizeBytes > 0L) {
                setFixedLengthStreamingMode(sizeBytes)
            } else {
                setChunkedStreamingMode(STREAM_BUFFER_SIZE)
            }
            setRequestProperty("X-File-Name", URLEncoder.encode(displayName, "UTF-8"))
            setRequestProperty("X-Mime-Type", mimeType)
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        try {
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            var totalSent = 0L
            val startTimeMs = SystemClock.elapsedRealtime()
            var lastEmitTimeMs = startTimeMs

            conn.outputStream.use { out ->
                resolver.openInputStream(uri)?.use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        totalSent += read.toLong()

                        val now = SystemClock.elapsedRealtime()
                        if (now - lastEmitTimeMs >= DropHubCoordinator.PROGRESS_EMIT_INTERVAL_MS) {
                            val elapsedSec = ((now - startTimeMs).coerceAtLeast(1L)) / 1000f
                            onProgress(
                                DropTransferProgress(
                                    fileName = displayName,
                                    isSending = true,
                                    bytesTransferred = totalSent,
                                    totalBytes = sizeBytes,
                                    speedBytesPerSec = totalSent / elapsedSec,
                                ),
                            )
                            lastEmitTimeMs = now
                        }
                    }
                } ?: error("Cannot open file stream")
                out.flush()
            }

            val totalElapsedSec = ((SystemClock.elapsedRealtime() - startTimeMs).coerceAtLeast(1L)) / 1000f
            onProgress(
                DropTransferProgress(
                    fileName = displayName,
                    isSending = true,
                    bytesTransferred = totalSent,
                    totalBytes = if (sizeBytes > 0L) sizeBytes else totalSent,
                    speedBytesPerSec = totalSent / totalElapsedSec,
                ),
            )

            val code = conn.responseCode
            if (code !in 200..299) {
                error("Upload failed (HTTP $code)")
            }
            displayName
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Streams a peer file (300MB–2GB+) directly from socket to a temporary staged file in
     * `Downloads/A-Kit` using 64KB chunks with infinite read timeout and ~150ms progress updates.
     */
    fun downloadFileToDownloads(
        context: Context,
        baseUrl: String,
        item: PeerFileItem,
        boundNetwork: Network? = null,
        onProgress: (DropTransferProgress) -> Unit = {},
    ): Result<DropFileItem> = runCatching {
        val cleanBase = baseUrl.trimEnd('/')
        val encodedId = URLEncoder.encode(item.id, "UTF-8")
        val conn = openConnection(URL("$cleanBase/api/files/$encodedId"), boundNetwork).apply {
            requestMethod = "GET"
            connectTimeout = TRANSFER_CONNECT_TIMEOUT_MS
            readTimeout = 0 // Infinite during active large-file stream
            useCaches = false
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("Download failed (HTTP $code)")
            }
            val length = conn.contentLengthLong
            val stream = BufferedInputStream(conn.inputStream, STREAM_BUFFER_SIZE)
            val expectedBytes = if (length > 0L) length else -1L
            val saved = DropHubCoordinator.saveUploadedStreamToDownloads(
                context = context,
                rawFileName = item.fileName,
                mimeType = item.mimeType,
                inputStream = stream,
                expectedBytes = expectedBytes,
                registerInHostHub = false,
                onProgress = { transferred, total, speedBps ->
                    onProgress(
                        DropTransferProgress(
                            fileName = item.fileName,
                            isSending = false,
                            bytesTransferred = transferred,
                            totalBytes = total,
                            speedBytesPerSec = speedBps,
                        ),
                    )
                },
            ) ?: error("Could not save file to Downloads/A-Kit")
            saved
        } finally {
            conn.disconnect()
        }
    }
}
