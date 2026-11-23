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
import kotlinx.coroutines.Job
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
 *
 * Battery-optimized: hardware polling runs at 1500ms ONLY while the Power screen is visible,
 * Charging AOD is active, or the Floating HUD / Protection Guard service is running.
 */
object PowerTelemetryEngine {

    private const val POLLING_INTERVAL_MS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(PowerTelemetryState())
    val state: StateFlow<PowerTelemetryState> = _state.asStateFlow()

    @Volatile
    private var initialized = false
    private var appContext: Context? = null
    private var preferencesStore: PowerPreferencesStore? = null
    private var batteryManager: BatteryManager? = null

    @Volatile
    private var isPowerScreenVisible = false

    @Volatile
    private var isAodVisible = false

    @Volatile
    private var isServiceActive = false

    @Volatile
    private var autoLaunchAodEnabled = false

    private var pollingJob: Job? = null
    private var lastIntent: Intent? = null
    private var lastBroadcastWasCharging: Boolean? = null

    // Live decimal battery percentage coulomb-counter interpolation state
    private var lastOsLevelPercent: Int = -1
    private var smoothExactPercent: Float = 0f
    private var lastIntegrationMs: Long = 0L

    // Active charging session analytics
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
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                val nowCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL ||
                    plugged != 0

                val prevCharging = lastBroadcastWasCharging
                lastBroadcastWasCharging = nowCharging
                lastIntent = intent
                processReading(intent, recordSample = true)

