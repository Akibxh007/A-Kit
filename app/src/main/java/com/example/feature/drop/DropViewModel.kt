package com.example.feature.drop

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

private data class NetworkSnapshot(
    val isWifiEnabled: Boolean,
    val isWifiConnected: Boolean,
    val localIp: String?,
)

private data class HotspotState(
    val isActive: Boolean = false,
    val isResolvingIp: Boolean = false,
    val resolvedGatewayIp: String? = null,
    val ssid: String? = null,
    val password: String? = null,
    val useAutoConnectQr: Boolean = true,
)

private data class PeerClientState(
    val peerUrl: String? = null,
    val peerDeviceName: String? = null,
    val wifiSsid: String? = null,
    val fallbackWifiPassword: String? = null,
    val showWifiFallbackAction: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val snippets: List<DropSnippet> = emptyList(),
    val files: List<PeerFileItem> = emptyList(),
    val downloadingFileId: String? = null,
)

class DropViewModel(application: Application) : AndroidViewModel(application) {

    private val wifiManager =
        application.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager =
        application.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val hotspotState = MutableStateFlow(HotspotState())
    private val networkSnapshot = MutableStateFlow(readNetworkSnapshot(preferHotspot = false))
    private val statusBanner = MutableStateFlow<String?>(null)
    private val peerState = MutableStateFlow(PeerClientState())
    private val deviceName = resolveDeviceName()
    private var peerPollingJob: Job? = null
    private var hotspotConnectJob: Job? = null
    private var hotspotIpResolveJob: Job? = null

    private val serverMetaFlow = combine(
        DropHubCoordinator.isServerRunning,
        DropHubCoordinator.boundPort,
        DropHubCoordinator.activePeerCount,
        DropHubCoordinator.idleShutdownMessage,
    ) { running, port, peers, idleMsg ->
        ServerMeta(running, port, peers, idleMsg)
    }

    private val networkAndHotspotFlow = combine(
        networkSnapshot,
        hotspotState,
    ) { net, hotspot ->
        net to hotspot
    }

    private val localContentFlow = combine(
        DropHubCoordinator.snippets,
        DropHubCoordinator.sharedFiles,
        statusBanner,
    ) { snippets, files, banner ->
        Triple(snippets, files, banner)
    }

    val uiState: StateFlow<DropUiState> = combine(
        serverMetaFlow,
        networkAndHotspotFlow,
        localContentFlow,
        peerState,
    ) { meta, (net, hotspot), (snippets, files, banner), peer ->
        val effectiveIp = if (hotspot.isActive) {
            hotspot.resolvedGatewayIp
        } else {
            net.localIp
        }
        DropUiState(
            isServerRunning = meta.running,
            isWifiEnabled = net.isWifiEnabled,
            isWifiConnected = net.isWifiConnected,
            isLocalHotspotActive = hotspot.isActive,
            isHotspotResolvingIp = hotspot.isResolvingIp,
            hotspotSsid = hotspot.ssid,
            hotspotPassword = hotspot.password,
            useAutoConnectQr = hotspot.useAutoConnectQr,
            localIpAddress = effectiveIp,
            serverPort = meta.port,
            deviceBeaconName = deviceName,
            activePeerCount = meta.peers,
            snippets = snippets,
            sharedFiles = files,
            connectedPeerUrl = peer.peerUrl,
            connectedPeerDeviceName = peer.peerDeviceName,
            connectedWifiSsid = peer.wifiSsid,
            isPeerConnecting = peer.isConnecting,
            isPeerConnected = peer.isConnected,
            peerSnippets = peer.snippets,
            peerFiles = peer.files,
            downloadingPeerFileId = peer.downloadingFileId,
            fallbackWifiSsid = peer.wifiSsid,
            fallbackWifiPassword = peer.fallbackWifiPassword,
            showWifiFallbackAction = peer.showWifiFallbackAction,
            statusBanner = banner ?: meta.idleMsg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DropUiState(
            isServerRunning = DropHubCoordinator.isServerRunning.value,
            isWifiEnabled = networkSnapshot.value.isWifiEnabled,
            isWifiConnected = networkSnapshot.value.isWifiConnected,
            localIpAddress = networkSnapshot.value.localIp,
            serverPort = DropHubCoordinator.boundPort.value,
            deviceBeaconName = deviceName,
        ),
    )

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                refreshNetwork()
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetwork()
        override fun onLost(network: Network) = refreshNetwork()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
            refreshNetwork()
    }

