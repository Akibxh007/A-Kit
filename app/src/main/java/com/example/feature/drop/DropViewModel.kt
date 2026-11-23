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
    val ssid: String? = null,
    val password: String? = null,
    val useAutoConnectQr: Boolean = true,
)

private data class PeerClientState(
    val peerUrl: String? = null,
    val peerDeviceName: String? = null,
    val wifiSsid: String? = null,
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
        DropUiState(
            isServerRunning = meta.running,
            isWifiEnabled = net.isWifiEnabled,
            isWifiConnected = net.isWifiConnected,
            isLocalHotspotActive = hotspot.isActive,
            hotspotSsid = hotspot.ssid,
            hotspotPassword = hotspot.password,
            useAutoConnectQr = hotspot.useAutoConnectQr,
            localIpAddress = net.localIp,
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
        networkSnapshot.value = readNetworkSnapshot(preferHotspot = hotspotState.value.isActive)
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
        statusBanner.value = "Starting 100% offline local hotspot..."
        DropOfflineWifiManager.startLocalOnlyHotspot(
            context = getApplication(),
            onStarted = { ssid, password ->
                hotspotState.value = HotspotState(
                    isActive = true,
                    ssid = ssid,
                    password = password,
                    useAutoConnectQr = true,
                )
                if (!DropHubCoordinator.isServerRunning.value) {
                    DropServerService.start(getApplication())
                }
                statusBanner.value = "Offline Hotspot \"$ssid\" is live! Scan the QR inside A-Kit on another phone for 1-tap connect."
                viewModelScope.launch(Dispatchers.IO) {
                    listOf(350L, 900L, 1_800L).forEach { waitMs ->
                        delay(waitMs)
                        refreshNetwork()
                    }
                }
            },
            onStopped = {
                hotspotState.value = HotspotState()
                refreshNetwork()
            },
            onError = { errorMsg ->
                hotspotState.value = HotspotState()
                statusBanner.value = errorMsg
            },
        )
    }

    fun stopOfflineHotspot(silent: Boolean = false) {
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

        if (payload.hasWifiCredentials) {
            val ssid = payload.wifiSsid.orEmpty()
            val primaryTarget = payload.serverUrl ?: "http://192.168.43.1:8080"
            val fallbackTarget = if (payload.serverUrl == null) "http://192.168.49.1:8080" else null

            peerState.value = PeerClientState(
                peerUrl = primaryTarget,
                wifiSsid = ssid,
                isConnecting = true,
                isConnected = false,
            )
            statusBanner.value = "Joining offline hotspot \"$ssid\"..."

            DropOfflineWifiManager.connectToHotspot(
                context = getApplication(),
                ssid = ssid,
                password = payload.wifiPassword,
                onConnected = { boundNetwork ->
                    statusBanner.value = "Joined \"$ssid\"! Opening offline Drop session..."
                    startPeerPolling(
                        primaryUrl = primaryTarget,
                        fallbackUrl = fallbackTarget,
                        boundNetwork = boundNetwork,
                        initialDelayMs = 650L,
                    )
                },
                onFailed = { errorMsg ->
                    peerState.update { it.copy(isConnecting = false, isConnected = false) }
                    statusBanner.value = errorMsg
                },
            )
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
                        peerState.update { it.copy(isConnecting = false, isConnected = false) }
                        statusBanner.value = "Cannot reach $activeUrl. Ensure both devices are on the same Wi-Fi or Hotspot."
                    }
                }
                delay(2_500L)
            }
        }
    }

    fun disconnectPeer() {
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
            statusBanner.value = "Uploading ${uris.size} item(s) to peer..."
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
                statusBanner.value = "Upload failed. Check that the peer server is still online."
            }
        }
    }

    fun downloadPeerFile(item: PeerFileItem) {
        val url = peerState.value.peerUrl ?: return
        val wifiNet = DropOfflineWifiManager.findActiveWifiNetwork(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            peerState.update { it.copy(downloadingFileId = item.id) }
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
