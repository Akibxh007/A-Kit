package com.example.data

import android.content.Context
import androidx.compose.runtime.Immutable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    MINIMAL_THIN_ARC("minimal_thin", "Classic Thin Arc", "Clean 270° progress arc"),
    THICK_RING("thick_ring", "Thick Ring", "Bold high-contrast energy band"),
    SEGMENTED_TICK("segmented_tick", "Segmented Ticks", "Precision radial tick ring"),
    DOTTED_VORTEX("dotted_vortex", "Dotted Vortex", "Halftone concentric spiral"),
    VERTICAL_BATTERY_PILL("vertical_pill", "Vertical Battery Pill", "Liquid wave capsule level"),
    ANALOG_NEEDLE_DIAL("analog_needle", "Analog Needle Dial", "180° speedometer with pointer"),
    CONCENTRIC_ORBIT("concentric_orbit", "Concentric Orbit", "Dual-ring glowing orbit"),
    ATOMIC_SWIRL("atomic_swirl", "Atomic Swirl", "Rotating multi-petal orbits"),
    CORNER_BRACKETS_HUD("corner_brackets", "Corner Brackets HUD", "4-corner viewfinder frame"),
    LIVE_SPARKLINE_WAVE("sparkline_wave", "Live Sparkline Wave", "Animated waveform readout"),
    HORIZONTAL_BAR_STACK("bar_stack", "Horizontal Bar Stack", "Stacked power signal bars"),
    INVERTED_FUNNEL_BARS("funnel_bars", "Inverted Funnel Bars", "Tapered triangular bars"),
    DOUBLE_RING_NEON_ARC("double_ring_neon_arc", "Double-Ring Neon", "Outer dark-teal ring & glowing arc"),
    BRAIDED_WAVE_HELIX("braided_wave_helix", "Braided Wave Helix", "Intertwined dual-sine neon ring");

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
    OFF("off", "Off", "Hide clock on AOD"),
    EDITORIAL_SERIF("editorial_serif", "Editorial Serif", "High-contrast Didone display"),
    MONTSERRAT_BOLD("montserrat_bold", "Bold Geometric", "Montserrat Alternates Bold"),
    SLIM_LIGHT_MODERN("slim_light", "Slim Light", "Thin geometric modern"),
    RETRO_STENCIL("retro_stencil", "Retro Stencil", "Bold split-slab stencil"),
    OUTLINE_DIGITAL("outline_digital", "Outlined Hollow", "Stroke-outlined numerals"),
    EDITORIAL_DECORATIVE_SERIF("editorial_decorative_serif", "Decorative Serif", "Vintage serif with inline accents"),
    BOLD_ROUNDED_SANS("bold_rounded_sans", "Bold Rounded Sans", "Heavy soft-cornered digital"),
    GEOMETRIC_TECH_BLOCK("geometric_tech_block", "Geometric Tech Block", "Sharp squared futuristic digits");

    companion object {
        fun fromKey(key: String?): ClockNumberStyle = when (key) {
            "condensed_tech" -> SLIM_LIGHT_MODERN
            else -> entries.firstOrNull { it.storageKey == key } ?: EDITORIAL_SERIF
        }
    }
}

enum class BatteryDetailsCardStyle(val storageKey: String, val label: String, val subtitle: String) {
    OUTLINED_CARDS("outlined_cards", "Outlined Cards", "3 bordered rounded cards"),
    MINIMAL_BORDERLESS("minimal_borderless", "Minimal Columns", "Hairline-separated open row"),
    FILLED_DARK_PILLS("filled_pills", "Filled Dark Pills", "Solid #141714 elevated pills"),
    COMPACT_SINGLE_BAR("compact_bar", "Compact Bar", "Unified inline pill strip"),
    ICON_TOP_OUTLINED("icon_top_outlined", "Icon-Top Outlined", "Dark-cyan bordered icon cards"),
    MINIMAL_CIRCULAR_BADGES("minimal_circular_badges", "Minimal Circles", "Clean icon-free circular badges"),
    TOP_GLOW_GRADIENT("top_glow_gradient", "Top-Glow Gradient", "Top cyan glow fading into #070B0D");

