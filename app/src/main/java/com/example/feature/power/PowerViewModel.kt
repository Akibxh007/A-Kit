package com.example.feature.power

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PowerViewModel(application: Application) : AndroidViewModel(application) {

    val uiState: StateFlow<PowerTelemetryState> = PowerTelemetryEngine.state

    init {
        PowerTelemetryEngine.ensureInitialized(application)

        // Keep Foreground Service synchronized whenever HUD or Protection Alarms settings change
        viewModelScope.launch {
            PowerTelemetryEngine.state
                .map { it.settings }
                .distinctUntilChanged()
                .collect { settings ->
                    PowerMonitorService.syncServiceState(getApplication(), settings)
                }
        }
    }

    fun refreshPermissions() {
        PowerTelemetryEngine.refreshPermissions()
    }

    fun setPowerScreenVisible(visible: Boolean) {
        PowerTelemetryEngine.setPowerScreenVisible(visible)
    }

    fun toggleTempUnit() {
        PowerTelemetryEngine.updateSettings {
            val next = if (it.tempUnit == TempUnit.CELSIUS) TempUnit.FAHRENHEIT else TempUnit.CELSIUS
            it.copy(tempUnit = next)
        }
    }

    fun setTempUnit(unit: TempUnit) {
        PowerTelemetryEngine.updateSettings { it.copy(tempUnit = unit) }
    }

    fun setDualCellMode(enabled: Boolean) {
        PowerTelemetryEngine.updateSettings {
            it.copy(dualCellMode = enabled, dualCellUserOverridden = true)
        }
    }

    fun setDesignCapacityMah(capacityMah: Int) {
        PowerTelemetryEngine.updateSettings {
            it.copy(designCapacityMah = capacityMah.coerceIn(1500, 15000))
        }
    }

    fun resetAutoDesignCapacity() {
        val detected = PowerPreferencesStore.detectHardwareDesignCapacity(getApplication())
        setDesignCapacityMah(detected)
    }

    fun setHudEnabled(enabled: Boolean) {
        val app = getApplication<Application>()
        if (enabled && !Settings.canDrawOverlays(app)) {
            PowerTelemetryEngine.refreshPermissions()
            return
        }
        PowerTelemetryEngine.updateSettings { it.copy(hudEnabled = enabled) }
    }

    fun setHudOpacity(opacity: Float) {
        PowerTelemetryEngine.updateSettings { it.copy(hudOpacity = opacity.coerceIn(0.4f, 1.0f)) }
    }

    fun setHudAccent(accent: PowerAccentPreset) {
        PowerTelemetryEngine.updateSettings { it.copy(hudAccent = accent) }
    }

    fun setAodStyle(style: AodVisualStyle) {
        PowerTelemetryEngine.updateSettings { it.copy(aodStyle = style) }
    }

    fun setAodAccent(accent: PowerAccentPreset) {
        PowerTelemetryEngine.updateSettings { it.copy(aodAccent = accent) }
    }

    fun setHighChargeAlarm(enabled: Boolean) {
        PowerTelemetryEngine.updateSettings { it.copy(highChargeAlarmEnabled = enabled) }
    }

    fun setHighChargeThreshold(percent: Int) {
        PowerTelemetryEngine.updateSettings { it.copy(highChargeThreshold = percent.coerceIn(60, 100)) }
    }

    fun setLowBatteryAlarm(enabled: Boolean) {
        PowerTelemetryEngine.updateSettings { it.copy(lowBatteryAlarmEnabled = enabled) }
    }

    fun setLowBatteryThreshold(percent: Int) {
        PowerTelemetryEngine.updateSettings { it.copy(lowBatteryThreshold = percent.coerceIn(5, 45)) }
    }

    fun setOverheatAlarm(enabled: Boolean) {
        PowerTelemetryEngine.updateSettings { it.copy(overheatAlarmEnabled = enabled) }
    }

    fun setOverheatThreshold(celsius: Int) {
        PowerTelemetryEngine.updateSettings { it.copy(overheatThresholdC = celsius.coerceIn(35, 52)) }
    }

    fun saveSessionSnapshot() {
        PowerTelemetryEngine.saveCurrentSessionSnapshot()
    }

    fun clearLocalAnalytics() {
        PowerTelemetryEngine.clearLocalAnalytics()
    }

    fun dismissActiveAlert() {
        PowerTelemetryEngine.setActiveAlert(null)
    }

    fun testAlarmSoundAndVibration() {
        PowerMonitorService.triggerAlarmSoundAndVibration(getApplication())
    }

    fun buildOverlayPermissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${getApplication<Application>().packageName}"),
    )

    fun buildChargingAodIntent(): Intent = Intent(
        getApplication(),
        ChargingAodActivity::class.java,
    )
}
