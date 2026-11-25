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
    val preHotspotWlanIp: String? = null,
    val ssid: String? = null,
    val password: String? = null,
    val useAutoConnectQr: Boolean = true,
)

private data class PeerClientState(
    val peerUrl: String? = null,
    val peerDeviceName: String? = null,
    val wifiSsid: String? = null,
    val fallbackWifiPassword: String? = null,
    val specifierNetwork: Network? = null,
    val showWifiFallbackAction: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val snippets: List<DropSnippet> = emptyList(),
    val files: List<PeerFileItem> = emptyList(),
    val downloadingFileId: String? = null,
    val downloadedFiles: Map<String, DropFileItem> = emptyMap(),
    val activeTransfer: DropTransferProgress? = null,
)

private data class LocalContentSnapshot(
    val snippets: List<DropSnippet>,
    val files: List<DropFileItem>,
    val banner: String?,
    val activeHostTransfer: DropTransferProgress?,
    val savingHostFileId: String?,
)

class DropViewModel(application: Application) : AndroidViewModel(application) {

    private val wifiManager =
        application.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager =
        application.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val hotspotState = MutableStateFlow(HotspotState())
    private val networkSnapshot = MutableStateFlow(readNetworkSnapshot(preferHotspot = false))
    private val statusBanner = MutableStateFlow<String?>(null)
    private val savingHostFileId = MutableStateFlow<String?>(null)
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
        DropHubCoordinator.activeHostTransfer,
        savingHostFileId,
    ) { snippets, files, banner, hostTransfer, savingId ->
        LocalContentSnapshot(
            snippets = snippets,
            files = files,
            banner = banner,
            activeHostTransfer = hostTransfer,
            savingHostFileId = savingId,
        )
    }

    val uiState: StateFlow<DropUiState> = combine(
        serverMetaFlow,
        networkAndHotspotFlow,
        localContentFlow,
        peerState,
    ) { meta, (net, hotspot), content, peer ->
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
            snippets = content.snippets,
            sharedFiles = content.files,
            connectedPeerUrl = peer.peerUrl,
            connectedPeerDeviceName = peer.peerDeviceName,
            connectedWifiSsid = peer.wifiSsid,
            isPeerConnecting = peer.isConnecting,
            isPeerConnected = peer.isConnected,
            peerSnippets = peer.snippets,
            peerFiles = peer.files,
            downloadingPeerFileId = peer.downloadingFileId,
            downloadedPeerFiles = peer.downloadedFiles,
            activePeerTransfer = peer.activeTransfer,
            activeHostTransfer = content.activeHostTransfer,
            savingHostFileId = content.savingHostFileId,
            fallbackWifiSsid = peer.wifiSsid,
            fallbackWifiPassword = peer.fallbackWifiPassword,
            showWifiFallbackAction = peer.showWifiFallbackAction,
            statusBanner = content.banner ?: meta.idleMsg,
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
        val currentHotspot = hotspotState.value
        val hotspotActive = currentHotspot.isActive
        networkSnapshot.value = readNetworkSnapshot(preferHotspot = hotspotActive)
        if (hotspotActive) {
            val resolved = LocalNetworkUtils.detectHotspotGatewayIpv4(
                preHotspotWlanIp = currentHotspot.preHotspotWlanIp,
            )
            if (resolved != null) {
                hotspotState.update { it.copy(isResolvingIp = false, resolvedGatewayIp = resolved) }
            }
        }
    }

    /**
     * Called on DropScreen ON_RESUME: as soon as the user returns to A-Kit after connecting to
     * the sender's hotspot in Wi-Fi Settings, immediately fire the `GET /api/ping` handshake over
     * the active `TRANSPORT_WIFI` network (using its dynamic DHCP/route gateway IP) so it connects
     * within 1 second.
     */
    fun retryPendingPeerOnResume() {
        refreshNetwork()
        val peer = peerState.value
        val targetUrl = peer.peerUrl ?: return
        if (peer.isConnected) return

        val activeWifi = peer.specifierNetwork ?: DropOfflineWifiManager.findActiveWifiNetwork(getApplication())
        if (activeWifi != null || peer.showWifiFallbackAction) {
            // Cancel any stale WifiNetworkSpecifier request since Wi-Fi is now active
            hotspotConnectJob?.cancel()
            hotspotConnectJob = null

            val port = extractPort(targetUrl)
            val dynamicGw = DropOfflineWifiManager.resolveGatewayIpv4(getApplication(), activeWifi)
            val preferredUrl = dynamicGw?.let { "http://$it:$port" } ?: targetUrl

            statusBanner.value = "Wi-Fi active — handshaking with $preferredUrl..."
            peerState.update { it.copy(peerUrl = preferredUrl, isConnecting = true) }
            startPeerPolling(
                primaryUrl = preferredUrl,
                qrUrl = targetUrl,
                specifierNetwork = activeWifi,
                initialDelayMs = 0L,
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
        // Release any previous peer process network binding so the host AP interface is unrestricted
        DropOfflineWifiManager.disconnectBoundNetwork(getApplication())
        DropOfflineWifiManager.unbindProcessNetwork(getApplication())
        hotspotIpResolveJob?.cancel()
        val preHotspotIp = networkSnapshot.value.localIp
        statusBanner.value = "Starting 100% offline local hotspot..."
        DropOfflineWifiManager.startLocalOnlyHotspot(
            context = getApplication(),
            onStarted = { ssid, password ->
                hotspotState.value = HotspotState(
                    isActive = true,
                    isResolvingIp = true,
                    resolvedGatewayIp = null,
                    preHotspotWlanIp = preHotspotIp,
                    ssid = ssid,
                    password = password,
                    useAutoConnectQr = true,
                )
                if (!DropHubCoordinator.isServerRunning.value) {
                    DropServerService.start(getApplication())
                }
                statusBanner.value = "Offline Hotspot \"$ssid\" started. Resolving hotspot gateway IP (ap0/wlan0)..."

                // Wait for ap0/ap_br_*/swlan0/p2p0/wlan0 to come up and acquire its real hotspot gateway IP
                // (e.g. 192.168.43.1, 192.168.49.1, 192.168.137.1, or Android 11+ randomized subnet)
                // before generating the QR code — NEVER a cellular 172.x.x.x IP.
                hotspotIpResolveJob = viewModelScope.launch(Dispatchers.IO) {
                    var resolvedIp: String? = null
                    repeat(28) {
                        if (!isActive || !hotspotState.value.isActive) return@launch
                        resolvedIp = LocalNetworkUtils.detectHotspotGatewayIpv4(
                            preHotspotWlanIp = preHotspotIp,
                        )
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

    fun saveHostReceivedFile(item: DropFileItem) {
        if (item.isSavedToDownloads || savingHostFileId.value == item.id) return
        viewModelScope.launch(Dispatchers.IO) {
            savingHostFileId.value = item.id
            val result = DropHubCoordinator.exportHostFileToPublicDownloads(
                context = getApplication(),
                fileId = item.id,
            )
            savingHostFileId.value = null
            statusBanner.value = if (result.isSuccess) {
                "Saved \"${item.fileName}\" to Downloads/A-Kit."
            } else {
                "Could not save \"${item.fileName}\" to Downloads/A-Kit."
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
            val qrTarget = payload.serverUrl ?: "http://192.168.43.1:8080"
            val port = extractPort(qrTarget)

            peerState.value = PeerClientState(
                peerUrl = qrTarget,
                wifiSsid = ssid,
                fallbackWifiPassword = password,
                specifierNetwork = null,
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
                result.onSuccess { bound ->
                    val dynamicGatewayUrl = bound.gatewayIp?.let { "http://$it:$port" }
                    val primaryUrl = dynamicGatewayUrl ?: qrTarget
                    peerState.update {
                        it.copy(
                            peerUrl = primaryUrl,
                            specifierNetwork = bound.network,
                            showWifiFallbackAction = false,
                        )
                    }
                    statusBanner.value = "Joined \"$ssid\"! Connecting to $primaryUrl..."
                    startPeerPolling(
                        primaryUrl = primaryUrl,
                        qrUrl = qrTarget,
                        specifierNetwork = bound.network,
                        initialDelayMs = 150L,
                    )
                }.onFailure { err ->
                    peerState.update {
                        it.copy(
                            specifierNetwork = null,
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
            specifierNetwork = null,
            showWifiFallbackAction = false,
            isConnecting = true,
            isConnected = false,
        )
        statusBanner.value = "Connecting to peer at $targetUrl..."
        startPeerPolling(
            primaryUrl = targetUrl,
            qrUrl = null,
            specifierNetwork = null,
            initialDelayMs = 0L,
        )
    }

    fun connectToPeerUrl(rawUrl: String) {
        connectFromRawPayload(rawUrl)
    }

    private fun startPeerPolling(
        primaryUrl: String,
        qrUrl: String?,
        specifierNetwork: Network?,
        initialDelayMs: Long,
    ) {
        peerPollingJob?.cancel()
        peerPollingJob = viewModelScope.launch(Dispatchers.IO) {
            if (initialDelayMs > 0L) {
                delay(initialDelayMs)
            }
            var activeUrl = primaryUrl
            val port = extractPort(primaryUrl)
            var consecutiveFailures = 0
            while (isActive) {
                // Always bind both the socket and the process directly to the saved WifiNetworkSpecifier
                // Network (or active TRANSPORT_WIFI) so Offline Hotspot traffic NEVER leaks onto Mobile Data
                val wifiNetwork = DropOfflineWifiManager.bindProcessToWifi(
                    context = getApplication(),
                    network = specifierNetwork
                        ?: peerState.value.specifierNetwork
                        ?: DropOfflineWifiManager.findActiveWifiNetwork(getApplication()),
                )

                // Step 1: Reliable /api/ping handshake across dynamic DHCP/route gateway + QR URL + hotspot subnets
                if (!peerState.value.isConnected) {
                    val dynamicGwUrl = DropOfflineWifiManager
                        .resolveGatewayIpv4(getApplication(), wifiNetwork)
                        ?.let { "http://$it:$port" }

                    val subnetCandidates = LocalNetworkUtils
                        .detectReceiverHotspotGatewayCandidates()
                        .map { "http://$it:$port" }

                    val candidates = buildList {
                        if (dynamicGwUrl != null) add(dynamicGwUrl)
                        add(activeUrl)
                        if (!qrUrl.isNullOrBlank()) add(qrUrl)
                        addAll(subnetCandidates)
                    }.distinct()

                    var connectedDevice: String? = null
                    for (candidate in candidates) {
                        val pingResult = DropPeerClient.ping(candidate, wifiNetwork)
                        if (pingResult.isSuccess) {
                            activeUrl = candidate
                            connectedDevice = pingResult.getOrNull() ?: "A-Kit Peer"
                            break
                        }
                    }

                    if (connectedDevice != null) {
                        consecutiveFailures = 0
                        peerState.update {
                            it.copy(
                                peerUrl = activeUrl,
                                peerDeviceName = connectedDevice,
                                specifierNetwork = wifiNetwork ?: it.specifierNetwork,
                                showWifiFallbackAction = false,
                                isConnecting = false,
                                isConnected = true,
                            )
                        }
                        statusBanner.value = "Connected to $connectedDevice ($activeUrl)"
                    }
                }

                // Step 2: Sync live snippets & files directly over the bound Wi-Fi network
                val result = DropPeerClient.fetchState(activeUrl, wifiNetwork)
                if (result.isSuccess) {
                    val payload = result.getOrThrow()
                    val firstConnect = !peerState.value.isConnected
                    consecutiveFailures = 0
                    peerState.update {
                        it.copy(
                            peerUrl = activeUrl,
                            specifierNetwork = wifiNetwork ?: it.specifierNetwork,
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
                    if (consecutiveFailures >= 4) {
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
                delay(if (peerState.value.isConnected) 2_500L else 600L)
            }
        }
    }

    private fun extractPort(url: String): Int {
        return runCatching { Uri.parse(url).port }.getOrDefault(-1).takeIf { it > 0 } ?: 8080
    }

    private fun resolvePeerNetwork(): Network? {
        val candidate = peerState.value.specifierNetwork
            ?: DropOfflineWifiManager.findActiveWifiNetwork(getApplication())
        return DropOfflineWifiManager.bindProcessToWifi(getApplication(), candidate) ?: candidate
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
        val wifiNet = resolvePeerNetwork()
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
        val wifiNet = resolvePeerNetwork()
        viewModelScope.launch(Dispatchers.IO) {
            statusBanner.value = "Streaming ${uris.size} item(s) at full Wi-Fi speed..."
            var successCount = 0
            try {
                uris.forEach { uri ->
                    val res = DropPeerClient.uploadUri(
                        context = getApplication(),
                        baseUrl = url,
                        uri = uri,
                        boundNetwork = wifiNet,
                        onProgress = { progress ->
                            peerState.update { it.copy(activeTransfer = progress) }
                        },
                    )
                    if (res.isSuccess) {
                        successCount++
                    }
                }
            } finally {
                peerState.update { it.copy(activeTransfer = null) }
            }
            if (successCount > 0) {
                statusBanner.value = "Uploaded $successCount item(s) to peer."
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
        val wifiNet = resolvePeerNetwork()
        viewModelScope.launch(Dispatchers.IO) {
            peerState.update { it.copy(downloadingFileId = item.id) }
            statusBanner.value = "Downloading \"${item.fileName}\"..."
            val result = try {
                DropPeerClient.downloadFileToDownloads(
                    context = getApplication(),
                    baseUrl = url,
                    item = item,
                    boundNetwork = wifiNet,
                    onProgress = { progress ->
                        peerState.update { it.copy(activeTransfer = progress) }
                    },
                )
            } finally {
                peerState.update {
                    it.copy(
                        downloadingFileId = null,
                        activeTransfer = null,
                    )
                }
            }
            val savedItem = result.getOrNull()
            if (savedItem != null) {
                peerState.update {
                    it.copy(downloadedFiles = it.downloadedFiles + (item.id to savedItem))
                }
                statusBanner.value = "Saved \"${item.fileName}\" to Downloads/A-Kit."
            } else {
                statusBanner.value = "Failed to download \"${item.fileName}\"."
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