    companion object {
        fun fromKey(key: String?): BatteryDetailsCardStyle =
            entries.firstOrNull { it.storageKey == key } ?: OUTLINED_CARDS
    }
}

enum class AccentSwatch(val storageKey: String, val label: String, val argbInt: Int) {
    ELECTRIC_BLUE("electric_blue", "Blue", 0xFF38BDF8.toInt()),
    DYNAMIC_AUTO("dynamic_auto", "Auto", 0xFF7CE7A7.toInt()),
    CORAL_RED("coral_red", "Coral", 0xFFFF3B5C.toInt()),
    MAGENTA("magenta", "Magenta", 0xFFD946EF.toInt()),
    INDIGO("indigo", "Indigo", 0xFF6366F1.toInt()),
    CYAN("cyan", "Cyan", 0xFF00E5FF.toInt()),
    MINT("mint", "Mint", 0xFF10B981.toInt()),
    LIME("lime", "Lime", 0xFF84CC16.toInt()),
    AMBER("amber", "Amber", 0xFFF59E0B.toInt()),
    PURE_WHITE("pure_white", "White", 0xFFFFFFFF.toInt());

    companion object {
        fun fromKey(key: String?): AccentSwatch = when (key) {
            "emerald_green" -> MINT
            "cyber_peach" -> AMBER
            "monochrome" -> PURE_WHITE
            else -> entries.firstOrNull { it.storageKey == key } ?: ELECTRIC_BLUE
        }
    }
}

enum class AodMetricSlot(val storageKey: String, val label: String) {
    TEMP("temp", "Temp"),
    VOLTAGE("voltage", "Voltage"),
    ESTIMATE("estimate", "Estimate"),
    CURRENT("current", "Current"),
    WATTAGE("wattage", "Wattage"),
    HEALTH("health", "Health");

    companion object {
        val DEFAULT_THREE: List<AodMetricSlot> = listOf(TEMP, VOLTAGE, ESTIMATE)

        fun decodeList(raw: String?): List<AodMetricSlot> {
            if (raw.isNullOrBlank()) return DEFAULT_THREE
            val parsed = raw.split(",")
                .mapNotNull { key -> entries.firstOrNull { it.storageKey == key.trim() } }
                .distinct()
            return if (parsed.size == 3) parsed else DEFAULT_THREE
        }

        fun encodeList(list: List<AodMetricSlot>): String =
            list.take(3).joinToString(",") { it.storageKey }
    }
}

enum class AodDimTimeout(val storageKey: String, val label: String, val delayMs: Long?) {
    NEVER("never", "Never", null),
    SEC_30("30s", "30s", 30_000L),
    MIN_1("1m", "1m", 60_000L),
    MIN_5("5m", "5m", 300_000L);

    companion object {
        fun fromKey(key: String?): AodDimTimeout =
            entries.firstOrNull { it.storageKey == key } ?: NEVER
    }
}

enum class AodHideTimeout(val storageKey: String, val label: String, val delayMs: Long?) {
    NEVER("never", "Never", null),
    MIN_1("1m", "1m", 60_000L),
    MIN_5("5m", "5m", 300_000L),
    MIN_15("15m", "15m", 900_000L);

    companion object {
        fun fromKey(key: String?): AodHideTimeout =
            entries.firstOrNull { it.storageKey == key } ?: NEVER
    }
}

enum class AodDateFormatOption(val storageKey: String, val pattern: String, val sampleLabel: String) {
    EEE_MMM_D("eee_mmm_d", "EEE, MMM d", "Wed, Nov 26"),
    EEE_D_MMM("eee_d_mmm", "EEE, d MMM", "Thu, 26 Nov"),
    D_MMM_YYYY("d_mmm_yyyy", "d MMM yyyy", "26 Nov 2026"),
    EEEE_MMM_D("eeee_mmm_d", "EEEE, MMM d", "Wednesday, Nov 26");

