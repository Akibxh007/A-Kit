package com.example.feature.drop

import android.content.Context
import android.net.Network
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
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
 * Supports optional [Network] binding so HTTP traffic routes directly through a local-only
 * hotspot even when the network has zero internet access (preventing ERR_INTERNET_DISCONNECTED).
 */
object DropPeerClient {

    private const val CONNECT_TIMEOUT_MS = 4_500
    private const val READ_TIMEOUT_MS = 12_000

    private fun openConnection(url: URL, boundNetwork: Network?): HttpURLConnection {
        val raw = if (boundNetwork != null) {
            boundNetwork.openConnection(url)
        } else {
            url.openConnection()
        }
        return raw as HttpURLConnection
    }

    fun fetchState(
        baseUrl: String,
        boundNetwork: Network? = null,
    ): Result<PeerStatePayload> = runCatching {
        val cleanBase = baseUrl.trimEnd('/')
        val conn = openConnection(URL("$cleanBase/api/state"), boundNetwork).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
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
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setFixedLengthStreamingMode(payload.size)
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

    fun uploadUri(
        context: Context,
        baseUrl: String,
        uri: Uri,
        boundNetwork: Network? = null,
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
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"

        // If ContentResolver did not provide exact byte size, buffer safely for Content-Length
        val bytesFallback: ByteArray? = if (sizeBytes <= 0L) {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } else {
            null
        }
        val contentLength = bytesFallback?.size?.toLong() ?: sizeBytes
        require(contentLength > 0L) { "Unable to read selected file" }

        val cleanBase = baseUrl.trimEnd('/')
        val conn = openConnection(URL("$cleanBase/api/upload"), boundNetwork).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = 60_000
            doOutput = true
            setFixedLengthStreamingMode(contentLength)
            setRequestProperty("X-File-Name", URLEncoder.encode(displayName, "UTF-8"))
            setRequestProperty("X-Mime-Type", mimeType)
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        try {
            conn.outputStream.use { out ->
                if (bytesFallback != null) {
                    out.write(bytesFallback)
                } else {
                    resolver.openInputStream(uri)?.use { input ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                        }
                    } ?: error("Cannot open file stream")
                }
                out.flush()
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                error("Upload failed (HTTP $code)")
            }
            displayName
        } finally {
            conn.disconnect()
        }
    }

    fun downloadFileToDownloads(
        context: Context,
        baseUrl: String,
        item: PeerFileItem,
        boundNetwork: Network? = null,
    ): Result<DropFileItem> = runCatching {
        val cleanBase = baseUrl.trimEnd('/')
        val encodedId = URLEncoder.encode(item.id, "UTF-8")
        val conn = openConnection(URL("$cleanBase/api/files/$encodedId"), boundNetwork).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = 60_000
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("Download failed (HTTP $code)")
            }
            val length = conn.contentLengthLong
            val stream = BufferedInputStream(conn.inputStream, 16 * 1024)
            val expectedBytes = if (length > 0L) length else Long.MAX_VALUE
            val saved = DropHubCoordinator.saveUploadedStreamToDownloads(
                context = context,
                rawFileName = item.fileName,
                mimeType = item.mimeType,
                inputStream = stream,
                expectedBytes = expectedBytes,
            ) ?: error("Could not save file to Downloads/A-Kit")
            saved
        } finally {
            conn.disconnect()
        }
    }
}
