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
            entries.firstOrNull { it.storageKey == key } ?: AMOLED
    }
}

@Immutable
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    val dynamicColor: Boolean = false,
    val autoLaunchAodOnPlugIn: Boolean = false,
    val defaultStartupTab: String = "power",
    val snapCleanRetentionHours: Int = 72,
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
}
