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

private data class PeerClientState(
    val peerUrl: String? = null,
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

    private val networkSnapshot = MutableStateFlow(readNetworkSnapshot())
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

    private val localContentFlow = combine(
        DropHubCoordinator.snippets,
        DropHubCoordinator.sharedFiles,
        statusBanner,
    ) { snippets, files, banner ->
        Triple(snippets, files, banner)
    }

    val uiState: StateFlow<DropUiState> = combine(
        serverMetaFlow,
        networkSnapshot,
        localContentFlow,
        peerState,
    ) { meta, net, (snippets, files, banner), peer ->
        DropUiState(
            isServerRunning = meta.running,
            isWifiEnabled = net.isWifiEnabled,
            isWifiConnected = net.isWifiConnected,
            localIpAddress = net.localIp,
            serverPort = meta.port,
            deviceBeaconName = deviceName,
            activePeerCount = meta.peers,
            snippets = snippets,
            sharedFiles = files,
            connectedPeerUrl = peer.peerUrl,
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
        networkSnapshot.value = readNetworkSnapshot()
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
            statusBanner.value = "Local Drop Server stopped."
        } else {
            DropServerService.start(getApplication())
            val ip = networkSnapshot.value.localIp
            statusBanner.value = if (ip != null) {
                "Local Drop Server live at http://$ip:${DropHubCoordinator.boundPort.value}"
            } else {
                "Local Drop Server started. Connect to Wi-Fi or enable Mobile Hotspot to share."
            }
        }
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

    // --- Native In-App Peer Client Mode (QR Scan / akit://drop Deep Link) ---

    fun connectToPeerUrl(rawUrl: String) {
        val normalized = DropUrlParser.normalizeDropTargetUrl(rawUrl)
        if (normalized == null) {
            statusBanner.value = "Invalid A-Kit Drop URL."
            return
        }
        peerPollingJob?.cancel()
        peerState.value = PeerClientState(
            peerUrl = normalized,
            isConnecting = true,
            isConnected = false,
        )
        statusBanner.value = "Connecting to peer at $normalized..."

        peerPollingJob = viewModelScope.launch(Dispatchers.IO) {
            var consecutiveFailures = 0
            while (isActive) {
                val result = DropPeerClient.fetchState(normalized)
                if (result.isSuccess) {
                    val payload = result.getOrThrow()
                    val firstConnect = !peerState.value.isConnected
                    consecutiveFailures = 0
                    peerState.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = true,
                            snippets = payload.snippets,
                            files = payload.files,
                        )
                    }
                    if (firstConnect) {
                        statusBanner.value = "Connected natively to $normalized"
                    }
                } else {
                    consecutiveFailures += 1
                    if (consecutiveFailures >= 3) {
                        peerState.update { it.copy(isConnecting = false, isConnected = false) }
                        statusBanner.value = "Cannot reach $normalized. Ensure both devices are on the same Wi-Fi or Hotspot."
                    }
                }
                delay(2_500L)
            }
        }
    }

    fun disconnectPeer() {
        peerPollingJob?.cancel()
        peerPollingJob = null
        peerState.value = PeerClientState()
        statusBanner.value = "Disconnected from remote Drop peer."
    }

    fun sendSnippetToPeer(text: String) {
        val url = peerState.value.peerUrl ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val res = DropPeerClient.sendSnippet(url, trimmed)
            if (res.isSuccess) {
                statusBanner.value = "Snippet sent to peer."
                DropPeerClient.fetchState(url).getOrNull()?.let { payload ->
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
        viewModelScope.launch(Dispatchers.IO) {
            statusBanner.value = "Uploading ${uris.size} item(s) to peer..."
            var successCount = 0
            uris.forEach { uri ->
                if (DropPeerClient.uploadUri(getApplication(), url, uri).isSuccess) {
                    successCount++
                }
            }
            if (successCount > 0) {
                statusBanner.value = "Uploaded $successCount item(s) to peer's Downloads/A-Kit."
                DropPeerClient.fetchState(url).getOrNull()?.let { payload ->
                    peerState.update { it.copy(snippets = payload.snippets, files = payload.files) }
                }
            } else {
                statusBanner.value = "Upload failed. Check that the peer server is still online."
            }
        }
    }

    fun downloadPeerFile(item: PeerFileItem) {
        val url = peerState.value.peerUrl ?: return
        viewModelScope.launch(Dispatchers.IO) {
            peerState.update { it.copy(downloadingFileId = item.id) }
            val result = DropPeerClient.downloadFileToDownloads(getApplication(), url, item)
            peerState.update { it.copy(downloadingFileId = null) }
            statusBanner.value = if (result.isSuccess) {
                "Saved \"${item.fileName}\" to Downloads/A-Kit."
            } else {
                "Failed to download \"${item.fileName}\"."
            }
        }
    }

    private fun readNetworkSnapshot(): NetworkSnapshot {
        val wifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
        val wifiConnected = runCatching {
            val activeNetwork = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }.getOrDefault(false)
        val ip = LocalNetworkUtils.detectLocalIpv4Address()
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
