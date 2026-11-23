package com.example.feature.power

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.util.Locale

enum class TempUnit(val key: String, val symbol: String) {
    CELSIUS("c", "°C"),
    FAHRENHEIT("f", "°F");

    fun format(celsius: Float): String {
        val value = if (this == CELSIUS) celsius else (celsius * 9f / 5f) + 32f
        return String.format(Locale.US, "%.1f%s", value, symbol)
    }

    fun convertValue(celsius: Float): Float =
        if (this == CELSIUS) celsius else (celsius * 9f / 5f) + 32f

    companion object {
        fun fromKey(key: String?): TempUnit = entries.firstOrNull { it.key == key } ?: CELSIUS
    }
}

enum class PowerAccentPreset(
    val key: String,
    val label: String,
    val composeColor: Color,
    val argbInt: Int,
) {
    CYAN("cyan", "Cyan", Color(0xFF00E5FF), 0xFF00E5FF.toInt()),
    MINT("mint", "Mint", Color(0xFF00E676), 0xFF00E676.toInt()),
    AMBER("amber", "Amber", Color(0xFFFFCA28), 0xFFFFCA28.toInt()),
    CORAL("coral", "Coral", Color(0xFFFF7A70), 0xFFFF7A70.toInt()),
    WHITE("white", "Pure", Color(0xFFF1F5F9), 0xFFF1F5F9.toInt());

    companion object {
        fun fromKey(key: String?): PowerAccentPreset = entries.firstOrNull { it.key == key } ?: CYAN
    }
}

enum class AodVisualStyle(val key: String, val title: String, val subtitle: String) {
    SPEEDOMETER_RING("ring", "Segmented Ring", "60-segment precision wattage arc"),
    PLASMA_WAVE("wave", "Energy Wave", "Animated liquid charge horizon"),
    MINIMAL_DIGITAL("digital", "Ultra Minimal", "Low-pixel OLED mono readout");

    companion object {
        fun fromKey(key: String?): AodVisualStyle =
            entries.firstOrNull { it.key == key } ?: SPEEDOMETER_RING
    }
}

@Immutable
data class PowerSettings(
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val dualCellMode: Boolean = false,
    val designCapacityMah: Int = 5000,
    val hudEnabled: Boolean = false,
    val hudOpacity: Float = 0.88f,
    val hudAccent: PowerAccentPreset = PowerAccentPreset.CYAN,
    val aodStyle: AodVisualStyle = AodVisualStyle.SPEEDOMETER_RING,
    val aodAccent: PowerAccentPreset = PowerAccentPreset.CYAN,
    val highChargeAlarmEnabled: Boolean = false,
    val highChargeThreshold: Int = 85,
    val lowBatteryAlarmEnabled: Boolean = false,
    val lowBatteryThreshold: Int = 20,
    val overheatAlarmEnabled: Boolean = false,
    val overheatThresholdC: Int = 42,
) {
    val activeAlarmCount: Int
        get() = listOf(highChargeAlarmEnabled, lowBatteryAlarmEnabled, overheatAlarmEnabled).count { it }

    val requiresBackgroundService: Boolean
        get() = hudEnabled || activeAlarmCount > 0
}

@Immutable
data class TelemetrySample(
    val timestampMs: Long,
    val watts: Float,
    val tempCelsius: Float,
    val levelPercent: Int,
    val currentMa: Int,
)

@Immutable
data class LevelBracketStat(
    val id: String,
    val label: String,
    val minPercent: Int,
    val maxPercent: Int,
    val avgWatts: Float,
    val peakWatts: Float,
    val sampleCount: Int,
)

@Immutable
data class ChargingSessionRecord(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val startPercent: Int,
    val endPercent: Int,
    val peakWatts: Float,
    val avgWatts: Float,
    val maxTempCelsius: Float,
    val isDualCell: Boolean,
)

@Immutable
data class PowerTelemetryState(
    val levelPercent: Int = 0,
    val exactLevelPercent: Float = 0f,
    val temperatureCelsius: Float = 0f,
    val rawVoltageMv: Int = 0,
    val effectiveVoltageMv: Int = 0,
    val currentNowMa: Int = 0,
    val wattage: Float = 0f,
    val peakSessionWatts: Float = 0f,
    val isCharging: Boolean = false,
    val isAutoDualCellDetected: Boolean = false,
    val effectiveDualCellActive: Boolean = false,
    val statusLabel: String = "Reading",
    val pluggedLabel: String = "Battery",
    val estimatedTimeLabel: String = "Estimating...",
    val estimatedTimeShort: String = "--",
    val healthLabel: String = "Good",
    val technology: String = "Li-ion",
    val estimatedCapacityMah: Int? = null,
    val healthEstimatePercent: Int? = null,
    val hasLiveReading: Boolean = false,
    val sessionSamples: List<TelemetrySample> = emptyList(),
    val bracketStats: List<LevelBracketStat> = defaultBracketStats(),
    val sessionHistory: List<ChargingSessionRecord> = emptyList(),
    val settings: PowerSettings = PowerSettings(),
    val overlayPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val isGuardServiceRunning: Boolean = false,
    val activeAlertMessage: String? = null,
)

fun defaultBracketStats(): List<LevelBracketStat> = listOf(
    LevelBracketStat("b0", "0-20%", 0, 20, 0f, 0f, 0),
    LevelBracketStat("b1", "20-50%", 21, 50, 0f, 0f, 0),
    LevelBracketStat("b2", "50-80%", 51, 80, 0f, 0f, 0),
    LevelBracketStat("b3", "80-100%", 81, 100, 0f, 0f, 0),
)
