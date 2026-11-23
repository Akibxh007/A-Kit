package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    val dynamicColor: Boolean = true,
    val snapCleanRetentionHours: Int = 168,
)

private val Context.akitDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "akit_local_preferences"
)

class ThemePreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SNAPCLEAN_RETENTION_HOURS = intPreferencesKey("snapclean_retention_hours")
    }

    val preferencesFlow: Flow<AppPreferences> = context.akitDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AppPreferences(
                themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
                snapCleanRetentionHours = prefs[Keys.SNAPCLEAN_RETENTION_HOURS] ?: 168,
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

    suspend fun setSnapCleanRetentionHours(hours: Int) {
        context.akitDataStore.edit { prefs ->
            prefs[Keys.SNAPCLEAN_RETENTION_HOURS] = hours
        }
    }
}
