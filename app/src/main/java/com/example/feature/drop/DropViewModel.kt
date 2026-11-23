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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

private data class NetworkSnapshot(
    val isWifiEnabled: Boolean,
    val isWifiConnected: Boolean,
    val localIp: String?,
)

class DropViewModel(application: Application) : AndroidViewModel(application) {

    private val wifiManager =
        application.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager =
        application.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val networkSnapshot = MutableStateFlow(readNetworkSnapshot())
    private val statusBanner = MutableStateFlow<String?>(null)
    private val deviceName = resolveDeviceName()

    private val serverMetaFlow = combine(
        DropHubCoordinator.isServerRunning,
        DropHubCoordinator.boundPort,
        DropHubCoordinator.activePeerCount,
    ) { running, port, peers -> Triple(running, port, peers) }

    val uiState: StateFlow<DropUiState> = combine(
        serverMetaFlow,
        networkSnapshot,
        DropHubCoordinator.snippets,
        DropHubCoordinator.sharedFiles,
        statusBanner,
    ) { (running, port, peers), net, snippets, files, banner ->
        DropUiState(
            isServerRunning = running,
            isWifiEnabled = net.isWifiEnabled,
            isWifiConnected = net.isWifiConnected,
            localIpAddress = net.localIp,
            serverPort = port,
            deviceBeaconName = deviceName,
            activePeerCount = peers,
            snippets = snippets,
            sharedFiles = files,
            statusBanner = banner,
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
    }

    fun toggleDropServer() {
        refreshNetwork()
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
            "Snippet staged. Start the Local Drop Server so nearby browsers can grab it."
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
        runCatching { getApplication<Application>().unregisterReceiver(wifiStateReceiver) }
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }
}