    fun formatDate(date: Date = Date()): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(date)

    companion object {
        fun fromKey(key: String?): AodDateFormatOption =
            entries.firstOrNull { it.storageKey == key } ?: EEE_MMM_D
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
    val clockNumberStyle: ClockNumberStyle = ClockNumberStyle.EDITORIAL_SERIF,
    val batteryDetailsStyle: BatteryDetailsCardStyle = BatteryDetailsCardStyle.OUTLINED_CARDS,
    val accentSwatch: AccentSwatch = AccentSwatch.ELECTRIC_BLUE,
    val speedometerBrightness: Float = 1.0f,
    // What Appears on Screen
    val aodSelectedMetrics: List<AodMetricSlot> = AodMetricSlot.DEFAULT_THREE,
    val showWattsInsteadOfMa: Boolean = true,
    val percentagePrimaryDisplay: Boolean = true,
    val showMoreInfoButton: Boolean = true,
    val showMediaControls: Boolean = false,
    val showAodNotifications: Boolean = false,
    val showTorchButton: Boolean = true,
    val showCameraButton: Boolean = true,
    // Behavior
    val powerSavingMode: Boolean = false,
    val dimScreenAfter: AodDimTimeout = AodDimTimeout.NEVER,
    val hideScreenAfter: AodHideTimeout = AodHideTimeout.NEVER,
    val hideOnScheduleEnabled: Boolean = false,
    val hideScheduleFromMinutes: Int = 22 * 60, // 10:00 PM
    val hideScheduleUntilMinutes: Int = 6 * 60, // 6:00 AM
    val doubleTapToExit: Boolean = true,
    val oledBurnInProtection: Boolean = true,
    // Format
    val dateFormatOption: AodDateFormatOption = AodDateFormatOption.EEE_MMM_D,
    val use24HourTime: Boolean = false,
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
        val BATTERY_DETAILS_STYLE = stringPreferencesKey("design_battery_details_style")
        val ACCENT_SWATCH = stringPreferencesKey("design_accent_swatch")
        val SPEEDOMETER_BRIGHTNESS = floatPreferencesKey("design_speedometer_brightness")

        val AOD_SELECTED_METRICS = stringPreferencesKey("aod_selected_metrics")
        val SHOW_WATTS_INSTEAD_OF_MA = booleanPreferencesKey("aod_show_watts_instead_of_ma")
        val PERCENTAGE_PRIMARY_DISPLAY = booleanPreferencesKey("aod_percentage_primary_display")
        val SHOW_MORE_INFO_BUTTON = booleanPreferencesKey("aod_show_more_info_button")
        val SHOW_MEDIA_CONTROLS = booleanPreferencesKey("aod_show_media_controls")
        val SHOW_AOD_NOTIFICATIONS = booleanPreferencesKey("aod_show_notifications")
        val SHOW_TORCH_BUTTON = booleanPreferencesKey("aod_show_torch_button")
        val SHOW_CAMERA_BUTTON = booleanPreferencesKey("aod_show_camera_button")

        val POWER_SAVING_MODE = booleanPreferencesKey("aod_power_saving_mode")
        val DIM_SCREEN_AFTER = stringPreferencesKey("aod_dim_screen_after")
        val HIDE_SCREEN_AFTER = stringPreferencesKey("aod_hide_screen_after")
        val HIDE_ON_SCHEDULE_ENABLED = booleanPreferencesKey("aod_hide_on_schedule_enabled")
        val HIDE_SCHEDULE_FROM_MINUTES = intPreferencesKey("aod_hide_schedule_from_minutes")
        val HIDE_SCHEDULE_UNTIL_MINUTES = intPreferencesKey("aod_hide_schedule_until_minutes")
        val DOUBLE_TAP_TO_EXIT = booleanPreferencesKey("aod_double_tap_to_exit")
        val OLED_BURN_IN_PROTECTION = booleanPreferencesKey("aod_oled_burn_in_protection")

        val DATE_FORMAT_OPTION = stringPreferencesKey("aod_date_format_option")
        val USE_24_HOUR_TIME = booleanPreferencesKey("global_use_24_hour_time")
    }

