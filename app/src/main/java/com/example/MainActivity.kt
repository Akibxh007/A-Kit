package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.ThemePreferencesRepository
import com.example.feature.drop.DropUrlParser

class MainActivity : ComponentActivity() {

    private lateinit var preferencesRepository: ThemePreferencesRepository
    private var pendingDropUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferencesRepository = ThemePreferencesRepository(applicationContext)
        pendingDropUrl = extractDropUrl(intent)

        setContent {
            AKitApp(
                preferencesRepository = preferencesRepository,
                incomingDropUrl = pendingDropUrl,
                onConsumeIncomingDropUrl = { pendingDropUrl = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDropUrl(intent)?.let { url ->
            pendingDropUrl = url
        }
    }

    private fun extractDropUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val raw = intent.dataString ?: return null
        return DropUrlParser.normalizeDropTargetUrl(raw)
    }
}
