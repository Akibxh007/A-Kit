package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.data.AccentSwatch
import com.example.data.AppPreferences
import com.example.data.ClockNumberStyle
import com.example.data.FloatingPillStyle
import com.example.data.GaugeStyle
import com.example.data.ThemeMode
import com.example.data.ThemePreferencesRepository
import com.example.feature.design.DesignScreen
import com.example.feature.drop.DropScreen
import com.example.feature.drop.DropViewModel
import com.example.feature.power.PowerScreen
import com.example.feature.power.PowerTelemetryEngine
import com.example.feature.power.PowerViewModel
import com.example.feature.snapclean.SnapCleanScreen
import com.example.feature.snapclean.SnapCleanViewModel
import com.example.feature.update.AppUpdateManager
import com.example.feature.update.AppUpdateState
import com.example.feature.update.UpdateCheckStatus
import com.example.feature.update.UpdateModalSheet
import com.example.ui.components.AKitSplashOverlay
import com.example.ui.components.AKitTopBar
import com.example.ui.components.SettingsModalSheet
import com.example.ui.navigation.AKitBottomNavigationBar
import com.example.ui.navigation.AKitDestination
import com.example.ui.navigation.AKitNavigationRail
import com.example.ui.theme.AKitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun setGaugeStyle(style: GaugeStyle) {
        viewModelScope.launch {
            preferencesRepository.setGaugeStyle(style)
        }
    }

    fun setFloatingPillStyle(style: FloatingPillStyle) {
        viewModelScope.launch {
            preferencesRepository.setFloatingPillStyle(style)
        }
    }

    fun setClockNumberStyle(style: ClockNumberStyle) {
        viewModelScope.launch {
            preferencesRepository.setClockNumberStyle(style)
        }
    }

    private val _updateState = MutableStateFlow(AppUpdateState())
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    fun setAccentSwatch(swatch: AccentSwatch) {
        viewModelScope.launch {
            preferencesRepository.setAccentSwatch(swatch)
        }
    }

    fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences(transform)
        }
    }

    fun checkForUpdates(silent: Boolean) {
        val current = _updateState.value
        if (current.status == UpdateCheckStatus.CHECKING || current.status == UpdateCheckStatus.DOWNLOADING) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (!silent) {
                _updateState.update {
                    it.copy(
                        status = UpdateCheckStatus.CHECKING,
                        statusMessage = "Checking GitHub Releases...",
                    )
                }
            }
            when (val result = AppUpdateManager.checkLatestRelease(BuildConfig.VERSION_NAME)) {
                is AppUpdateManager.CheckResult.UpdateAvailable -> {
                    _updateState.update {
                        it.copy(
                            status = UpdateCheckStatus.UPDATE_AVAILABLE,
                            release = result.release,
                            showSheet = true,
                            downloadedBytes = 0L,
                            totalBytes = result.release.apkSizeBytes,
                            downloadedApkUri = null,
                            downloadedApkPath = null,
                            statusMessage = "Version ${result.release.tagName} available",
                        )
                    }
                }
                is AppUpdateManager.CheckResult.UpToDate -> {
                    _updateState.update {
                        it.copy(
                            status = UpdateCheckStatus.UP_TO_DATE,
                            showSheet = false,
                            statusMessage = if (silent) null else "You're on the latest version (v${BuildConfig.VERSION_NAME})",
                        )
                    }
                }
                is AppUpdateManager.CheckResult.Error -> {
                    if (silent) {
                        _updateState.update {
                            if (it.release == null) it.copy(status = UpdateCheckStatus.IDLE, statusMessage = null) else it
                        }
                    } else {
                        _updateState.update {
                            it.copy(
                                status = UpdateCheckStatus.OFFLINE_OR_ERROR,
                                statusMessage = result.message,
                            )
                        }
                    }
                }
            }
        }
    }

    fun downloadUpdateToDownloads() {
        val release = _updateState.value.release ?: return
        if (_updateState.value.status == UpdateCheckStatus.DOWNLOADING) return

        viewModelScope.launch(Dispatchers.IO) {
            _updateState.update {
                it.copy(
                    status = UpdateCheckStatus.DOWNLOADING,
                    downloadedBytes = 0L,
                    totalBytes = release.apkSizeBytes,
                    downloadedApkUri = null,
                    downloadedApkPath = null,
                    statusMessage = "Saving ${release.apkFileName} to Downloads...",
                )
            }
            when (
                val result = AppUpdateManager.downloadApkToPublicDownloads(
                    context = getApplication(),
                    release = release,
                ) { downloaded, total ->
                    _updateState.update {
                        it.copy(
                            downloadedBytes = downloaded,
                            totalBytes = total,
                        )
                    }
                }
            ) {
                is AppUpdateManager.DownloadResult.SavedToDownloads -> {
                    _updateState.update {
                        it.copy(
                            status = UpdateCheckStatus.READY_TO_INSTALL,
                            downloadedApkUri = result.contentUri?.toString(),
                            downloadedApkPath = result.localFilePath,
                            statusMessage = "Saved ${result.displayName} to Downloads",
                        )
                    }
                    withContext(Dispatchers.Main) {
                        AppUpdateManager.launchPackageInstaller(
                            context = getApplication(),
                            apkFilePath = result.localFilePath,
                            fallbackUri = result.contentUri,
                        )
                    }
                }
                AppUpdateManager.DownloadResult.OpenedInBrowser -> {
                    _updateState.update {
                        it.copy(
                            status = UpdateCheckStatus.UPDATE_AVAILABLE,
                            showSheet = false,
                            statusMessage = "Opened APK download in browser",
                        )
                    }
                }
                is AppUpdateManager.DownloadResult.Failed -> {
                    _updateState.update {
                        it.copy(
                            status = UpdateCheckStatus.OFFLINE_OR_ERROR,
                            statusMessage = result.reason,
                        )
                    }
                }
            }
        }
    }

    fun installReadyApk(callerContext: Context = getApplication()) {
        val current = _updateState.value
        val uri = current.downloadedApkUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        AppUpdateManager.launchPackageInstaller(
            context = callerContext,
            apkFilePath = current.downloadedApkPath,
            fallbackUri = uri,
        )
    }

    fun openUpdateSheet() {
        if (_updateState.value.release != null) {
            _updateState.update { it.copy(showSheet = true) }
        } else {
            checkForUpdates(silent = false)
        }
    }

    fun dismissUpdateSheet() {
        _updateState.update { it.copy(showSheet = false) }
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
    openPowerTabRequested: Boolean = false,
    onConsumeOpenPowerTabRequest: () -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application, preferencesRepository) {
        AKitViewModelFactory(application, preferencesRepository)
    }

    val appViewModel: AKitAppViewModel = viewModel(factory = factory)
    val powerViewModel: PowerViewModel = viewModel(factory = factory)
    val snapCleanViewModel: SnapCleanViewModel = viewModel(factory = factory)
    val dropViewModel: DropViewModel = viewModel(factory = factory)

    val preferences by appViewModel.preferences.collectAsStateWithLifecycle()
    val updateState by appViewModel.updateState.collectAsStateWithLifecycle()
    var currentDestination by rememberSaveable { mutableStateOf(AKitDestination.POWER) }
    var appliedStartupTab by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showSplashOverlay by rememberSaveable { mutableStateOf(incomingDropUrl.isNullOrBlank() && !openPowerTabRequested) }

    // Silent GitHub Releases update check deferred past splash animation (zero cold-start contention)
    LaunchedEffect(Unit) {
        delay(950L)
        appViewModel.checkForUpdates(silent = true)
    }

    // Keep Charging AOD & Design Studio styles synchronized with PowerTelemetryEngine (HUD + AOD)
    LaunchedEffect(preferences) {
        PowerTelemetryEngine.setAutoLaunchAodOnPlugIn(preferences.autoLaunchAodOnPlugIn)
        PowerTelemetryEngine.updateDesignPreferences(preferences)
    }

    // Apply user's preferred Default Startup Tab once on cold launch (unless a deep link or AOD More Info arrived)
    LaunchedEffect(preferences.defaultStartupTab, incomingDropUrl, openPowerTabRequested) {
        if (!appliedStartupTab && incomingDropUrl == null && !openPowerTabRequested) {
            val preferred = AKitDestination.entries.firstOrNull { it.route == preferences.defaultStartupTab }
            if (preferred != null) {
                currentDestination = preferred
            }
            appliedStartupTab = true
        }
    }

    // Navigate directly to detailed Power telemetry dashboard when launched from AOD "More Info ↗"
    LaunchedEffect(openPowerTabRequested) {
        if (openPowerTabRequested) {
            showSplashOverlay = false
            appliedStartupTab = true
            currentDestination = AKitDestination.POWER
            onConsumeOpenPowerTabRequest()
        }
    }

    // Open Drop tab & auto-connect natively when launched via akit://drop or LAN Drop URL
    LaunchedEffect(incomingDropUrl) {
        if (!incomingDropUrl.isNullOrBlank()) {
            showSplashOverlay = false
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
        gaugeStyle = preferences.gaugeStyle,
        floatingPillStyle = preferences.floatingPillStyle,
        clockNumberStyle = preferences.clockNumberStyle,
        accentSwatch = preferences.accentSwatch,
        use24HourTime = preferences.use24HourTime,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 840.dp

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
                    bottomBar = {
                        if (!useNavigationRail) {
                            AKitBottomNavigationBar(
                                currentDestination = currentDestination,
                                onSelectDestination = { currentDestination = it },
                            )
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        val listContentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = if (useNavigationRail) 40.dp else 96.dp,
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
                                    entranceVisible = !showSplashOverlay,
                                )
                                AKitDestination.DESIGN -> DesignScreen(
                                    preferences = preferences,
                                    powerViewModel = powerViewModel,
                                    onUpdatePreferences = appViewModel::updatePreferences,
                                    onToggleAutoLaunchCharging = appViewModel::setAutoLaunchAodOnPlugIn,
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
                    }
                }
            }

            if (showSettingsSheet) {
                SettingsModalSheet(
                    preferences = preferences,
                    updateState = updateState,
                    onSelectThemeMode = appViewModel::setThemeMode,
                    onToggleDynamicColor = appViewModel::setDynamicColor,
                    onToggleAutoLaunchAod = appViewModel::setAutoLaunchAodOnPlugIn,
                    onSelectStartupTab = appViewModel::setDefaultStartupTab,
                    onCheckForUpdates = { appViewModel.checkForUpdates(silent = false) },
                    onOpenUpdateSheet = {
                        showSettingsSheet = false
                        appViewModel.openUpdateSheet()
                    },
                    onDismiss = { showSettingsSheet = false },
                )
            }

            if (updateState.showSheet && updateState.release != null) {
                UpdateModalSheet(
                    updateState = updateState,
                    onDownloadUpdate = appViewModel::downloadUpdateToDownloads,
                    onInstallReadyApk = { callerContext -> appViewModel.installReadyApk(callerContext) },
                    onDismiss = appViewModel::dismissUpdateSheet,
                )
            }

            // Branded AMOLED spring splash overlay (650ms total -> smooth cross-fade to dashboard)
            AKitSplashOverlay(
                visible = showSplashOverlay,
                accentColor = MaterialTheme.colorScheme.primary,
                onAnimationFinished = { showSplashOverlay = false },
            )
        }
    }
}
