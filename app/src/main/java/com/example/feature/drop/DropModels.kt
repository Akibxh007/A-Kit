package com.example.feature.drop

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import java.util.Locale

enum class DropDirection {
    FROM_PHONE,
    FROM_BROWSER,
}

@Immutable
data class DropSnippet(
    val id: String,
    val text: String,
    val senderLabel: String,
    val direction: DropDirection,
    val timestampMs: Long,
)

@Immutable
data class DropFileItem(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val uriString: String,
    val savedLocationLabel: String,
    val direction: DropDirection,
    val timestampMs: Long,
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/", ignoreCase = true)

    fun formatSize(): String {
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        return when {
            sizeBytes <= 0L -> "—"
            kb < 1024.0 -> String.format(Locale.US, "%.1f KB", kb)
            mb < 1024.0 -> String.format(Locale.US, "%.2f MB", mb)
            else -> String.format(Locale.US, "%.2f GB", mb / 1024.0)
        }
    }
}

@Immutable
data class PeerFileItem(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeLabel: String,
    val sourceLabel: String,
    val isImage: Boolean,
)

@Immutable
data class DropQrPayload(
    val serverUrl: String?,
    val wifiSsid: String? = null,
    val wifiPassword: String? = null,
) {
    val hasWifiCredentials: Boolean
        get() = !wifiSsid.isNullOrBlank()
}

@Immutable
data class DropUiState(
    val isServerRunning: Boolean = false,
    val isWifiEnabled: Boolean = false,
    val isWifiConnected: Boolean = false,
    val isLocalHotspotActive: Boolean = false,
    val hotspotSsid: String? = null,
    val hotspotPassword: String? = null,
    val useAutoConnectQr: Boolean = true,
    val localIpAddress: String? = null,
    val serverPort: Int = 8080,
    val deviceBeaconName: String = "Android Device",
    val activePeerCount: Int = 0,
    val snippets: List<DropSnippet> = emptyList(),
    val sharedFiles: List<DropFileItem> = emptyList(),
    val connectedPeerUrl: String? = null,
    val connectedPeerDeviceName: String? = null,
    val connectedWifiSsid: String? = null,
    val isPeerConnecting: Boolean = false,
    val isPeerConnected: Boolean = false,
    val peerSnippets: List<DropSnippet> = emptyList(),
    val peerFiles: List<PeerFileItem> = emptyList(),
    val downloadingPeerFileId: String? = null,
    val statusBanner: String? = null,
) {
    val serverUrl: String?
        get() = localIpAddress?.let { "http://$it:$serverPort" }

    val activeQrContent: String?
        get() {
            val url = serverUrl ?: return null
            return if (isLocalHotspotActive && useAutoConnectQr && !hotspotSsid.isNullOrBlank()) {
                DropUrlParser.buildOfflineQrPayload(
                    serverUrl = url,
                    hotspotSsid = hotspotSsid,
                    hotspotPassword = hotspotPassword,
                )
            } else {
                url
            }
        }
}

object DropUrlParser {

    /**
     * Builds an A-Kit offline auto-connect QR payload that embeds both the local HTTP Drop URL
     * and the active Local-Only Hotspot SSID/password for 1-tap receiver connection.
     */
    fun buildOfflineQrPayload(
        serverUrl: String,
        hotspotSsid: String?,
        hotspotPassword: String?,
    ): String {
        if (hotspotSsid.isNullOrBlank()) return serverUrl
        val encodedUrl = URLEncoder.encode(serverUrl, "UTF-8")
        val encodedSsid = URLEncoder.encode(hotspotSsid, "UTF-8")
        val encodedPass = URLEncoder.encode(hotspotPassword.orEmpty(), "UTF-8")
        return "akit://drop?url=$encodedUrl&ssid=$encodedSsid&pass=$encodedPass"
    }

    /**
     * Parses any supported QR payload into a structured [DropQrPayload]:
     * 1. `akit://drop?url=http%3A%2F%2F192.168.43.1%3A8080&ssid=AndroidShare_1234&pass=abcd1234`
     * 2. Standard `WIFI:T:WPA;S:MyHotspot;P:secret123;;` (optionally with `U:http://...` or newline URL)
     * 3. Plain `http://192.168.1.5:8080` or `192.168.1.5:8080`
     */
    fun parseQrPayload(raw: String): DropQrPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // Format 1: A-Kit custom deep-link payload with optional Wi-Fi hotspot credentials
        if (trimmed.startsWith("akit://", ignoreCase = true)) {
            val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
            val ssid = parsed.getQueryParameter("ssid")?.takeIf { it.isNotBlank() }
            val pass = parsed.getQueryParameter("pass")?.takeIf { it.isNotBlank() }
            val url = normalizeDropTargetUrl(trimmed)
            if (url != null || ssid != null) {
                return DropQrPayload(
                    serverUrl = url,
                    wifiSsid = ssid,
                    wifiPassword = pass,
                )
            }
            return null
        }

