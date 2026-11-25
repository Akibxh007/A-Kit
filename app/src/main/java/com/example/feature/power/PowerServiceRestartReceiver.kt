package com.example.feature.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.ThemePreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Revives [PowerMonitorService] if the OS removes the app task from Recents while
 * Floating HUD, Battery Protection Guards, or Auto-Launch Charging AOD are enabled.
 */
class PowerServiceRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESTART_POWER_SERVICE) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext
                val powerSettings = PowerPreferencesStore(app).settingsFlow.first()
                val appPrefs = ThemePreferencesRepository(app).preferencesFlow.first()
                if (powerSettings.requiresBackgroundService || appPrefs.autoLaunchAodOnPlugIn) {
                    PowerMonitorService.syncServiceState(
                        context = app,
                        settings = powerSettings,
                        autoLaunchAodOnPlugIn = appPrefs.autoLaunchAodOnPlugIn,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RESTART_POWER_SERVICE = "com.example.feature.power.ACTION_RESTART_SERVICE"
    }
}
