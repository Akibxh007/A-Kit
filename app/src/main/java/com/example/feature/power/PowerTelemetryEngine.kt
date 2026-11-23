package com.example.feature.power

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared local hardware telemetry engine used by PowerViewModel, ChargingAodActivity,
 * and PowerMonitorService so every UI surface and overlay reads identical live data.
 */
object PowerTelemetryEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(PowerTelemetryState())
    val state: StateFlow<PowerTelemetryState> = _state.asStateFlow()

    @Volatile
    private var initialized = false
    private var appContext: Context? = null
    private var preferencesStore: PowerPreferencesStore? = null
    private var batteryManager: BatteryManager? = null

    private var lastIntent: Intent? = null
    private var activeSessionStartMs: Long = 0L
    private var activeSessionStartPercent: Int = 0
    private var activeSessionMaxTempC: Float = 0f
    private var activeSessionWattSum: Float = 0f
    private var activeSessionSampleCount: Int = 0
    private var wasChargingSession: Boolean = false
    private var samplesSincePersist: Int = 0

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                lastIntent = intent
                processReading(intent, recordSample = true)
            }
        }
    }

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) {
            refreshPermissions()
            return
        }
        val app = context.applicationContext
        appContext = app
        val store = PowerPreferencesStore(app)
        preferencesStore = store
        batteryManager = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        initialized = true

        // Collect persisted Power settings
        scope.launch {
            store.settingsFlow.collect { settings ->
                _state.update { it.copy(settings = settings) }
                lastIntent?.let { processReading(it, recordSample = false) }
            }
        }

        // Collect persisted session history
        scope.launch {
            store.sessionHistoryFlow.collect { history ->
                _state.update { it.copy(sessionHistory = history) }
            }
        }

        // Collect persisted bracket stats
        scope.launch {
            store.bracketStatsFlow.collect { savedBrackets ->
                _state.update { current ->
                    val merged = current.bracketStats.map { live ->
                        val persisted = savedBrackets.firstOrNull { it.id == live.id }
                        if (persisted != null && persisted.sampleCount > live.sampleCount) persisted else live
                    }
                    current.copy(bracketStats = merged)
                }
            }
        }

        val sticky = app.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) {
            lastIntent = sticky
            processReading(sticky, recordSample = true)
        }
        refreshPermissions()

        // Smooth 2-second hardware polling loop for live wattage & thermal curves
        scope.launch {
            while (isActive) {
                delay(2_000L)
                val currentIntent = lastIntent
                    ?: app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (currentIntent != null) {
                    lastIntent = currentIntent
                    processReading(currentIntent, recordSample = true)
                }
            }
        }
    }

    fun getStore(context: Context): PowerPreferencesStore {
        ensureInitialized(context)
        return preferencesStore ?: PowerPreferencesStore(context.applicationContext)
    }

    fun refreshPermissions() {
        val ctx = appContext ?: return
        val overlayGranted = Settings.canDrawOverlays(ctx)
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        _state.update {
            it.copy(
                overlayPermissionGranted = overlayGranted,
                notificationPermissionGranted = notifGranted,
            )
        }
    }

    fun setServiceRunning(running: Boolean) {
        _state.update { it.copy(isGuardServiceRunning = running) }
    }

    fun setActiveAlert(message: String?) {
        _state.update { it.copy(activeAlertMessage = message) }
    }

    fun updateSettings(transform: (PowerSettings) -> PowerSettings) {
        val store = preferencesStore ?: return
        scope.launch {
            store.updateSettings(transform)
        }
    }

    fun saveCurrentSessionSnapshot() {
        val snapshot = _state.value
        val store = preferencesStore ?: return
        if (!snapshot.hasLiveReading) return

        val now = System.currentTimeMillis()
        val samples = snapshot.sessionSamples
        val startMs = if (activeSessionStartMs > 0L) activeSessionStartMs else (samples.firstOrNull()?.timestampMs ?: now)
        val startPct = if (activeSessionStartMs > 0L) activeSessionStartPercent else (samples.firstOrNull()?.levelPercent ?: snapshot.levelPercent)
        val avgW = if (activeSessionSampleCount > 0) {
            activeSessionWattSum / activeSessionSampleCount
        } else {
            snapshot.wattage
        }
        val peakW = max(snapshot.peakSessionWatts, snapshot.wattage)
        val maxTemp = max(activeSessionMaxTempC, snapshot.temperatureCelsius)

        val record = ChargingSessionRecord(
            id = "sess_$now",
            startedAtMs = startMs,
            endedAtMs = now,
            startPercent = startPct,
            endPercent = snapshot.levelPercent,
            peakWatts = peakW,
            avgWatts = avgW,
            maxTempCelsius = maxTemp,
            isDualCell = snapshot.settings.dualCellMode,
        )
        scope.launch {
            store.saveSession(record)
        }
    }

    fun clearLocalAnalytics() {
        val store = preferencesStore ?: return
        activeSessionStartMs = System.currentTimeMillis()
        activeSessionStartPercent = _state.value.levelPercent
        activeSessionMaxTempC = _state.value.temperatureCelsius
        activeSessionWattSum = 0f
        activeSessionSampleCount = 0
        _state.update {
            it.copy(
                peakSessionWatts = it.wattage,
                sessionSamples = it.sessionSamples.takeLast(1),
                bracketStats = defaultBracketStats(),
                sessionHistory = emptyList(),
            )
        }
        scope.launch {
            store.clearSessions()
        }
    }

    private fun processReading(intent: Intent, recordSample: Boolean) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) ((level * 100) / scale).coerceIn(0, 100) else 0

        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val tempC = tempTenths / 10f

        val rawVoltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val normalizedVoltageMv = if (rawVoltageMv in 1..20) rawVoltageMv * 1000 else rawVoltageMv
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?.takeIf { it.isNotBlank() } ?: "Li-ion"

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0

        val statusLabel = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Idle"
            else -> "Standby"
        }

        val pluggedLabel = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }

        val healthLabel = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Degraded"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Volt"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        // Read hardware current (normalize microamps vs milliamps across OEMs)
        val bm = batteryManager
        val rawCurrent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE }
            ?: bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
                ?.takeIf { it != Int.MIN_VALUE }
            ?: 0

        val absMa = when {
            abs(rawCurrent) >= 10_000 -> abs(rawCurrent) / 1000
            else -> abs(rawCurrent)
        }
        val signedMa = if (isCharging) absMa else -absMa

        val currentSettings = _state.value.settings
        val cellMultiplier = if (currentSettings.dualCellMode) 2 else 1
        val effectiveVoltageMv = normalizedVoltageMv * cellMultiplier
        val watts = (effectiveVoltageMv / 1000f) * (absMa / 1000f)

        // Estimated full capacity from hardware Charge Counter (µAh)
        val chargeCounterUah = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it != Int.MIN_VALUE && it > 0 }
        val estimatedMah = if (chargeCounterUah != null && percent >= 5) {
            val currentChargeMah = chargeCounterUah / 1000.0
            val estimatedFull = (currentChargeMah / (percent / 100.0)).roundToInt()
            estimatedFull.takeIf { it in 1000..15000 }
        } else {
            null
        }

        val designMah = currentSettings.designCapacityMah.coerceAtLeast(1000)
        val healthPct = estimatedMah?.let { ((it * 100f) / designMah).roundToInt().coerceIn(40, 105) }

        val now = System.currentTimeMillis()

        // Track automatic session start/end transitions
        if (recordSample) {
            if (isCharging && !wasChargingSession) {
                activeSessionStartMs = now
                activeSessionStartPercent = percent
                activeSessionMaxTempC = tempC
                activeSessionWattSum = 0f
                activeSessionSampleCount = 0
                wasChargingSession = true
            } else if (!isCharging && wasChargingSession) {
                if (activeSessionSampleCount >= 3) {
                    saveCurrentSessionSnapshot()
                }
                wasChargingSession = false
            }

            if (activeSessionStartMs == 0L) {
                activeSessionStartMs = now
                activeSessionStartPercent = percent
            }
            activeSessionMaxTempC = max(activeSessionMaxTempC, tempC)
            activeSessionWattSum += watts
            activeSessionSampleCount += 1
        }

        _state.update { prev ->
            val newSamples = if (recordSample) {
                val sample = TelemetrySample(
                    timestampMs = now,
                    watts = watts,
                    tempCelsius = tempC,
                    levelPercent = percent,
                    currentMa = signedMa,
                )
                (prev.sessionSamples + sample).takeLast(60)
            } else {
                prev.sessionSamples
            }

            val updatedBrackets = if (recordSample && (isCharging || watts > 0.1f)) {
                prev.bracketStats.map { bracket ->
                    if (percent in bracket.minPercent..bracket.maxPercent) {
                        val nextCount = bracket.sampleCount + 1
                        val nextAvg = ((bracket.avgWatts * bracket.sampleCount) + watts) / nextCount
                        bracket.copy(
                            avgWatts = nextAvg,
                            peakWatts = max(bracket.peakWatts, watts),
                            sampleCount = nextCount,
                        )
                    } else {
                        bracket
                    }
                }
            } else {
                prev.bracketStats
            }

            if (recordSample) {
                samplesSincePersist += 1
                if (samplesSincePersist >= 10) {
                    samplesSincePersist = 0
                    preferencesStore?.let { store ->
                        scope.launch { store.saveBracketStats(updatedBrackets) }
                    }
                }
            }

            prev.copy(
                levelPercent = percent,
                temperatureCelsius = tempC,
                rawVoltageMv = normalizedVoltageMv,
                effectiveVoltageMv = effectiveVoltageMv,
                currentNowMa = signedMa,
                wattage = watts,
                peakSessionWatts = max(prev.peakSessionWatts, watts),
                isCharging = isCharging,
                statusLabel = statusLabel,
                pluggedLabel = pluggedLabel,
                healthLabel = healthLabel,
                technology = tech,
                estimatedCapacityMah = estimatedMah,
                healthEstimatePercent = healthPct,
                hasLiveReading = true,
                sessionSamples = newSamples,
                bracketStats = updatedBrackets,
            )
        }
    }
}
