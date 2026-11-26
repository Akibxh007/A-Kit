package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.data.ThemePreferencesRepository
import com.example.feature.drop.DropUrlParser

class MainActivity : ComponentActivity() {

    private lateinit var preferencesRepository: ThemePreferencesRepository
    private var pendingDropUrl by mutableStateOf<String?>(null)
    private var pendingOpenPowerTab by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install AndroidX SplashScreen before super.onCreate so cold start renders pure #000000 immediately
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferencesRepository = ThemePreferencesRepository(applicationContext)
        pendingDropUrl = extractDropUrl(intent)
        pendingOpenPowerTab = intent?.getBooleanExtra(EXTRA_OPEN_POWER_TAB, false) == true

        // Render initial Compose frame immediately (< 150ms); all IO, DataStore, and service starts run async
        setContent {
            AKitApp(
                preferencesRepository = preferencesRepository,
                incomingDropUrl = pendingDropUrl,
                onConsumeIncomingDropUrl = { pendingDropUrl = null },
                openPowerTabRequested = pendingOpenPowerTab,
                onConsumeOpenPowerTabRequest = { pendingOpenPowerTab = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDropUrl(intent)?.let { url ->
            pendingDropUrl = url
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_POWER_TAB, false)) {
            pendingOpenPowerTab = true
        }
    }

    private fun extractDropUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val raw = intent.dataString ?: return null
        return if (DropUrlParser.parseQrPayload(raw) != null) raw else null
    }

    companion object {
        const val EXTRA_OPEN_POWER_TAB = "extra_open_power_tab"
    }
}
