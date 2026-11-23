package com.example.feature.power

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.powerSuiteDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "akit_power_suite_prefs",
)

class PowerPreferencesStore(private val context: Context) {

    private object Keys {
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val DUAL_CELL = booleanPreferencesKey("dual_cell")
        val DESIGN_CAPACITY_MAH = intPreferencesKey("design_capacity_mah")
        val HUD_ENABLED = booleanPreferencesKey("hud_enabled")
        val HUD_OPACITY = floatPreferencesKey("hud_opacity")
        val HUD_ACCENT = stringPreferencesKey("hud_accent")
        val AOD_STYLE = stringPreferencesKey("aod_style")
        val AOD_ACCENT = stringPreferencesKey("aod_accent")
        val HIGH_ALARM_ENABLED = booleanPreferencesKey("high_alarm_enabled")
        val HIGH_ALARM_THRESHOLD = intPreferencesKey("high_alarm_threshold")
        val LOW_ALARM_ENABLED = booleanPreferencesKey("low_alarm_enabled")
        val LOW_ALARM_THRESHOLD = intPreferencesKey("low_alarm_threshold")
        val OVERHEAT_ALARM_ENABLED = booleanPreferencesKey("overheat_alarm_enabled")
        val OVERHEAT_ALARM_THRESHOLD = intPreferencesKey("overheat_alarm_threshold")
        val SESSION_HISTORY_RAW = stringPreferencesKey("session_history_raw")
        val BRACKET_STATS_RAW = stringPreferencesKey("bracket_stats_raw")
    }

