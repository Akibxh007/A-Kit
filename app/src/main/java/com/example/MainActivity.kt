package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.ThemePreferencesRepository

class MainActivity : ComponentActivity() {

    private lateinit var preferencesRepository: ThemePreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferencesRepository = ThemePreferencesRepository(applicationContext)
        setContent {
            AKitApp(preferencesRepository = preferencesRepository)
        }
    }
}
