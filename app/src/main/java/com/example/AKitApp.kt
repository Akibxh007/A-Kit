package com.example

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppPreferences
import com.example.data.ThemeMode
import com.example.data.ThemePreferencesRepository
import com.example.feature.drop.DropScreen
import com.example.feature.drop.DropViewModel
import com.example.feature.power.PowerScreen
import com.example.feature.power.PowerTelemetryEngine
import com.example.feature.power.PowerViewModel
import com.example.feature.snapclean.SnapCleanScreen
import com.example.feature.snapclean.SnapCleanViewModel
import com.example.ui.components.AKitTopBar
import com.example.ui.components.SettingsModalSheet
import com.example.ui.navigation.AKitDestination
import com.example.ui.navigation.AKitNavigationRail
import com.example.ui.navigation.FloatingPillNavBar
import com.example.ui.theme.AKitTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AKitAppViewModel(
    application: Application,
    private val preferencesRepository: ThemePreferencesRepository,
) : AndroidViewModel(application) {

    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppPreferences(),
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDynamicColor(enabled)
        }
    }

    fun setAutoLaunchAodOnPlugIn(enabled: Boolean) {
        PowerTelemetryEngine.setAutoLaunchAodOnPlugIn(enabled)
        viewModelScope.launch {
            preferencesRepository.setAutoLaunchAodOnPlugIn(enabled)
        }
    }

    fun setDefaultStartupTab(tabRoute: String) {
        viewModelScope.launch {
            preferencesRepository.setDefaultStartupTab(tabRoute)
        }
    }
}

class AKitViewModelFactory(
    private val application: Application,
    private val preferencesRepository: ThemePreferencesRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AKitAppViewModel::class.java) ->
                AKitAppViewModel(application, preferencesRepository) as T
            modelClass.isAssignableFrom(PowerViewModel::class.java) ->
                PowerViewModel(application) as T
            modelClass.isAssignableFrom(SnapCleanViewModel::class.java) ->
                SnapCleanViewModel(application, preferencesRepository) as T
            modelClass.isAssignableFrom(DropViewModel::class.java) ->
                DropViewModel(application) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

@Composable
fun AKitApp(
    preferencesRepository: ThemePreferencesRepository,
    incomingDropUrl: String? = null,
    onConsumeIncomingDropUrl: () -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as Application
    val factory = AKitViewModelFactory(application, preferencesRepository)

    val appViewModel: AKitAppViewModel = viewModel(factory = factory)
    val powerViewModel: PowerViewModel = viewModel(factory = factory)
    val snapCleanViewModel: SnapCleanViewModel = viewModel(factory = factory)
    val dropViewModel: DropViewModel = viewModel(factory = factory)

    val preferences by appViewModel.preferences.collectAsStateWithLifecycle()
    var currentDestination by rememberSaveable { mutableStateOf(AKitDestination.POWER) }
    var appliedStartupTab by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    // Keep Charging AOD auto-launch setting synchronized with PowerTelemetryEngine
    LaunchedEffect(preferences.autoLaunchAodOnPlugIn) {
        PowerTelemetryEngine.setAutoLaunchAodOnPlugIn(preferences.autoLaunchAodOnPlugIn)
    }

    // Apply user's preferred Default Startup Tab once on cold launch (unless a Drop deep link arrived)
    LaunchedEffect(preferences.defaultStartupTab, incomingDropUrl) {
        if (!appliedStartupTab && incomingDropUrl == null) {
            val preferred = AKitDestination.entries.firstOrNull { it.route == preferences.defaultStartupTab }
            if (preferred != null) {
                currentDestination = preferred
            }
            appliedStartupTab = true
        }
    }

    // Open Drop tab & auto-connect natively when launched via akit://drop or LAN Drop URL
    LaunchedEffect(incomingDropUrl) {
        if (!incomingDropUrl.isNullOrBlank()) {
            appliedStartupTab = true
            currentDestination = AKitDestination.DROP
            dropViewModel.connectFromRawPayload(incomingDropUrl)
            onConsumeIncomingDropUrl()
        }
    }

    // Return to primary Power tab on system Back when viewing secondary tabs
    BackHandler(enabled = currentDestination != AKitDestination.POWER) {
        currentDestination = AKitDestination.POWER
    }

    AKitTheme(
        themeMode = preferences.themeMode,
        dynamicColor = preferences.dynamicColor,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 840.dp
            val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            Row(modifier = Modifier.fillMaxSize()) {
                if (useNavigationRail) {
                    AKitNavigationRail(
                        currentDestination = currentDestination,
                        onSelectDestination = { currentDestination = it },
                    )
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        AKitTopBar(
                            onOpenSettings = { showSettingsSheet = true },
                        )
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        // Generous bottom clearance so scrolling cards/buttons are NEVER obscured by floating pill nav
                        val listContentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = if (useNavigationRail) {
                                28.dp + navBarBottomInset
                            } else {
                                128.dp + navBarBottomInset
                            },
                        )

                        AnimatedContent(
                            targetState = currentDestination,
                            transitionSpec = {
                                val movingForward = targetState.ordinal >= initialState.ordinal
                                val slideSpec = spring<IntOffset>(
                                    dampingRatio = 0.86f,
                                    stiffness = Spring.StiffnessMediumLow,
                                )
                                val slideIn = slideInHorizontally(
                                    animationSpec = slideSpec,
                                    initialOffsetX = { fullWidth ->
                                        if (movingForward) fullWidth / 5 else -fullWidth / 5
                                    },
                                ) + fadeIn(animationSpec = tween(220))
                                val slideOut = slideOutHorizontally(
                                    animationSpec = slideSpec,
                                    targetOffsetX = { fullWidth ->
                                        if (movingForward) -fullWidth / 6 else fullWidth / 6
                                    },
                                ) + fadeOut(animationSpec = tween(160))
                                slideIn togetherWith slideOut
                            },
                            label = "akit_tab_transition",
                        ) { destination ->
                            when (destination) {
                                AKitDestination.POWER -> PowerScreen(
                                    viewModel = powerViewModel,
                                    contentPadding = listContentPadding,
                                )
                                AKitDestination.SNAPCLEAN -> SnapCleanScreen(
                                    viewModel = snapCleanViewModel,
                                    contentPadding = listContentPadding,
                                )
                                AKitDestination.DROP -> DropScreen(
                                    viewModel = dropViewModel,
                                    contentPadding = listContentPadding,
                                )
                            }
                        }

                        if (!useNavigationRail) {
                            FloatingPillNavBar(
                                currentDestination = currentDestination,
                                onSelectDestination = { currentDestination = it },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }

            if (showSettingsSheet) {
                SettingsModalSheet(
                    preferences = preferences,
                    onSelectThemeMode = appViewModel::setThemeMode,
                    onToggleDynamicColor = appViewModel::setDynamicColor,
                    onToggleAutoLaunchAod = appViewModel::setAutoLaunchAodOnPlugIn,
                    onSelectStartupTab = appViewModel::setDefaultStartupTab,
                    onDismiss = { showSettingsSheet = false },
                )
            }
        }
    }
}
