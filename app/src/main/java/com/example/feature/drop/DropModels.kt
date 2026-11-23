package com.example.feature.drop

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.net.Inet4Address
import java.net.NetworkInterface
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
data class DropUiState(
    val isServerRunning: Boolean = false,
    val isWifiEnabled: Boolean = false,
    val isWifiConnected: Boolean = false,
    val localIpAddress: String? = null,
    val serverPort: Int = 8080,
    val deviceBeaconName: String = "Android Device",
    val activePeerCount: Int = 0,
    val snippets: List<DropSnippet> = emptyList(),
    val sharedFiles: List<DropFileItem> = emptyList(),
    val connectedPeerUrl: String? = null,
    val isPeerConnecting: Boolean = false,
    val isPeerConnected: Boolean = false,
    val peerSnippets: List<DropSnippet> = emptyList(),
    val peerFiles: List<PeerFileItem> = emptyList(),
    val downloadingPeerFileId: String? = null,
    val statusBanner: String? = null,
) {
    val serverUrl: String?
        get() = localIpAddress?.let { "http://$it:$serverPort" }
}

object DropUrlParser {

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
}

object LocalNetworkUtils {

    /**
     * Detects the best local IPv4 address for LAN Wi-Fi or Mobile Hotspot hosting
     * without requiring internet connectivity.
     */
    fun detectLocalIpv4Address(): String? {
        return runCatching {
            val candidates = mutableListOf<Pair<Int, String>>()
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
                val name = nif.name.lowercase(Locale.US)
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun") || name.startsWith("ppp")) {
                    continue
                }
                val priority = when {
                    name.startsWith("wlan") -> 1
                    name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> 2
                    name.startsWith("eth") -> 3
                    else -> 4
                }
                val addresses = nif.inetAddresses.toList()
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val host = addr.hostAddress ?: continue
                        if (isPrivateLanIpv4(host)) {
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