    val preferencesFlow: Flow<AppPreferences> = context.akitDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> decodePreferences(prefs) }

    private fun decodePreferences(prefs: Preferences): AppPreferences = AppPreferences(
        themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
        dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
        autoLaunchAodOnPlugIn = prefs[Keys.AUTO_LAUNCH_AOD] ?: false,
        defaultStartupTab = prefs[Keys.DEFAULT_STARTUP_TAB] ?: "power",
        snapCleanRetentionHours = prefs[Keys.SNAPCLEAN_RETENTION_HOURS] ?: 72,
        gaugeStyle = GaugeStyle.fromKey(prefs[Keys.GAUGE_STYLE]),
        floatingPillStyle = FloatingPillStyle.fromKey(prefs[Keys.FLOATING_PILL_STYLE]),
        clockNumberStyle = ClockNumberStyle.fromKey(prefs[Keys.CLOCK_NUMBER_STYLE]),
        batteryDetailsStyle = BatteryDetailsCardStyle.fromKey(prefs[Keys.BATTERY_DETAILS_STYLE]),
        accentSwatch = AccentSwatch.fromKey(prefs[Keys.ACCENT_SWATCH]),
        speedometerBrightness = (prefs[Keys.SPEEDOMETER_BRIGHTNESS] ?: 1.0f).coerceIn(0.20f, 1.0f),
        aodSelectedMetrics = AodMetricSlot.decodeList(prefs[Keys.AOD_SELECTED_METRICS]),
        showWattsInsteadOfMa = prefs[Keys.SHOW_WATTS_INSTEAD_OF_MA] ?: true,
        percentagePrimaryDisplay = prefs[Keys.PERCENTAGE_PRIMARY_DISPLAY] ?: true,
        showMoreInfoButton = prefs[Keys.SHOW_MORE_INFO_BUTTON] ?: true,
        showMediaControls = prefs[Keys.SHOW_MEDIA_CONTROLS] ?: false,
        showAodNotifications = prefs[Keys.SHOW_AOD_NOTIFICATIONS] ?: false,
        showTorchButton = prefs[Keys.SHOW_TORCH_BUTTON] ?: true,
        showCameraButton = prefs[Keys.SHOW_CAMERA_BUTTON] ?: true,
        powerSavingMode = prefs[Keys.POWER_SAVING_MODE] ?: false,
        dimScreenAfter = AodDimTimeout.fromKey(prefs[Keys.DIM_SCREEN_AFTER]),
        hideScreenAfter = AodHideTimeout.fromKey(prefs[Keys.HIDE_SCREEN_AFTER]),
        hideOnScheduleEnabled = prefs[Keys.HIDE_ON_SCHEDULE_ENABLED] ?: false,
        hideScheduleFromMinutes = (prefs[Keys.HIDE_SCHEDULE_FROM_MINUTES] ?: (22 * 60)).coerceIn(0, 1439),
        hideScheduleUntilMinutes = (prefs[Keys.HIDE_SCHEDULE_UNTIL_MINUTES] ?: (6 * 60)).coerceIn(0, 1439),
        doubleTapToExit = prefs[Keys.DOUBLE_TAP_TO_EXIT] ?: true,
        oledBurnInProtection = prefs[Keys.OLED_BURN_IN_PROTECTION] ?: true,
        dateFormatOption = AodDateFormatOption.fromKey(prefs[Keys.DATE_FORMAT_OPTION]),
        use24HourTime = prefs[Keys.USE_24_HOUR_TIME] ?: false,
    )

    suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        context.akitDataStore.edit { prefs ->
            val current = decodePreferences(prefs)
            val updated = transform(current)
            prefs[Keys.THEME_MODE] = updated.themeMode.storageKey
            prefs[Keys.DYNAMIC_COLOR] = updated.dynamicColor
            prefs[Keys.AUTO_LAUNCH_AOD] = updated.autoLaunchAodOnPlugIn
            prefs[Keys.DEFAULT_STARTUP_TAB] = updated.defaultStartupTab
            prefs[Keys.SNAPCLEAN_RETENTION_HOURS] = updated.snapCleanRetentionHours
            prefs[Keys.GAUGE_STYLE] = updated.gaugeStyle.storageKey
            prefs[Keys.FLOATING_PILL_STYLE] = updated.floatingPillStyle.storageKey
            prefs[Keys.CLOCK_NUMBER_STYLE] = updated.clockNumberStyle.storageKey
            prefs[Keys.BATTERY_DETAILS_STYLE] = updated.batteryDetailsStyle.storageKey
            prefs[Keys.ACCENT_SWATCH] = updated.accentSwatch.storageKey
            prefs[Keys.SPEEDOMETER_BRIGHTNESS] = updated.speedometerBrightness.coerceIn(0.20f, 1.0f)

            prefs[Keys.AOD_SELECTED_METRICS] = AodMetricSlot.encodeList(updated.aodSelectedMetrics)
            prefs[Keys.SHOW_WATTS_INSTEAD_OF_MA] = updated.showWattsInsteadOfMa
            prefs[Keys.PERCENTAGE_PRIMARY_DISPLAY] = updated.percentagePrimaryDisplay
            prefs[Keys.SHOW_MORE_INFO_BUTTON] = updated.showMoreInfoButton
            prefs[Keys.SHOW_MEDIA_CONTROLS] = updated.showMediaControls
            prefs[Keys.SHOW_AOD_NOTIFICATIONS] = updated.showAodNotifications
            prefs[Keys.SHOW_TORCH_BUTTON] = updated.showTorchButton
            prefs[Keys.SHOW_CAMERA_BUTTON] = updated.showCameraButton

            prefs[Keys.POWER_SAVING_MODE] = updated.powerSavingMode
            prefs[Keys.DIM_SCREEN_AFTER] = updated.dimScreenAfter.storageKey
            prefs[Keys.HIDE_SCREEN_AFTER] = updated.hideScreenAfter.storageKey
            prefs[Keys.HIDE_ON_SCHEDULE_ENABLED] = updated.hideOnScheduleEnabled
            prefs[Keys.HIDE_SCHEDULE_FROM_MINUTES] = updated.hideScheduleFromMinutes.coerceIn(0, 1439)
            prefs[Keys.HIDE_SCHEDULE_UNTIL_MINUTES] = updated.hideScheduleUntilMinutes.coerceIn(0, 1439)
            prefs[Keys.DOUBLE_TAP_TO_EXIT] = updated.doubleTapToExit
            prefs[Keys.OLED_BURN_IN_PROTECTION] = updated.oledBurnInProtection

            prefs[Keys.DATE_FORMAT_OPTION] = updated.dateFormatOption.storageKey
            prefs[Keys.USE_24_HOUR_TIME] = updated.use24HourTime
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) = updatePreferences { it.copy(themeMode = mode) }
    suspend fun setDynamicColor(enabled: Boolean) = updatePreferences { it.copy(dynamicColor = enabled) }
    suspend fun setAutoLaunchAodOnPlugIn(enabled: Boolean) = updatePreferences { it.copy(autoLaunchAodOnPlugIn = enabled) }
    suspend fun setDefaultStartupTab(tabRoute: String) = updatePreferences { it.copy(defaultStartupTab = tabRoute) }
    suspend fun setSnapCleanRetentionHours(hours: Int) = updatePreferences { it.copy(snapCleanRetentionHours = hours) }
    suspend fun setGaugeStyle(style: GaugeStyle) = updatePreferences { it.copy(gaugeStyle = style) }
    suspend fun setFloatingPillStyle(style: FloatingPillStyle) = updatePreferences { it.copy(floatingPillStyle = style) }
    suspend fun setClockNumberStyle(style: ClockNumberStyle) = updatePreferences { it.copy(clockNumberStyle = style) }
    suspend fun setAccentSwatch(swatch: AccentSwatch) = updatePreferences { it.copy(accentSwatch = swatch) }
}
