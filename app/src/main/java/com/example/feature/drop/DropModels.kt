package com.example.feature.drop

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

enum class DropDirection {
    FROM_PHONE,
    FROM_BROWSER,
}

data class DropSnippet(
    val id: String,
    val text: String,
    val senderLabel: String,
    val direction: DropDirection,
    val timestampMs: Long,
)

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
    val statusBanner: String? = null,
) {
    val serverUrl: String?
        get() = localIpAddress?.let { "http://$it:$serverPort" }
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
                // Skip cellular data interfaces where inbound peer connections are blocked
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