                // Auto-launch Charging AOD on fresh plug-in if enabled in Settings
                if (prevCharging == false && nowCharging && autoLaunchAodEnabled && !isAodVisible) {
                    val ctx = appContext
                    if (ctx != null) {
                        val aodIntent = Intent(ctx, ChargingAodActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        runCatching { ctx.startActivity(aodIntent) }
                    }
                }
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
            val plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            lastBroadcastWasCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                plugged != 0
            processReading(sticky, recordSample = true)
        }
        refreshPermissions()
        updatePollingState()
    }

    fun setPowerScreenVisible(visible: Boolean) {
        isPowerScreenVisible = visible
        if (visible) {
            pollOnceImmediately()
        }
        updatePollingState()
    }

    fun setAodVisible(visible: Boolean) {
        isAodVisible = visible
        if (visible) {
            pollOnceImmediately()
        }
        updatePollingState()
    }

    fun setServiceRunning(running: Boolean) {
        isServiceActive = running
        _state.update { it.copy(isGuardServiceRunning = running) }
        updatePollingState()
    }

    fun setAutoLaunchAodOnPlugIn(enabled: Boolean) {
        autoLaunchAodEnabled = enabled
    }

    @Synchronized
    private fun updatePollingState() {
        val shouldPoll = initialized && (isPowerScreenVisible || isAodVisible || isServiceActive)
        if (shouldPoll) {
            if (pollingJob?.isActive == true) return
            pollingJob = scope.launch {
                while (isActive) {
                    delay(POLLING_INTERVAL_MS)
                    val ctx = appContext ?: continue
                    val currentIntent = lastIntent
                        ?: ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    if (currentIntent != null) {
                        lastIntent = currentIntent
                        processReading(currentIntent, recordSample = true)
                    }
                }
            }
        } else {
            pollingJob?.cancel()
            pollingJob = null
        }
    }

    private fun pollOnceImmediately() {
        val ctx = appContext ?: return
        scope.launch {
            val currentIntent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: lastIntent
            if (currentIntent != null) {
                lastIntent = currentIntent
                processReading(currentIntent, recordSample = false)
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
            isDualCell = snapshot.effectiveDualCellActive,
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

        val rawVoltageExtra = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val normalizedVoltageMv = when {
            rawVoltageExtra in 1..20 -> rawVoltageExtra * 1000
            rawVoltageExtra > 100_000 -> rawVoltageExtra / 1000 // microvolts on rare kernels
            else -> rawVoltageExtra
        }

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

        // Read hardware current with floating-point precision (normalize µA vs mA across OEMs)
        val bm = batteryManager
        val rawCurrentNow = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE && it != 0 }
        val rawCurrentAvg = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            ?.takeIf { it != Int.MIN_VALUE && it != 0 }
        val rawCurrent = rawCurrentNow ?: rawCurrentAvg ?: 0

        val absMaFloat = when {
            abs(rawCurrent) >= 10_000 -> abs(rawCurrent) / 1000f
            else -> abs(rawCurrent).toFloat()
        }
        val absMaInt = absMaFloat.roundToInt()
        val signedMa = if (isCharging) absMaInt else -absMaInt

        val currentSettings = _state.value.settings
        val designMah = currentSettings.designCapacityMah.coerceAtLeast(1000)

        // Read hardware Charge Counter (µAh) for capacity health & 2S auto-detection
        val chargeCounterUah = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it != Int.MIN_VALUE && it > 0 }

        val rawEstimatedFullMah = if (chargeCounterUah != null && percent >= 5) {
            val currentChargeMah = chargeCounterUah / 1000.0
            (currentChargeMah / (percent / 100.0)).roundToInt().takeIf { it in 800..16000 }
        } else {
            null
        }

        // Smart Dual-Cell (2S) auto-detection on first run:
        // Detects 2S series packs when single-cell voltage is reported (~3.0-5.4V) AND either:
        //  (a) Charge counter yields ~half the design capacity (< 62% of designMah), OR
        //  (b) High-speed fast charging (>15W raw or effective 2S charge pump on AC) is detected on first run.
        // Never doubles if the kernel already reports series pack voltage (> 5.4V),
        // and always respects manual user toggles once overridden.
        val kernelReportsSingleCellVoltage = normalizedVoltageMv in 3000..5400
        val rawSingleCellWatts = (normalizedVoltageMv / 1000f) * (absMaFloat / 1000f)
        val halfCapacitySignature = rawEstimatedFullMah != null &&
            rawEstimatedFullMah < (designMah * 0.62f).roundToInt()
        val fastChargePumpSignature = isCharging &&
            plugged == BatteryManager.BATTERY_PLUGGED_AC &&
            (rawSingleCellWatts > 15f || (rawSingleCellWatts * 2f > 15f && absMaFloat >= 1750f))

        val autoDualCellDetected = kernelReportsSingleCellVoltage &&
            (halfCapacitySignature || fastChargePumpSignature)

        // Automatically enable Dual-Cell calibration on first run if not manually overridden
        if (autoDualCellDetected && !currentSettings.dualCellUserOverridden && !currentSettings.dualCellMode) {
            updateSettings { prevSettings ->
                if (!prevSettings.dualCellUserOverridden && !prevSettings.dualCellMode) {
                    prevSettings.copy(dualCellMode = true)
                } else {
                    prevSettings
                }
            }
        }

        val userOrAutoDualCell = if (currentSettings.dualCellUserOverridden) {
            currentSettings.dualCellMode
        } else {
            currentSettings.dualCellMode || autoDualCellDetected
        }

        val effectiveDualCell = userOrAutoDualCell && kernelReportsSingleCellVoltage
        val cellMultiplier = if (effectiveDualCell) 2 else 1
        val effectiveVoltageMv = normalizedVoltageMv * cellMultiplier

        // Accurate real-time pack wattage: Effective Pack Voltage (V) * Current (A)
        val watts = (effectiveVoltageMv / 1000f) * (absMaFloat / 1000f)

        val estimatedMah = when {
            rawEstimatedFullMah == null -> null
            effectiveDualCell && rawEstimatedFullMah < (designMah * 0.62f).roundToInt() ->
                (rawEstimatedFullMah * 2).coerceIn(1000, 16000)
            else -> rawEstimatedFullMah.coerceIn(1000, 16000)
        }
        val effectiveFullMah = (estimatedMah ?: designMah).toFloat().coerceAtLeast(1500f)
        val healthPct = estimatedMah?.let { ((it * 100f) / designMah).roundToInt().coerceIn(40, 105) }

        val now = System.currentTimeMillis()

        // Smooth live decimal battery percentage via charge-counter + 2S-normalized coulomb interpolation
        val exactPercent = computeSmoothDecimalPercent(
            osPercent = percent,
            isCharging = isCharging,
            absMaFloat = absMaFloat,
            chargeCounterUah = chargeCounterUah,
            effectiveFullMah = effectiveFullMah,
            effectiveDualCell = effectiveDualCell,
            nowMs = now,
        )

        // Accurate Watt-Hour Energy Time Estimate (never double-counts 2S series current)
        val (estimateLong, estimateShort) = computeTimeEstimate(
            bm = bm,
            osPercent = percent,
            exactPercent = exactPercent,
            isCharging = isCharging,
            watts = watts,
            absMaFloat = absMaFloat,
            effectiveFullMah = effectiveFullMah,
            effectiveDualCell = effectiveDualCell,
        )

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
                exactLevelPercent = exactPercent,
                temperatureCelsius = tempC,
                rawVoltageMv = normalizedVoltageMv,
                effectiveVoltageMv = effectiveVoltageMv,
                currentNowMa = signedMa,
                wattage = watts,
                peakSessionWatts = max(prev.peakSessionWatts, watts),
                isCharging = isCharging,
                isAutoDualCellDetected = autoDualCellDetected,
                effectiveDualCellActive = effectiveDualCell,
                statusLabel = statusLabel,
                pluggedLabel = pluggedLabel,
                estimatedTimeLabel = estimateLong,
                estimatedTimeShort = estimateShort,
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

    private fun computeSmoothDecimalPercent(
        osPercent: Int,
        isCharging: Boolean,
        absMaFloat: Float,
        chargeCounterUah: Int?,
        effectiveFullMah: Float,
        effectiveDualCell: Boolean,
        nowMs: Long,
    ): Float {
        if (osPercent >= 100 && isCharging) {
            lastOsLevelPercent = 100
            smoothExactPercent = 100.00f
            lastIntegrationMs = nowMs
            return 100.00f
        }
        if (osPercent <= 0) {
            lastOsLevelPercent = 0
            smoothExactPercent = 0.00f
            lastIntegrationMs = nowMs
            return 0.00f
        }

        if (lastOsLevelPercent == -1 || lastIntegrationMs == 0L) {
            val initialFrac = estimateFractionalOffsetFromCoulomb(
                osPercent = osPercent,
                chargeCounterUah = chargeCounterUah,
                effectiveFullMah = effectiveFullMah,
                effectiveDualCell = effectiveDualCell,
            )
            lastOsLevelPercent = osPercent
            smoothExactPercent = (osPercent + initialFrac).coerceIn(0f, 99.99f)
            lastIntegrationMs = nowMs
            return smoothExactPercent
        }

        if (osPercent != lastOsLevelPercent) {
            val steppedUp = osPercent > lastOsLevelPercent
            lastOsLevelPercent = osPercent
            smoothExactPercent = if (steppedUp) {
                osPercent.toFloat() + 0.01f
            } else {
                (osPercent.toFloat() + 0.96f).coerceAtMost(99.99f)
            }
            lastIntegrationMs = nowMs
            return smoothExactPercent
        }

        val dtMs = (nowMs - lastIntegrationMs).coerceIn(0L, 15_000L)
        lastIntegrationMs = nowMs
        if (dtMs <= 0L) return smoothExactPercent

        // Normalize current to single-cell-equivalent capacity (in 2S, series current charges BOTH cells simultaneously)
        val dtHours = dtMs / 3_600_000f
        val baseCurrentMa = if (absMaFloat > 20f) absMaFloat else if (isCharging) 350f else 180f
        val equivalentSingleCellMa = if (effectiveDualCell) baseCurrentMa * 2f else baseCurrentMa
        val deltaPct = (equivalentSingleCellMa * dtHours / effectiveFullMah) * 100f

        val lowerBound = osPercent.toFloat()
        val upperBound = (osPercent + 0.99f).coerceAtMost(99.99f)

        smoothExactPercent = if (isCharging) {
            (smoothExactPercent + deltaPct).coerceIn(lowerBound, upperBound)
        } else {
            (smoothExactPercent - deltaPct).coerceIn(lowerBound, upperBound)
        }
        return smoothExactPercent
    }

    private fun estimateFractionalOffsetFromCoulomb(
        osPercent: Int,
        chargeCounterUah: Int?,
        effectiveFullMah: Float,
        effectiveDualCell: Boolean,
    ): Float {
        if (chargeCounterUah == null || effectiveFullMah <= 0f) return 0.42f
        val rawMah = chargeCounterUah / 1000f
        val adjustedMah = if (effectiveDualCell && rawMah < effectiveFullMah * 0.62f) rawMah * 2f else rawMah
        val rawPct = (adjustedMah / effectiveFullMah) * 100f
        val diff = rawPct - osPercent
        return if (diff in 0.0f..0.99f) diff else 0.35f
    }

    /**
     * Computes remaining charge/discharge time using pack Watt-Hours (Wh = mAh/1000 * 3.85V)
     * divided by true net pack power (W). This avoids the classic 2S Dual-Cell bug where
     * dividing single-cell-equivalent mAh (e.g. 5000mAh) by series current (e.g. 960mA at 8.1V)
     * mistakenly doubles the estimated time to full.
     */
    private fun computeTimeEstimate(
        bm: BatteryManager?,
        osPercent: Int,
        exactPercent: Float,
        isCharging: Boolean,
        watts: Float,
        absMaFloat: Float,
        effectiveFullMah: Float,
        effectiveDualCell: Boolean,
    ): Pair<String, String> {
        val packEnergyWh = (effectiveFullMah / 1000f) * NOMINAL_CELL_VOLTAGE_V

        if (isCharging) {
            if (osPercent >= 100) return "Fully Charged" to "Full"

            val totalMinutes = if (watts >= 1.2f) {
                val remainingFraction = ((100f - exactPercent).coerceAtLeast(0.5f)) / 100f
                val remainingWh = packEnergyWh * remainingFraction
                // Gentle lithium-ion CV taper factor above 80%
                val taperFactor = when {
                    osPercent >= 90 -> 1.22f
                    osPercent >= 80 -> 1.12f
                    else -> 1.03f
                }
                ((remainingWh / watts) * 60f * taperFactor).roundToInt().coerceIn(1, 999)
            } else {
                val osMillis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { bm?.computeChargeTimeRemaining() ?: -1L }.getOrDefault(-1L)
                } else {
                    -1L
                }
                if (osMillis > 60_000L) {
                    (osMillis / 60_000L).toInt().coerceIn(1, 999)
                } else {
                    val equivMa = (if (effectiveDualCell) absMaFloat * 2f else absMaFloat).coerceAtLeast(200f)
                    val remainingMah = effectiveFullMah * ((100f - exactPercent).coerceAtLeast(0.5f) / 100f)
                    ((remainingMah / equivMa) * 60f * 1.05f).roundToInt().coerceIn(1, 999)
                }
            }

            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            val shortStr = when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                hours > 0 -> "${hours}h"
                else -> "$mins min"
            }
            return "$shortStr Estimate" to shortStr
        } else {
            val remainingFraction = (exactPercent.coerceAtLeast(1f)) / 100f
            val totalMinutes = if (watts >= 0.4f) {
                val remainingWh = packEnergyWh * remainingFraction
                ((remainingWh / watts) * 60f).roundToInt().coerceIn(5, 72 * 60)
            } else {
                val equivDrawMa = (if (effectiveDualCell) absMaFloat * 2f else absMaFloat).coerceAtLeast(160f)
                val remainingMah = effectiveFullMah * remainingFraction
                ((remainingMah / equivDrawMa) * 60f).roundToInt().coerceIn(5, 72 * 60)
            }
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            val shortStr = when {
                hours >= 24 -> "${hours}h"
                hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                hours > 0 -> "${hours}h"
                else -> "${mins}m"
            }
            return "$shortStr Remaining" to shortStr
        }
    }

    private const val NOMINAL_CELL_VOLTAGE_V = 3.85f
}
