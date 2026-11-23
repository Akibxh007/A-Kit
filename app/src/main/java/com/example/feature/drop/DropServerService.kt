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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
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
                client.soTimeout = 30_000
                val remoteIp = client.inetAddress?.hostAddress
                DropHubCoordinator.recordClientActivity(remoteIp)

                val input = BufferedInputStream(client.getInputStream(), 16 * 1024)
                val output = client.getOutputStream()

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

                val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L

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
                        if (contentLength <= 0L || contentLength > MAX_UPLOAD_BYTES) {
                            writeSimpleResponse(
                                output,
                                413,
                                "Payload Too Large",
                                "application/json",
                                """{"ok":false,"error":"Invalid file size"}""".toByteArray(Charsets.UTF_8),
                            )
                            return
                        }
                        val encodedName = headers["x-file-name"] ?: "Upload_${System.currentTimeMillis()}.bin"
                        val fileName = runCatching {
                            URLDecoder.decode(encodedName, "UTF-8")
                        }.getOrDefault(encodedName)
                        val mimeType = headers["x-mime-type"] ?: "application/octet-stream"

                        val saved = DropHubCoordinator.saveUploadedStreamToDownloads(
                            context = applicationContext,
                            rawFileName = fileName,
                            mimeType = mimeType,
                            inputStream = input,
                            expectedBytes = contentLength,
                        )
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
        stream.use { input ->
            val headerBuilder = StringBuilder()
                .append("HTTP/1.1 200 OK\r\n")
                .append("Content-Type: ${item.mimeType}\r\n")
                .append("Content-Disposition: inline; filename=\"$safeDispositionName\"\r\n")
            if (item.sizeBytes > 0L) {
                headerBuilder.append("Content-Length: ${item.sizeBytes}\r\n")
            }
            headerBuilder
                .append("Cache-Control: no-store\r\n")
                .append("Connection: close\r\n\r\n")

            output.write(headerBuilder.toString().toByteArray(Charsets.ISO_8859_1))
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
            }
            output.flush()
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
        val out = ByteArrayOutputStream(128)
        var sawAny = false
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sawAny) out.toString("ISO-8859-1") else null
            }
            sawAny = true
            if (b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
            if (out.size() > 8192) break
        }
        return out.toString("ISO-8859-1")
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

    companion object {
        private const val CHANNEL_ID = "akit_drop_server_channel"
        private const val NOTIFICATION_ID = 6301
        private const val DEFAULT_PORT = 8080
        private const val MAX_UPLOAD_BYTES = 150L * 1024L * 1024L

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
