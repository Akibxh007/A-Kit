package com.example.feature.drop

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 100% offline Wi-Fi Hotspot, Wi-Fi Socket Binding & Peer Auto-Connect manager.
 *
 * - **Sender**: Launches an Android Local-Only Hotspot (`WifiManager.startLocalOnlyHotspot`)
 *   requiring zero internet or mobile data, returning its temporary SSID & WPA2 password to embed
 *   in A-Kit's QR code.
 * - **Receiver**: Joins a scanned hotspot automatically on Android 10+ via `WifiNetworkSpecifier`
 *   inside a ViewModel-scoped coroutine, keeping the `ConnectivityManager.NetworkCallback` alive
 *   across the entire session until explicit disconnect or ViewModel teardown.
 * - **Explicit Wi-Fi Socket Routing**: [findActiveWifiNetwork] resolves any active `TRANSPORT_WIFI`
 *   [Network] so local LAN HTTP requests (`http://192.168.x.x:8080`) always traverse Wi-Fi/Hotspot
 *   rather than Mobile Data when the Wi-Fi link has no internet access.
 */
object DropOfflineWifiManager {

    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    var activeBoundNetwork: Network? = null
        private set

    fun requiredHotspotPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
    }

    fun hasHotspotPermission(context: Context): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNearby = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ) == PackageManager.PERMISSION_GRANTED
            hasNearby && hasLocation
        } else {
            hasLocation
        }
    }

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { lm.isLocationEnabled }.getOrDefault(true)
        } else {
            runCatching {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(true)
        }
    }

    /**
     * Resolves the best active Wi-Fi [Network] for local LAN HTTP connections:
     * 1. Explicitly bound `WifiNetworkSpecifier` hotspot network if connected.
     * 2. Active default network if it carries `TRANSPORT_WIFI`.
     * 3. Any connected `TRANSPORT_WIFI` network from `ConnectivityManager.allNetworks`
     *    (critical when the phone is connected to an offline Portable Hotspot while Mobile Data
     *    remains Android's default internet route!).
     */
    @Suppress("DEPRECATION")
    fun findActiveWifiNetwork(context: Context): Network? {
        activeBoundNetwork?.let { return it }
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null

        val active = cm.activeNetwork
        if (active != null) {
            val caps = cm.getNetworkCapabilities(active)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                return active
            }
        }

        val allNets = runCatching { cm.allNetworks }.getOrNull().orEmpty()
        for (net in allNets) {
            val caps = runCatching { cm.getNetworkCapabilities(net) }.getOrNull()
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                return net
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun startLocalOnlyHotspot(
        context: Context,
        onStarted: (ssid: String, password: String) -> Unit,
        onStopped: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!hasHotspotPermission(context)) {
            onError("Nearby Wi-Fi & Location permissions are required to start an Offline Hotspot.")
            return
        }
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            onError("Wi-Fi hardware is unavailable on this device.")
            return
        }

        // Release any stale reservation first
        stopLocalOnlyHotspot()

        try {
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        hotspotReservation = reservation
                        val (ssid, pass) = extractHotspotCredentials(reservation)
                        onStarted(ssid, pass)
                    }

                    override fun onStopped() {
                        hotspotReservation = null
                        onStopped()
                    }

                    override fun onFailed(reason: Int) {
                        hotspotReservation = null
                        val message = when (reason) {
                            ERROR_TETHERING_DISALLOWED ->
                                "Offline Hotspot is restricted by device policy. Use Portable Hotspot in system settings instead."
                            ERROR_INCOMPATIBLE_MODE ->
                                "Turn off system Portable Hotspot first, or share directly over your active Portable Hotspot."
                            ERROR_NO_CHANNEL ->
                                "No Wi-Fi channel available right now. Toggle Wi-Fi or use Portable Hotspot."
                            else ->
                                "Could not start Offline Hotspot (code $reason). Ensure Location/GPS is ON or use Portable Hotspot."
                        }
                        onError(message)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (se: SecurityException) {
            onError("Grant Nearby Wi-Fi & Location permissions to start an Offline Hotspot.")
        } catch (t: Throwable) {
            onError("Offline Hotspot unavailable (${t.message ?: "use Portable Hotspot"}).")
        }
    }

    fun stopLocalOnlyHotspot() {
        runCatching { hotspotReservation?.close() }
        hotspotReservation = null
    }

    /**
     * Requests a `WifiNetworkSpecifier` connection inside a ViewModel-scoped coroutine.
     *
     * IMPORTANT: The registered [ConnectivityManager.NetworkCallback] is deliberately kept alive
     * after this function returns `Result.success(network)` (because Android immediately drops a
     * `WifiNetworkSpecifier` connection if its callback is unregistered). It is only released when
     * [disconnectBoundNetwork] is called on explicit disconnect or ViewModel teardown, or if the
     * requesting coroutine is cancelled before connecting.
     */
    suspend fun awaitHotspotConnection(
        context: Context,
        ssid: String,
        password: String?,
        timeoutMs: Int = 30_000,
    ): Result<Network> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Result.failure(
                IllegalStateException("Auto-join requires Android 10+. Copy the password and join \"$ssid\" in Wi-Fi Settings."),
            )
        }

        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return Result.failure(IllegalStateException("Connectivity service unavailable."))

        disconnectBoundNetwork(context)

        val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        if (!password.isNullOrBlank()) {
            specifierBuilder.setWpa2Passphrase(password)
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()

        return suspendCancellableCoroutine { cont ->
            val resumed = AtomicBoolean(false)
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    activeBoundNetwork = network
                    runCatching { cm.bindProcessToNetwork(network) }
                    if (resumed.compareAndSet(false, true) && cont.isActive) {
                        cont.resume(Result.success(network))
                    }
                }

                override fun onUnavailable() {
                    if (activeNetworkCallback === this) {
                        runCatching { cm.unregisterNetworkCallback(this) }
                        activeNetworkCallback = null
                    }
                    activeBoundNetwork = null
                    if (resumed.compareAndSet(false, true) && cont.isActive) {
                        cont.resume(
                            Result.failure(
                                IllegalStateException(
                                    "Hotspot prompt dismissed or unsupported on this ROM. Tap below to copy password & join \"$ssid\" manually.",
                                ),
                            ),
                        )
                    }
                }

                override fun onLost(network: Network) {
                    if (activeBoundNetwork == network) {
                        activeBoundNetwork = null
                        runCatching { cm.bindProcessToNetwork(null) }
                    }
                }
            }

            activeNetworkCallback = callback
            cont.invokeOnCancellation {
                // Only tear down if the coroutine was cancelled BEFORE the network connected
                if (activeBoundNetwork == null && activeNetworkCallback === callback) {
                    runCatching { cm.unregisterNetworkCallback(callback) }
                    activeNetworkCallback = null
                }
            }

            try {
                cm.requestNetwork(request, callback, timeoutMs)
            } catch (t: Throwable) {
                if (activeNetworkCallback === callback) {
                    activeNetworkCallback = null
                }
                if (resumed.compareAndSet(false, true) && cont.isActive) {
                    cont.resume(
                        Result.failure(
                            IllegalStateException(
                                "Auto-join unavailable (${t.message ?: "OEM restriction"}). Copy password & join \"$ssid\" in Wi-Fi Settings.",
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun disconnectBoundNetwork(context: Context) {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        activeNetworkCallback?.let { cb ->
            runCatching { cm?.unregisterNetworkCallback(cb) }
        }
        activeNetworkCallback = null
        activeBoundNetwork = null
        runCatching { cm?.bindProcessToNetwork(null) }
    }

    private fun extractHotspotCredentials(
        reservation: WifiManager.LocalOnlyHotspotReservation,
    ): Pair<String, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val softAp = reservation.softApConfiguration
            val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                softAp.wifiSsid?.bytes?.toString(Charsets.UTF_8)
                    ?.removeSurrounding("\"")
                    ?.takeIf { it.isNotBlank() }
                    ?: @Suppress("DEPRECATION") softAp.ssid?.removeSurrounding("\"").orEmpty()
            } else {
                @Suppress("DEPRECATION") softAp.ssid?.removeSurrounding("\"").orEmpty()
            }
            val passphrase = softAp.passphrase.orEmpty()
            if (ssid.isNotBlank()) {
                return ssid to passphrase
            }
        }
        @Suppress("DEPRECATION")
        val legacyConfig = reservation.wifiConfiguration
        val legacySsid = legacyConfig?.SSID?.removeSurrounding("\"").orEmpty().ifBlank { "A-Kit_Hotspot" }
        val legacyPass = legacyConfig?.preSharedKey?.removeSurrounding("\"").orEmpty()
        return legacySsid to legacyPass
    }
}
