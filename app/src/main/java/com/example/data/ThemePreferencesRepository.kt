package com.example.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    AMOLED("amoled");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: DARK
    }
}

enum class GaugeStyle(val storageKey: String, val label: String, val subtitle: String) {
    MINIMAL_THIN_ARC("minimal_thin", "Minimal Thin Arc", "Clean 270° hairline sweep"),
    THICK_RING("thick_ring", "Thick Ring", "Bold high-contrast energy band"),
    SEGMENTED_TICK("segmented_tick", "Segmented Ticks", "Precision dotted dial notches");

    companion object {
        fun fromKey(key: String?): GaugeStyle =
            entries.firstOrNull { it.storageKey == key } ?: MINIMAL_THIN_ARC
    }
}

enum class FloatingPillStyle(val storageKey: String, val label: String, val subtitle: String) {
    COMPACT_SINGLE_LINE("compact_line", "Compact Single-Line", "Slim inline wattage & temp strip"),
    TWO_ROW_BOXED("two_row_boxed", "2-Row Boxed", "Structured dual-line telemetry card"),
    GLASS_PILL("glass_pill", "Glass Pill", "Frosted capsule with accent rim");

    companion object {
        fun fromKey(key: String?): FloatingPillStyle =
            entries.firstOrNull { it.storageKey == key } ?: COMPACT_SINGLE_LINE
    }
}

enum class ClockNumberStyle(val storageKey: String, val label: String, val subtitle: String) {
    MONTSERRAT_BOLD("montserrat_bold", "Montserrat Bold", "Geometric modern display"),
    CONDENSED_TECH("condensed_tech", "Condensed Tech", "Compact high-density readout"),
    OUTLINE_DIGITAL("outline_digital", "Outline Digital", "Crisp wide-spaced instrument");

    companion object {
        fun fromKey(key: String?): ClockNumberStyle =
            entries.firstOrNull { it.storageKey == key } ?: MONTSERRAT_BOLD
    }
}

enum class AccentSwatch(val storageKey: String, val label: String, val argbInt: Int) {
    DYNAMIC_AUTO("dynamic_auto", "Dynamic Auto", 0xFF7CE7A7.toInt()),
    EMERALD_GREEN("emerald_green", "Emerald Green", 0xFF4ADE80.toInt()),
    CYBER_PEACH("cyber_peach", "Cyber Peach", 0xFFFDBA74.toInt()),
    ELECTRIC_BLUE("electric_blue", "Electric Blue", 0xFF38BDF8.toInt()),
    MONOCHROME("monochrome", "Monochrome", 0xFFE2E8F0.toInt());

    companion object {
        fun fromKey(key: String?): AccentSwatch =
            entries.firstOrNull { it.storageKey == key } ?: ELECTRIC_BLUE
    }
}

@Immutable
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val autoLaunchAodOnPlugIn: Boolean = false,
    val defaultStartupTab: String = "power",
    val snapCleanRetentionHours: Int = 72,
    val gaugeStyle: GaugeStyle = GaugeStyle.MINIMAL_THIN_ARC,
    val floatingPillStyle: FloatingPillStyle = FloatingPillStyle.COMPACT_SINGLE_LINE,
    val clockNumberStyle: ClockNumberStyle = ClockNumberStyle.MONTSERRAT_BOLD,
    val accentSwatch: AccentSwatch = AccentSwatch.ELECTRIC_BLUE,
)

private val Context.akitDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "akit_local_preferences",
)

class ThemePreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AUTO_LAUNCH_AOD = booleanPreferencesKey("auto_launch_aod_on_plug")
        val DEFAULT_STARTUP_TAB = stringPreferencesKey("default_startup_tab")
        val SNAPCLEAN_RETENTION_HOURS = intPreferencesKey("snapclean_retention_hours")
        val GAUGE_STYLE = stringPreferencesKey("design_gauge_style")
        val FLOATING_PILL_STYLE = stringPreferencesKey("design_floating_pill_style")
        val CLOCK_NUMBER_STYLE = stringPreferencesKey("design_clock_number_style")
        val ACCENT_SWATCH = stringPreferencesKey("design_accent_swatch")
    }

    val preferencesFlow: Flow<AppPreferences> = context.akitDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AppPreferences(
                themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
                autoLaunchAodOnPlugIn = prefs[Keys.AUTO_LAUNCH_AOD] ?: false,
                defaultStartupTab = prefs[Keys.DEFAULT_STARTUP_TAB] ?: "power",
                snapCleanRetentionHours = prefs[Keys.SNAPCLEAN_RETENTION_HOURS] ?: 72,
                gaugeStyle = GaugeStyle.fromKey(prefs[Keys.GAUGE_STYLE]),
                floatingPillStyle = FloatingPillStyle.fromKey(prefs[Keys.FLOATING_PILL_STYLE]),
                clockNumberStyle = ClockNumberStyle.fromKey(prefs[Keys.CLOCK_NUMBER_STYLE]),
                accentSwatch = AccentSwatch.fromKey(prefs[Keys.ACCENT_SWATCH]),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.akitDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.storageKey
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.akitDataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setAutoLaunchAodOnPlugIn(enabled: Boolean) {
        context.akitDataStore.edit { prefs ->
            prefs[Keys.AUTO_LAUNCH_AOD] = enabled
        }
    }

    suspend fun setDefaultStartupTab(tabRoute: String) {
        context.akitDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_STARTUP_TAB] = tabRoute
        }
    }

    suspend fun setSnapCleanRetentionHours(hours: Int) {
        context.akitDataStore.edit { prefs ->
            prefs[Keys.SNAPCLEAN_RETENTION_HOURS] = hours
        }
    }

    suspend fun setGaugeStyle(style: GaugeStyle) {
        context.akitDataStore.edit { prefs ->
            prefs[Keys.GAUGE_STYLE] = style.storageKey
        }
    }

    suspend fun setFloatingPillStyle(style: FloatingPillStyle) {
        context.akitDataStore.edit { prefs ->
            prefs[Keys.FLOATING_PILL_STYLE] = style.storageKey
        }
    }

    suspend fun setClockNumberStyle(style: ClockNumberStyle) {
        context.akitDataStore.edit { prefs ->
            prefs[Keys.CLOCK_NUMBER_STYLE] = style.storageKey
        }
    }

    suspend fun setAccentSwatch(swatch: AccentSwatch) {
        context.akitDataStore.edit { prefs ->
            prefs[Keys.ACCENT_SWATCH] = swatch.storageKey
        }
    }
}