    init {
        application.registerReceiver(
            wifiStateReceiver,
            IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
        )
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        }
    }

    fun refreshNetwork() {
        val hotspotActive = hotspotState.value.isActive
        networkSnapshot.value = readNetworkSnapshot(preferHotspot = hotspotActive)
        if (hotspotActive) {
            val resolved = LocalNetworkUtils.detectHotspotGatewayIpv4()
            if (resolved != null) {
                hotspotState.update { it.copy(isResolvingIp = false, resolvedGatewayIp = resolved) }
            }
        }
    }

    /**
     * Called on DropScreen ON_RESUME: if the user just returned from Wi-Fi Settings after
     * manually joining the sender's Offline Hotspot, immediately retry connecting over Wi-Fi.
     */
    fun retryPendingPeerOnResume() {
        refreshNetwork()
        val peer = peerState.value
        val targetUrl = peer.peerUrl ?: return
        if (!peer.isConnected && !peer.isConnecting && peer.showWifiFallbackAction) {
            statusBanner.value = "Checking connection to $targetUrl over Wi-Fi..."
            peerState.update { it.copy(isConnecting = true) }
            val fallbackUrl = if (targetUrl.contains("192.168.43.1")) {
                "http://192.168.49.1:8080"
            } else if (targetUrl.contains("192.168.49.1")) {
                "http://192.168.43.1:8080"
            } else {
                null
            }
            startPeerPolling(
                primaryUrl = targetUrl,
                fallbackUrl = fallbackUrl,
                boundNetwork = DropOfflineWifiManager.activeBoundNetwork,
                initialDelayMs = 200L,
            )
        }
    }

    fun dismissBanner() {
        statusBanner.value = null
        DropHubCoordinator.clearIdleShutdownMessage()
    }

    fun toggleDropServer() {
        refreshNetwork()
        DropHubCoordinator.clearIdleShutdownMessage()
        if (DropHubCoordinator.isServerRunning.value) {
            DropServerService.stop(getApplication())
            if (hotspotState.value.isActive) {
                stopOfflineHotspot(silent = true)
            }
            statusBanner.value = "Local Drop Server stopped."
        } else {
            DropServerService.start(getApplication())
            val ip = networkSnapshot.value.localIp
            statusBanner.value = if (ip != null) {
                "Local Drop Server live at http://$ip:${DropHubCoordinator.boundPort.value}"
            } else {
                "Local Drop Server started. Connect to Wi-Fi, turn on Portable Hotspot, or tap Offline Hotspot."
            }
        }
    }

    // --- 1-Tap Offline Local-Only Hotspot (Zero Internet Required) ---

    fun startOfflineHotspot() {
        DropHubCoordinator.clearIdleShutdownMessage()
        hotspotIpResolveJob?.cancel()
        statusBanner.value = "Starting 100% offline local hotspot..."
        DropOfflineWifiManager.startLocalOnlyHotspot(
            context = getApplication(),
            onStarted = { ssid, password ->
                hotspotState.value = HotspotState(
                    isActive = true,
                    isResolvingIp = true,
                    resolvedGatewayIp = null,
                    ssid = ssid,
                    password = password,
                    useAutoConnectQr = true,
                )
                if (!DropHubCoordinator.isServerRunning.value) {
                    DropServerService.start(getApplication())
                }
                statusBanner.value = "Offline Hotspot \"$ssid\" started. Resolving hotspot gateway IP (ap0/wlan0)..."

                // Wait for ap0/swlan0/wlan0 to come up and acquire its real hotspot gateway IP
                // (e.g. 192.168.43.1 or 192.168.49.1) before generating the QR code — NEVER a cellular 172.x.x.x IP.
                hotspotIpResolveJob = viewModelScope.launch(Dispatchers.IO) {
                    var resolvedIp: String? = null
                    repeat(28) {
                        if (!isActive || !hotspotState.value.isActive) return@launch
                        resolvedIp = LocalNetworkUtils.detectHotspotGatewayIpv4()
                        if (resolvedIp != null) return@repeat
                        delay(300L)
                    }
                    if (!hotspotState.value.isActive) return@launch

                    val finalIp = resolvedIp ?: "192.168.43.1"
                    hotspotState.update {
                        it.copy(
                            isResolvingIp = false,
                            resolvedGatewayIp = finalIp,
                        )
                    }
                    refreshNetwork()
                    statusBanner.value = if (resolvedIp != null) {
                        "Offline Hotspot \"$ssid\" live at http://$finalIp:${DropHubCoordinator.boundPort.value}!"
                    } else {
                        "Offline Hotspot \"$ssid\" live (default gateway $finalIp). Scan QR inside A-Kit or copy password."
                    }
                }
            },
            onStopped = {
                hotspotIpResolveJob?.cancel()
                hotspotState.value = HotspotState()
                refreshNetwork()
            },
            onError = { errorMsg ->
                hotspotIpResolveJob?.cancel()
                hotspotState.value = HotspotState()
                statusBanner.value = errorMsg
            },
        )
    }

    fun stopOfflineHotspot(silent: Boolean = false) {
        hotspotIpResolveJob?.cancel()
        DropOfflineWifiManager.stopLocalOnlyHotspot()
        hotspotState.value = HotspotState()
        refreshNetwork()
        if (!silent) {
            statusBanner.value = "Offline Hotspot stopped."
        }
    }

    fun setUseAutoConnectQr(enabled: Boolean) {
        hotspotState.update { it.copy(useAutoConnectQr = enabled) }
    }

    fun sendSnippetToPortal(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        DropHubCoordinator.addSnippetFromPhone(trimmed)
        statusBanner.value = if (DropHubCoordinator.isServerRunning.value) {
            "Snippet pushed live to Web Portal."
        } else {
            "Snippet staged. Start the Local Drop Server so nearby devices can grab it."
        }
    }

    fun stageFilesFromPhone(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            uris.forEach { uri ->
                if (DropHubCoordinator.shareFileFromPhone(getApplication(), uri) != null) {
                    count++
                }
            }
            if (count > 0) {
                statusBanner.value = "Added $count item(s) to the Local Drop Portal."
            }
        }
    }

    // --- Native In-App Peer Client Mode (QR Scan / Offline Hotspot Auto-Join / Deep Link) ---

    fun connectFromRawPayload(raw: String) {
        val parsed = DropUrlParser.parseQrPayload(raw)
        if (parsed == null) {
            statusBanner.value = "Invalid A-Kit Drop QR or URL."
            return
        }
        connectFromQrPayload(parsed)
    }

    fun connectFromQrPayload(payload: DropQrPayload) {
        peerPollingJob?.cancel()
        peerPollingJob = null
        hotspotConnectJob?.cancel()
        hotspotConnectJob = null

        if (payload.hasWifiCredentials) {
            val ssid = payload.wifiSsid.orEmpty()
            val password = payload.wifiPassword
            val primaryTarget = payload.serverUrl ?: "http://192.168.43.1:8080"
            val fallbackTarget = when {
                payload.serverUrl == null -> "http://192.168.49.1:8080"
                primaryTarget.contains("192.168.43.1") -> "http://192.168.49.1:8080"
                primaryTarget.contains("192.168.49.1") -> "http://192.168.43.1:8080"
                else -> "http://192.168.43.1:8080"
            }

            peerState.value = PeerClientState(
                peerUrl = primaryTarget,
                wifiSsid = ssid,
                fallbackWifiPassword = password,
                showWifiFallbackAction = false,
                isConnecting = true,
                isConnected = false,
            )
            statusBanner.value = "Requesting connection to \"$ssid\"... Tap Connect on the system prompt."

            // Run WifiNetworkSpecifier request inside viewModelScope (independent of the closing QR sheet)
            hotspotConnectJob = viewModelScope.launch {
                val result = DropOfflineWifiManager.awaitHotspotConnection(
                    context = getApplication(),
                    ssid = ssid,
                    password = password,
                )
                result.onSuccess { boundNetwork ->
                    peerState.update { it.copy(showWifiFallbackAction = false) }
                    statusBanner.value = "Joined \"$ssid\"! Verifying Drop peer..."
                    startPeerPolling(
                        primaryUrl = primaryTarget,
                        fallbackUrl = fallbackTarget,
                        boundNetwork = boundNetwork,
                        initialDelayMs = 650L,
                    )
                }.onFailure { err ->
                    peerState.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = false,
                            showWifiFallbackAction = true,
                        )
                    }
                    statusBanner.value = err.message
                        ?: "Could not auto-join \"$ssid\". Copy password & join in Wi-Fi Settings."
                }
            }
            return
        }

        val targetUrl = payload.serverUrl
        if (targetUrl == null) {
            statusBanner.value = "Invalid A-Kit Drop URL."
            return
        }

        DropOfflineWifiManager.disconnectBoundNetwork(getApplication())
        peerState.value = PeerClientState(
            peerUrl = targetUrl,
            wifiSsid = null,
            fallbackWifiPassword = null,
            showWifiFallbackAction = false,
            isConnecting = true,
            isConnected = false,
        )
        statusBanner.value = "Connecting to peer at $targetUrl..."
        startPeerPolling(
            primaryUrl = targetUrl,
            fallbackUrl = null,
            boundNetwork = null,
            initialDelayMs = 0L,
        )
    }

    fun connectToPeerUrl(rawUrl: String) {
        connectFromRawPayload(rawUrl)
    }

    private fun startPeerPolling(
        primaryUrl: String,
        fallbackUrl: String?,
        boundNetwork: Network?,
        initialDelayMs: Long,
    ) {
        peerPollingJob?.cancel()
        peerPollingJob = viewModelScope.launch(Dispatchers.IO) {
            if (initialDelayMs > 0L) {
                delay(initialDelayMs)
            }
            var activeUrl = primaryUrl
            var consecutiveFailures = 0
            while (isActive) {
                // Always prefer explicit TRANSPORT_WIFI Network so local LAN traffic never routes over Mobile Data
                val wifiNetwork = boundNetwork ?: DropOfflineWifiManager.findActiveWifiNetwork(getApplication())

                // Step 1: Reliable /api/ping handshake when not yet connected
                if (!peerState.value.isConnected) {
                    var pingResult = DropPeerClient.ping(activeUrl, wifiNetwork)
                    if (pingResult.isFailure && fallbackUrl != null && activeUrl == primaryUrl) {
                        val altPing = DropPeerClient.ping(fallbackUrl, wifiNetwork)
                        if (altPing.isSuccess) {
                            activeUrl = fallbackUrl
                            pingResult = altPing
                        }
                    }
                    if (pingResult.isSuccess) {
                        val peerDevice = pingResult.getOrNull() ?: "A-Kit Peer"
                        consecutiveFailures = 0
                        peerState.update {
                            it.copy(
                                peerUrl = activeUrl,
                                peerDeviceName = peerDevice,
                                showWifiFallbackAction = false,
                                isConnecting = false,
                                isConnected = true,
                            )
                        }
                        statusBanner.value = "Connected to $peerDevice ($activeUrl)"
                    }
                }

                // Step 2: Sync live snippets & files over the same Wi-Fi network
                var result = DropPeerClient.fetchState(activeUrl, wifiNetwork)
                if (result.isFailure && fallbackUrl != null && activeUrl == primaryUrl) {
                    val altResult = DropPeerClient.fetchState(fallbackUrl, wifiNetwork)
                    if (altResult.isSuccess) {
                        activeUrl = fallbackUrl
                        peerState.update { it.copy(peerUrl = fallbackUrl) }
                        result = altResult
                    }
                }

                if (result.isSuccess) {
                    val payload = result.getOrThrow()
                    val firstConnect = !peerState.value.isConnected
                    consecutiveFailures = 0
                    peerState.update {
                        it.copy(
                            peerUrl = activeUrl,
                            showWifiFallbackAction = false,
                            isConnecting = false,
                            isConnected = true,
                            snippets = payload.snippets,
                            files = payload.files,
                        )
                    }
                    if (firstConnect) {
                        statusBanner.value = "Connected natively to $activeUrl"
                    }
                } else {
                    consecutiveFailures += 1
                    if (consecutiveFailures >= 3) {
                        val hasHotspotCreds = !peerState.value.wifiSsid.isNullOrBlank()
                        peerState.update {
                            it.copy(
                                isConnecting = false,
                                isConnected = false,
                                showWifiFallbackAction = hasHotspotCreds,
                            )
                        }
                        statusBanner.value = if (hasHotspotCreds) {
                            "Cannot reach $activeUrl. Tap below to copy password & join \"${peerState.value.wifiSsid}\" in Wi-Fi Settings."
                        } else {
                            "Cannot reach $activeUrl. Ensure both devices are on the same Wi-Fi or Hotspot."
                        }
                    }
                }
                delay(2_500L)
            }
        }
    }

    fun disconnectPeer() {
        hotspotConnectJob?.cancel()
        hotspotConnectJob = null
        peerPollingJob?.cancel()
        peerPollingJob = null
        DropOfflineWifiManager.disconnectBoundNetwork(getApplication())
        peerState.value = PeerClientState()
        statusBanner.value = "Disconnected from remote Drop peer."
    }

    fun sendSnippetToPeer(text: String) {
        val url = peerState.value.peerUrl ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val wifiNet = DropOfflineWifiManager.findActiveWifiNetwork(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            val res = DropPeerClient.sendSnippet(url, trimmed, wifiNet)
            if (res.isSuccess) {
                statusBanner.value = "Snippet sent to peer."
                DropPeerClient.fetchState(url, wifiNet).getOrNull()?.let { payload ->
                    peerState.update { it.copy(snippets = payload.snippets, files = payload.files) }
                }
            } else {
                statusBanner.value = "Failed to send snippet to peer."
            }
        }
    }

    fun uploadFilesToPeer(uris: List<Uri>) {
        val url = peerState.value.peerUrl ?: return
        if (uris.isEmpty()) return
        val wifiNet = DropOfflineWifiManager.findActiveWifiNetwork(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            statusBanner.value = "Streaming ${uris.size} item(s) at full Wi-Fi speed..."
            var successCount = 0
            uris.forEach { uri ->
                if (DropPeerClient.uploadUri(getApplication(), url, uri, wifiNet).isSuccess) {
                    successCount++
                }
            }
            if (successCount > 0) {
                statusBanner.value = "Uploaded $successCount item(s) to peer's Downloads/A-Kit."
                DropPeerClient.fetchState(url, wifiNet).getOrNull()?.let { payload ->
                    peerState.update { it.copy(snippets = payload.snippets, files = payload.files) }
                }
            } else {
                statusBanner.value = "Upload failed. Check that both devices stay on the same Wi-Fi or Hotspot."
            }
        }
    }

    fun downloadPeerFile(item: PeerFileItem) {
        val url = peerState.value.peerUrl ?: return
        val wifiNet = DropOfflineWifiManager.findActiveWifiNetwork(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            peerState.update { it.copy(downloadingFileId = item.id) }
            statusBanner.value = "Downloading \"${item.fileName}\"..."
            val result = DropPeerClient.downloadFileToDownloads(getApplication(), url, item, wifiNet)
            peerState.update { it.copy(downloadingFileId = null) }
            statusBanner.value = if (result.isSuccess) {
                "Saved \"${item.fileName}\" to Downloads/A-Kit."
            } else {
                "Failed to download \"${item.fileName}\"."
            }
        }
    }

    private fun readNetworkSnapshot(preferHotspot: Boolean): NetworkSnapshot {
        val wifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
        val wifiConnected = runCatching {
            val activeNetwork = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }.getOrDefault(false)
        val ip = LocalNetworkUtils.detectLocalIpv4Address(preferHotspotInterface = preferHotspot)
        return NetworkSnapshot(
            isWifiEnabled = wifiEnabled,
            isWifiConnected = wifiConnected,
            localIp = ip,
        )
    }

    private fun resolveDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }.ifBlank { "Android Peer" }
    }

    override fun onCleared() {
        hotspotIpResolveJob?.cancel()
        hotspotConnectJob?.cancel()
        peerPollingJob?.cancel()
        DropOfflineWifiManager.disconnectBoundNetwork(getApplication())
        DropOfflineWifiManager.stopLocalOnlyHotspot()
        runCatching { getApplication<Application>().unregisterReceiver(wifiStateReceiver) }
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    private data class ServerMeta(
        val running: Boolean,
        val port: Int,
        val peers: Int,
        val idleMsg: String?,
    )
}