    val settingsFlow: Flow<PowerSettings> = context.powerSuiteDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p ->
            PowerSettings(
                tempUnit = TempUnit.fromKey(p[Keys.TEMP_UNIT]),
                dualCellMode = p[Keys.DUAL_CELL] ?: false,
                designCapacityMah = p[Keys.DESIGN_CAPACITY_MAH] ?: detectHardwareDesignCapacity(context),
                hudEnabled = p[Keys.HUD_ENABLED] ?: false,
                hudOpacity = (p[Keys.HUD_OPACITY] ?: 0.88f).coerceIn(0.4f, 1.0f),
                hudAccent = PowerAccentPreset.fromKey(p[Keys.HUD_ACCENT]),
                aodStyle = AodVisualStyle.fromKey(p[Keys.AOD_STYLE]),
                aodAccent = PowerAccentPreset.fromKey(p[Keys.AOD_ACCENT]),
                highChargeAlarmEnabled = p[Keys.HIGH_ALARM_ENABLED] ?: false,
                highChargeThreshold = (p[Keys.HIGH_ALARM_THRESHOLD] ?: 85).coerceIn(60, 100),
                lowBatteryAlarmEnabled = p[Keys.LOW_ALARM_ENABLED] ?: false,
                lowBatteryThreshold = (p[Keys.LOW_ALARM_THRESHOLD] ?: 20).coerceIn(5, 45),
                overheatAlarmEnabled = p[Keys.OVERHEAT_ALARM_ENABLED] ?: false,
                overheatThresholdC = (p[Keys.OVERHEAT_ALARM_THRESHOLD] ?: 42).coerceIn(35, 52),
            )
        }

    val sessionHistoryFlow: Flow<List<ChargingSessionRecord>> = context.powerSuiteDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p -> decodeSessions(p[Keys.SESSION_HISTORY_RAW].orEmpty()) }

    val bracketStatsFlow: Flow<List<LevelBracketStat>> = context.powerSuiteDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p -> decodeBrackets(p[Keys.BRACKET_STATS_RAW].orEmpty()) }

    suspend fun updateSettings(transform: (PowerSettings) -> PowerSettings) {
        context.powerSuiteDataStore.edit { p ->
            val current = PowerSettings(
                tempUnit = TempUnit.fromKey(p[Keys.TEMP_UNIT]),
                dualCellMode = p[Keys.DUAL_CELL] ?: false,
                designCapacityMah = p[Keys.DESIGN_CAPACITY_MAH] ?: detectHardwareDesignCapacity(context),
                hudEnabled = p[Keys.HUD_ENABLED] ?: false,
                hudOpacity = (p[Keys.HUD_OPACITY] ?: 0.88f).coerceIn(0.4f, 1.0f),
                hudAccent = PowerAccentPreset.fromKey(p[Keys.HUD_ACCENT]),
                aodStyle = AodVisualStyle.fromKey(p[Keys.AOD_STYLE]),
                aodAccent = PowerAccentPreset.fromKey(p[Keys.AOD_ACCENT]),
                highChargeAlarmEnabled = p[Keys.HIGH_ALARM_ENABLED] ?: false,
                highChargeThreshold = p[Keys.HIGH_ALARM_THRESHOLD] ?: 85,
                lowBatteryAlarmEnabled = p[Keys.LOW_ALARM_ENABLED] ?: false,
                lowBatteryThreshold = p[Keys.LOW_ALARM_THRESHOLD] ?: 20,
                overheatAlarmEnabled = p[Keys.OVERHEAT_ALARM_ENABLED] ?: false,
                overheatThresholdC = p[Keys.OVERHEAT_ALARM_THRESHOLD] ?: 42,
            )
            val updated = transform(current)
            p[Keys.TEMP_UNIT] = updated.tempUnit.key
            p[Keys.DUAL_CELL] = updated.dualCellMode
            p[Keys.DESIGN_CAPACITY_MAH] = updated.designCapacityMah
            p[Keys.HUD_ENABLED] = updated.hudEnabled
            p[Keys.HUD_OPACITY] = updated.hudOpacity
            p[Keys.HUD_ACCENT] = updated.hudAccent.key
            p[Keys.AOD_STYLE] = updated.aodStyle.key
            p[Keys.AOD_ACCENT] = updated.aodAccent.key
            p[Keys.HIGH_ALARM_ENABLED] = updated.highChargeAlarmEnabled
            p[Keys.HIGH_ALARM_THRESHOLD] = updated.highChargeThreshold
            p[Keys.LOW_ALARM_ENABLED] = updated.lowBatteryAlarmEnabled
            p[Keys.LOW_ALARM_THRESHOLD] = updated.lowBatteryThreshold
            p[Keys.OVERHEAT_ALARM_ENABLED] = updated.overheatAlarmEnabled
            p[Keys.OVERHEAT_ALARM_THRESHOLD] = updated.overheatThresholdC
        }
    }

    suspend fun saveSession(record: ChargingSessionRecord) {
        context.powerSuiteDataStore.edit { p ->
            val existing = decodeSessions(p[Keys.SESSION_HISTORY_RAW].orEmpty())
            val merged = (listOf(record) + existing).take(15)
            p[Keys.SESSION_HISTORY_RAW] = encodeSessions(merged)
        }
    }

    suspend fun clearSessions() {
        context.powerSuiteDataStore.edit { p ->
            p.remove(Keys.SESSION_HISTORY_RAW)
            p.remove(Keys.BRACKET_STATS_RAW)
        }
    }

    suspend fun saveBracketStats(stats: List<LevelBracketStat>) {
        context.powerSuiteDataStore.edit { p ->
            p[Keys.BRACKET_STATS_RAW] = encodeBrackets(stats)
        }
    }

    private fun encodeSessions(list: List<ChargingSessionRecord>): String =
        list.joinToString("\n") { s ->
            listOf(
                s.id,
                s.startedAtMs,
                s.endedAtMs,
                s.startPercent,
                s.endPercent,
                s.peakWatts,
                s.avgWatts,
                s.maxTempCelsius,
                s.isDualCell,
            ).joinToString("|")
        }

    private fun decodeSessions(raw: String): List<ChargingSessionRecord> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 9) return@mapNotNull null
            runCatching {
                ChargingSessionRecord(
                    id = parts[0],
                    startedAtMs = parts[1].toLong(),
                    endedAtMs = parts[2].toLong(),
                    startPercent = parts[3].toInt(),
                    endPercent = parts[4].toInt(),
                    peakWatts = parts[5].toFloat(),
                    avgWatts = parts[6].toFloat(),
                    maxTempCelsius = parts[7].toFloat(),
                    isDualCell = parts[8].toBoolean(),
                )
            }.getOrNull()
        }.toList()
    }

    private fun encodeBrackets(stats: List<LevelBracketStat>): String =
        stats.joinToString("\n") { b ->
            listOf(b.id, b.avgWatts, b.peakWatts, b.sampleCount).joinToString("|")
        }

    private fun decodeBrackets(raw: String): List<LevelBracketStat> {
        val defaults = defaultBracketStats()
        if (raw.isBlank()) return defaults
        val parsed = raw.lineSequence().mapNotNull { line ->
            val p = line.split("|")
            if (p.size < 4) return@mapNotNull null
            runCatching {
                p[0] to Triple(p[1].toFloat(), p[2].toFloat(), p[3].toInt())
            }.getOrNull()
        }.toMap()
        return defaults.map { base ->
            val saved = parsed[base.id]
            if (saved != null) {
                base.copy(avgWatts = saved.first, peakWatts = saved.second, sampleCount = saved.third)
            } else {
                base
            }
        }
    }

    companion object {
        @Suppress("PrivateApi")
        fun detectHardwareDesignCapacity(context: Context): Int {
            return runCatching {
                val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
                val instance = powerProfileClass
                    .getConstructor(Context::class.java)
                    .newInstance(context.applicationContext)
                val capacity = powerProfileClass
                    .getMethod("getBatteryCapacity")
                    .invoke(instance) as? Double
                val rounded = capacity?.toInt() ?: 0
                if (rounded in 1500..15000) rounded else 5000
            }.getOrDefault(5000)
        }
    }
}