        // Format 2: Standard WIFI: QR code (e.g. from Android Hotspot QR or combined payload)
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            val wifiPart = trimmed.substringBefore('\n')
            val trailingLine = trimmed.substringAfter('\n', "").trim()
            val ssid = extractWifiField(wifiPart, "S")
            val pass = extractWifiField(wifiPart, "P")
            val embeddedUrl = extractWifiField(wifiPart, "U")
                ?.let { normalizeDropTargetUrl(it) }
                ?: trailingLine.takeIf { it.isNotBlank() }?.let { normalizeDropTargetUrl(it) }
            if (!ssid.isNullOrBlank()) {
                return DropQrPayload(
                    serverUrl = embeddedUrl,
                    wifiSsid = ssid,
                    wifiPassword = pass,
                )
            }
            return null
        }

        // Format 3: Plain HTTP/HTTPS or bare LAN IP:port
        val normalized = normalizeDropTargetUrl(trimmed) ?: return null
        return DropQrPayload(serverUrl = normalized)
    }

    /**
     * Extracts a clean LAN HTTP base URL (e.g. http://192.168.1.5:8080) from:
     * - akit://drop?url=http%3A%2F%2F192.168.1.5%3A8080
     * - akit://drop/192.168.1.5:8080
     * - http://192.168.1.5:8080/...
     * - 192.168.1.5:8080
     */
    fun normalizeDropTargetUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val candidate = if (trimmed.startsWith("akit://", ignoreCase = true)) {
            val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
            val queryUrl = parsed.getQueryParameter("url")
            val hostParam = parsed.getQueryParameter("host")
            val portParam = parsed.getQueryParameter("port") ?: "8080"
            when {
                !queryUrl.isNullOrBlank() -> queryUrl.trim()
                !hostParam.isNullOrBlank() -> "http://$hostParam:$portParam"
                !parsed.path.isNullOrBlank() -> parsed.path!!.trimStart('/')
                else -> return null
            }
        } else {
            trimmed
        }

        val withScheme = when {
            candidate.startsWith("http://", ignoreCase = true) ||
                candidate.startsWith("https://", ignoreCase = true) -> candidate
            else -> "http://$candidate"
        }

        val uri = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US)?.takeIf { it == "http" || it == "https" } ?: "http"
        val port = if (uri.port > 0) uri.port else 8080
        return "$scheme://$host:$port"
    }

    private fun extractWifiField(wifiString: String, key: String): String? {
        val body = wifiString.removePrefix("WIFI:").removePrefix("wifi:")
        val token = "$key:"
        var index = 0
        while (index < body.length) {
            if (body.startsWith(token, index) && (index == 0 || body[index - 1] == ';')) {
                val valueStart = index + token.length
                val sb = StringBuilder()
                var i = valueStart
                while (i < body.length) {
                    val ch = body[i]
                    if (ch == '\\' && i + 1 < body.length) {
                        sb.append(body[i + 1])
                        i += 2
                    } else if (ch == ';') {
                        break
                    } else {
                        sb.append(ch)
                        i++
                    }
                }
                return sb.toString().takeIf { it.isNotEmpty() }
            }
            index++
        }
        return null
    }
}

object LocalNetworkUtils {

    /**
     * Detects the best local IPv4 address for LAN Wi-Fi or Mobile Hotspot hosting
     * without requiring internet connectivity.
     *
     * When [preferHotspotInterface] is true (e.g., Local-Only Hotspot is active on a device
     * with dual STA+AP concurrency), SoftAP interfaces (`ap*`, `swlan*`, `softap*`, `wlan1`,
     * `wlan2`, or `192.168.49.*` / `192.168.43.*`) are prioritized so the advertised QR URL
     * matches the exact hotspot gateway the receiver phone joins.
     */
    fun detectLocalIpv4Address(preferHotspotInterface: Boolean = false): String? {
        return runCatching {
            val candidates = mutableListOf<Pair<Int, String>>()
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
                val name = nif.name.lowercase(Locale.US)
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun") || name.startsWith("ppp")) {
                    continue
                }
                val addresses = nif.inetAddresses.toList()
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val host = addr.hostAddress ?: continue
                        if (isPrivateLanIpv4(host)) {
                            val isHotspotIf = name.startsWith("ap") ||
                                name.startsWith("swlan") ||
                                name.startsWith("softap") ||
                                name == "wlan1" ||
                                name == "wlan2" ||
                                host.startsWith("192.168.49.") ||
                                host.startsWith("192.168.43.")
                            val priority = if (preferHotspotInterface) {
                                when {
                                    isHotspotIf -> 1
                                    name.startsWith("wlan") -> 2
                                    name.startsWith("eth") -> 3
                                    else -> 4
                                }
                            } else {
                                when {
                                    name == "wlan0" -> 1
                                    isHotspotIf -> 2
                                    name.startsWith("wlan") -> 3
                                    name.startsWith("eth") -> 4
                                    else -> 5
                                }
                            }
                            candidates.add(priority to host)
                        }
                    }
                }
            }
            candidates.minByOrNull { it.first }?.second
                ?: findAnyNonLoopbackIpv4()
        }.getOrNull()
    }

    private fun isPrivateLanIpv4(ip: String): Boolean {
        return ip.startsWith("192.168.") ||
            ip.startsWith("10.") ||
            ip.startsWith("172.")
    }

    private fun findAnyNonLoopbackIpv4(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback) continue
            for (addr in nif.inetAddresses.toList()) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress
                }
            }
        }
        return null
    }
}
