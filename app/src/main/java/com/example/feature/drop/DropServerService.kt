package com.example.feature.drop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale

class DropServerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var boundPort: Int = DEFAULT_PORT

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForegroundSafely(buildNotification(null, DEFAULT_PORT, 0))
        startEmbeddedHttpServer()

        serviceScope.launch {
            DropHubCoordinator.activePeerCount.collect { peers ->
                if (DropHubCoordinator.isServerRunning.value) {
                    val ip = LocalNetworkUtils.detectLocalIpv4Address()
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(NOTIFICATION_ID, buildNotification(ip, boundPort, peers))
                }
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
        runCatching { serverSocket?.close() }
        serverSocket = null
        DropHubCoordinator.setServerStatus(running = false, port = DEFAULT_PORT)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startEmbeddedHttpServer() {
        serviceScope.launch {
            val socket = bindAvailablePort() ?: run {
                DropHubCoordinator.setServerStatus(running = false)
                stopSelf()
                return@launch
            }
            serverSocket = socket
            boundPort = socket.localPort
            DropHubCoordinator.setServerStatus(running = true, port = boundPort)

            val ip = LocalNetworkUtils.detectLocalIpv4Address()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, buildNotification(ip, boundPort, 0))

            // 15-minute idle auto-shutdown watchdog to protect battery life
            serviceScope.launch {
                while (isActive && !socket.isClosed) {
                    delay(IDLE_CHECK_INTERVAL_MS)
                    val idleElapsed = System.currentTimeMillis() - DropHubCoordinator.lastActivityMs
                    if (idleElapsed >= IDLE_TIMEOUT_MS) {
                        DropHubCoordinator.notifyIdleShutdown(
                            "Local Drop Server auto-stopped after 15 minutes of inactivity to save battery.",
                        )
                        runCatching { socket.close() }
                        stopSelf()
                        break
                    }
                }
            }

            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                serviceScope.launch {
                    handleClientConnection(client)
                }
            }
        }
    }

    private fun bindAvailablePort(): ServerSocket? {
        for (port in DEFAULT_PORT..(DEFAULT_PORT + 10)) {
            val candidate = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", port))
                }
            }.getOrNull()
            if (candidate != null) return candidate
        }
        return null
    }

    private fun handleClientConnection(socket: Socket) {
        socket.use { client ->
            runCatching {
                // Header handshake timeout; switched to infinite (0) during large file streaming
                client.soTimeout = HEADER_READ_TIMEOUT_MS
                client.tcpNoDelay = true
                client.receiveBufferSize = STREAM_BUFFER_SIZE
                client.sendBufferSize = STREAM_BUFFER_SIZE

                val remoteIp = client.inetAddress?.hostAddress
                DropHubCoordinator.recordClientActivity(remoteIp)

                val input = BufferedInputStream(client.getInputStream(), STREAM_BUFFER_SIZE)
                val output = BufferedOutputStream(client.getOutputStream(), STREAM_BUFFER_SIZE)

                val requestLine = readAsciiLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase(Locale.US)
                val rawPath = parts[1].substringBefore("?")

                val headers = HashMap<String, String>()
                while (true) {
                    val line = readAsciiLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val key = line.substring(0, colon).trim().lowercase(Locale.US)
                        val value = line.substring(colon + 1).trim()
                        headers[key] = value
                    }
                }

                // Honor HTTP/1.1 Expect: 100-continue immediately for large uploads
                if (headers["expect"]?.contains("100-continue", ignoreCase = true) == true) {
                    output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                    output.flush()
                }

                val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L
                val isChunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true

                when {
                    method == "OPTIONS" -> {
                        writeSimpleResponse(output, 204, "No Content", "text/plain", ByteArray(0))
                    }

                    method == "GET" && (rawPath == "/" || rawPath == "/index.html") -> {
                        val html = DropWebPortalHtml.render(resolveDeviceDisplayName())
                        writeSimpleResponse(
                            output,
                            200,
                            "OK",
                            "text/html; charset=utf-8",
                            html.toByteArray(Charsets.UTF_8),
                        )
                    }

                    method == "GET" && rawPath == "/api/ping" -> {
                        val safeDevice = resolveDeviceDisplayName()
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                        val json = """{"status":"ok","device":"$safeDevice"}"""
                        writeSimpleResponse(
                            output,
                            200,
                            "OK",
                            "application/json; charset=utf-8",
                            json.toByteArray(Charsets.UTF_8),
                        )
                    }

                    method == "GET" && rawPath == "/api/state" -> {
                        val json = DropHubCoordinator.serializePortalStateJson()
                        writeSimpleResponse(
                            output,
                            200,
                            "OK",
                            "application/json; charset=utf-8",
                            json.toByteArray(Charsets.UTF_8),
                        )
                    }

                    method == "POST" && rawPath == "/api/snippets" -> {
                        val capped = contentLength.coerceIn(0L, 64L * 1024L).toInt()
                        val bodyBytes = readFixedBytes(input, capped)
                        val text = String(bodyBytes, Charsets.UTF_8)
                        DropHubCoordinator.addSnippetFromBrowser(text, remoteIp)
                        writeSimpleResponse(
                            output,
                            200,
                            "OK",
                            "application/json; charset=utf-8",
                            """{"ok":true}""".toByteArray(Charsets.UTF_8),
                        )
                    }

                    method == "POST" && rawPath == "/api/upload" -> {
                        if (!isChunked && (contentLength <= 0L || contentLength > MAX_UPLOAD_BYTES)) {
                            writeSimpleResponse(
                                output,
                                413,
                                "Payload Too Large",
                                "application/json",
                                """{"ok":false,"error":"Invalid file size"}""".toByteArray(Charsets.UTF_8),
                            )
                            return
                        }

                        // Infinite read timeout while actively streaming 300MB–2GB+ uploads
                        client.soTimeout = 0

                        val encodedName = headers["x-file-name"] ?: "Upload_${System.currentTimeMillis()}.bin"
                        var fileName = runCatching {
                            URLDecoder.decode(encodedName, "UTF-8")
                        }.getOrDefault(encodedName)
                        var mimeType = headers["x-mime-type"] ?: "application/octet-stream"

                        val rawBodyStream: InputStream = if (isChunked) {
                            HttpChunkedInputStream(input)
                        } else {
                            input
                        }
                        val expectedBytes = if (isChunked) -1L else contentLength

                        // Support multipart/form-data streaming without RAM buffering as well as raw body streaming
                        val contentTypeHeader = headers["content-type"].orEmpty()
                        val boundary = extractMultipartBoundary(contentTypeHeader)

                        val saved = if (boundary != null) {
                            handleMultipartStreamingUpload(
                                bodyStream = rawBodyStream,
                                boundary = boundary,
                                fallbackFileName = fileName,
                                fallbackMimeType = mimeType,
                                expectedBytes = expectedBytes,
                            )
                        } else {
                            DropHubCoordinator.saveIncomingHostUpload(
                                context = applicationContext,
                                rawFileName = fileName,
                                mimeType = mimeType,
                                inputStream = rawBodyStream,
                                expectedBytes = expectedBytes,
                            )
                        }

                        if (saved != null) {
                            writeSimpleResponse(
                                output,
                                200,
                                "OK",
                                "application/json; charset=utf-8",
                                """{"ok":true,"id":"${saved.id}"}""".toByteArray(Charsets.UTF_8),
                            )
                        } else {
                            writeSimpleResponse(
                                output,
                                500,
                                "Internal Server Error",
                                "application/json",
                                """{"ok":false}""".toByteArray(Charsets.UTF_8),
                            )
                        }
                    }

                    method == "GET" && rawPath.startsWith("/api/files/") -> {
                        val fileId = runCatching {
                            URLDecoder.decode(rawPath.removePrefix("/api/files/"), "UTF-8")
                        }.getOrDefault("")
                        val fileItem = DropHubCoordinator.findFileById(fileId)
                        if (fileItem == null) {
                            writeSimpleResponse(
                                output,
                                404,
                                "Not Found",
                                "text/plain",
                                "File not found".toByteArray(Charsets.UTF_8),
                            )
                            return
                        }
                        // Infinite timeout during multi-GB disk-to-socket downloads
                        client.soTimeout = 0
                        streamFileResponse(output, fileItem)
                    }

                    else -> {
                        writeSimpleResponse(
                            output,
                            404,
                            "Not Found",
                            "text/plain",
                            "Not Found".toByteArray(Charsets.UTF_8),
                        )
                    }
                }
            }
        }
    }

    private fun handleMultipartStreamingUpload(
        bodyStream: InputStream,
        boundary: String,
        fallbackFileName: String,
        fallbackMimeType: String,
        expectedBytes: Long,
    ): DropFileItem? {
        val buffered = BufferedInputStream(bodyStream, STREAM_BUFFER_SIZE)
        val startBoundary = "--$boundary"
        // Scan to first part boundary
        var line = readAsciiLine(buffered)
        while (line != null && !line.startsWith(startBoundary)) {
            line = readAsciiLine(buffered)
        }
        if (line == null || line.startsWith("$startBoundary--")) return null

        var partFileName = fallbackFileName
        var partMimeType = fallbackMimeType
        while (true) {
            val hLine = readAsciiLine(buffered) ?: break
            if (hLine.isEmpty()) break
            val lower = hLine.lowercase(Locale.US)
            if (lower.startsWith("content-disposition:")) {
                val match = Regex("""filename="([^"]+)"""").find(hLine)
                match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                    partFileName = it
                }
            } else if (lower.startsWith("content-type:")) {
                hLine.substringAfter(':').trim().takeIf { it.isNotBlank() }?.let {
                    partMimeType = it
                }
            }
        }

        val boundaryMarker = "\r\n--$boundary".toByteArray(Charsets.ISO_8859_1)
        val partStream = MultipartBoundaryInputStream(buffered, boundaryMarker)
        return DropHubCoordinator.saveIncomingHostUpload(
            context = applicationContext,
            rawFileName = partFileName,
            mimeType = partMimeType,
            inputStream = partStream,
            expectedBytes = if (expectedBytes > 0L) -1L else expectedBytes,
        )
    }

    private fun extractMultipartBoundary(contentType: String): String? {
        if (!contentType.contains("multipart/form-data", ignoreCase = true)) return null
        val match = Regex("""boundary=([^;\s"]+|"[^"]+")""", RegexOption.IGNORE_CASE).find(contentType)
        return match?.groupValues?.getOrNull(1)?.trim('"')?.takeIf { it.isNotBlank() }
    }

    private fun streamFileResponse(output: OutputStream, item: DropFileItem) {
        val stream = DropHubCoordinator.openFileInputStream(applicationContext, item)
        if (stream == null) {
            writeSimpleResponse(
                output,
                404,
                "Not Found",
                "text/plain",
                "File unavailable".toByteArray(Charsets.UTF_8),
            )
            return
        }

        val safeDispositionName = item.fileName.replace("\"", "_")
        val resolvedSize = DropHubCoordinator.resolveFileSizeBytes(applicationContext, item)
        DropHubCoordinator.updateHostTransfer(
            DropTransferProgress(
                fileName = item.fileName,
                isSending = true,
                bytesTransferred = 0L,
                totalBytes = resolvedSize,
                speedBytesPerSec = 0f,
            ),
        )
        try {
            stream.use { input ->
                val headerBuilder = StringBuilder()
                    .append("HTTP/1.1 200 OK\r\n")
                    .append("Content-Type: ${item.mimeType}\r\n")
                    .append("Content-Disposition: inline; filename=\"$safeDispositionName\"\r\n")
                if (resolvedSize > 0L) {
                    headerBuilder.append("Content-Length: $resolvedSize\r\n")
                }
                headerBuilder
                    .append("Cache-Control: no-store\r\n")
                    .append("Connection: close\r\n\r\n")

                output.write(headerBuilder.toString().toByteArray(Charsets.ISO_8859_1))
                output.flush()

                // Direct 64KB disk-to-socket streaming with ~150ms live progress updates
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                var totalSent = 0L
                var sinceTouch = 0L
                val startTimeMs = SystemClock.elapsedRealtime()
                var lastEmitTimeMs = startTimeMs

                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    totalSent += read.toLong()
                    sinceTouch += read.toLong()

                    if (sinceTouch >= 4L * 1024L * 1024L) {
                        sinceTouch = 0L
                        DropHubCoordinator.touchActivity()
                    }

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastEmitTimeMs >= DropHubCoordinator.PROGRESS_EMIT_INTERVAL_MS) {
                        val elapsedSec = ((now - startTimeMs).coerceAtLeast(1L)) / 1000f
                        DropHubCoordinator.updateHostTransfer(
                            DropTransferProgress(
                                fileName = item.fileName,
                                isSending = true,
                                bytesTransferred = totalSent,
                                totalBytes = resolvedSize,
                                speedBytesPerSec = totalSent / elapsedSec,
                            ),
                        )
                        lastEmitTimeMs = now
                    }
                }
                output.flush()
                DropHubCoordinator.touchActivity()
            }
        } finally {
            DropHubCoordinator.clearHostTransfer()
        }
    }

    private fun writeSimpleResponse(
        output: OutputStream,
        statusCode: Int,
        reason: String,
        contentType: String,
        body: ByteArray,
    ) {
        val headers = buildString {
            append("HTTP/1.1 $statusCode $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(Charsets.ISO_8859_1))
        if (body.isNotEmpty()) {
            output.write(body)
        }
        output.flush()
    }

    private fun readAsciiLine(input: InputStream): String? {
        val sb = StringBuilder(128)
        var sawAny = false
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sawAny) sb.toString() else null
            }
            sawAny = true
            if (b == '\n'.code) break
            if (b != '\r'.code && sb.length < 8192) {
                sb.append(b.toChar())
            }
        }
        return sb.toString()
    }

    private fun readFixedBytes(input: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(out, offset, count - offset)
            if (read <= 0) break
            offset += read
        }
        return if (offset == count) out else out.copyOf(offset)
    }

    private fun resolveDeviceDisplayName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Drop Local Wi-Fi Server",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the local offline HTTP Drop server active over Wi-Fi or Hotspot."
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(ip: String?, port: Int, peers: Int): Notification {
        val openPending = PendingIntent.getActivity(
            this,
            700,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPending = PendingIntent.getService(
            this,
            701,
            Intent(this, DropServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val urlText = if (ip != null) "http://$ip:$port" else "Waiting for local Wi-Fi / Hotspot"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("A-Kit Drop Server Online")
            .setContentText("$urlText • $peers active peer(s)")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPending)
            .addAction(0, "Stop Server", stopPending)
            .build()
    }

    /**
     * Zero-buffering HTTP/1.1 `Transfer-Encoding: chunked` streaming decoder so clients using
     * `HttpURLConnection.setChunkedStreamingMode(65536)` can stream multi-GB files directly to disk.
     */
    private class HttpChunkedInputStream(
        private val raw: InputStream,
    ) : InputStream() {
        private var currentChunkRemaining = 0L
        private var finished = false

        override fun read(): Int {
            val single = ByteArray(1)
            val n = read(single, 0, 1)
            return if (n == 1) single[0].toInt() and 0xFF else -1
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (finished) return -1
            if (len == 0) return 0

            while (currentChunkRemaining == 0L) {
                val sizeLine = readLine() ?: run {
                    finished = true
                    return -1
                }
                val hex = sizeLine.substringBefore(';').trim()
                if (hex.isEmpty()) continue
                val chunkSize = hex.toLongOrNull(16) ?: run {
                    finished = true
                    return -1
                }
                if (chunkSize <= 0L) {
                    // Consume trailing headers until blank line
                    while (true) {
                        val trailer = readLine() ?: break
                        if (trailer.isEmpty()) break
                    }
                    finished = true
                    return -1
                }
                currentChunkRemaining = chunkSize
            }

            val toRead = minOf(len.toLong(), currentChunkRemaining).toInt()
            val read = raw.read(b, off, toRead)
            if (read <= 0) {
                finished = true
                return -1
            }
            currentChunkRemaining -= read.toLong()
            if (currentChunkRemaining == 0L) {
                // Consume CRLF ending this chunk
                readLine()
            }
            return read
        }

        private fun readLine(): String? {
            val sb = StringBuilder(32)
            var sawAny = false
            while (true) {
                val ch = raw.read()
                if (ch == -1) return if (sawAny) sb.toString() else null
                sawAny = true
                if (ch == '\n'.code) break
                if (ch != '\r'.code && sb.length < 1024) sb.append(ch.toChar())
            }
            return sb.toString()
        }
    }

    /**
     * Streaming multipart boundary reader that emits file payload bytes directly in 64KB chunks
     * while holding only a small tail window (`boundary.size` bytes) in memory.
     */
    private class MultipartBoundaryInputStream(
        private val source: InputStream,
        private val marker: ByteArray,
    ) : InputStream() {
        private val ring = ByteArray(STREAM_BUFFER_SIZE + marker.size)
        private var start = 0
        private var end = 0
        private var eofReached = false
        private var markerHit = false

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n == 1) one[0].toInt() and 0xFF else -1
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (markerHit && start >= end) return -1
            if (len == 0) return 0

            while (true) {
                val available = end - start
                if (!markerHit && available < marker.size && !eofReached) {
                    refill()
                }
                val currentAvail = end - start
                if (currentAvail <= 0) return -1

                val matchIdx = if (!markerHit) findMarker(ring, start, end, marker) else -1
                if (matchIdx >= 0) {
                    markerHit = true
                    val emitCount = minOf(len, matchIdx - start)
                    if (emitCount > 0) {
                        System.arraycopy(ring, start, b, off, emitCount)
                        start += emitCount
                        return emitCount
                    }
                    start = end
                    return -1
                }

                val safeEmit = if (eofReached || markerHit) {
                    currentAvail
                } else {
                    (currentAvail - marker.size).coerceAtLeast(0)
                }
                if (safeEmit > 0) {
                    val toCopy = minOf(len, safeEmit)
                    System.arraycopy(ring, start, b, off, toCopy)
                    start += toCopy
                    return toCopy
                }
                if (eofReached) return -1
                refill()
            }
        }

        private fun refill() {
            val remaining = end - start
            if (start > 0 && remaining > 0) {
                System.arraycopy(ring, start, ring, 0, remaining)
            }
            start = 0
            end = remaining
            val space = ring.size - end
            if (space <= 0) return
            val read = source.read(ring, end, space)
            if (read <= 0) {
                eofReached = true
            } else {
                end += read
            }
        }

        private fun findMarker(buf: ByteArray, from: Int, to: Int, needle: ByteArray): Int {
            val limit = to - needle.size
            if (limit < from) return -1
            val first = needle[0]
            var i = from
            while (i <= limit) {
                if (buf[i] == first) {
                    var j = 1
                    while (j < needle.size && buf[i + j] == needle[j]) j++
                    if (j == needle.size) return i
                }
                i++
            }
            return -1
        }
    }

    companion object {
        private const val CHANNEL_ID = "akit_drop_server_channel"
        private const val NOTIFICATION_ID = 6301
        private const val DEFAULT_PORT = 8080
        private const val STREAM_BUFFER_SIZE = DropHubCoordinator.STREAM_BUFFER_SIZE
        private const val HEADER_READ_TIMEOUT_MS = 15_000
        private const val MAX_UPLOAD_BYTES = 16L * 1024L * 1024L * 1024L // 16 GB max single transfer
        private const val IDLE_TIMEOUT_MS = 15L * 60_000L
        private const val IDLE_CHECK_INTERVAL_MS = 30_000L

        private const val ACTION_START = "com.example.feature.drop.START_SERVER"
        private const val ACTION_STOP = "com.example.feature.drop.STOP_SERVER"

        fun start(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, DropServerService::class.java).apply { action = ACTION_START }
            runCatching { app.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            runCatching { app.stopService(Intent(app, DropServerService::class.java)) }
        }
    }
}
