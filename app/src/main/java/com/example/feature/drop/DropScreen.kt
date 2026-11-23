package com.example.feature.drop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.ModuleHeroCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.MonoLabelStyle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingWifiPermissionAction {
    NONE,
    START_HOTSPOT,
    SCAN_QR,
}

@Composable
fun DropScreen(
    viewModel: DropViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var snippetDraft by rememberSaveable { mutableStateOf("") }
    var peerSnippetDraft by rememberSaveable { mutableStateOf("") }
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var pendingPermissionAction by remember { mutableStateOf(PendingWifiPermissionAction.NONE) }
    var locationPromptForAction by remember { mutableStateOf(PendingWifiPermissionAction.NONE) }
    val layoutDirection = LocalLayoutDirection.current

    // Ensure generous bottom clearance (>=160.dp) so QR cards & received files NEVER overlap the floating nav pill
    val effectiveContentPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        end = contentPadding.calculateEndPadding(layoutDirection),
        top = contentPadding.calculateTopPadding(),
        bottom = maxOf(contentPadding.calculateBottomPadding(), 160.dp),
    )

    val receivedHostPeerFiles = remember(state.sharedFiles) {
        state.sharedFiles.filter { it.direction == DropDirection.FROM_BROWSER }
    }
    val stagedPhoneFiles = remember(state.sharedFiles) {
        state.sharedFiles.filter { it.direction == DropDirection.FROM_PHONE }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.retryPendingPeerOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Zero-permission Android Photo Picker for staging photos/videos to local server
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.stageFilesFromPhone(uris)
        }
    }

    // Zero-permission Storage Access Framework for staging any document/file to local server
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.stageFilesFromPhone(uris)
        }
    }

    // Pickers for uploading directly to a connected remote A-Kit Peer
    val peerPhotoUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadFilesToPeer(uris)
        }
    }

    val peerFileUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadFilesToPeer(uris)
        }
    }

    val hotspotPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        val action = pendingPermissionAction
        pendingPermissionAction = PendingWifiPermissionAction.NONE
        when (action) {
            PendingWifiPermissionAction.START_HOTSPOT -> {
                if (DropOfflineWifiManager.hasHotspotPermission(context)) {
                    if (!DropOfflineWifiManager.isLocationEnabled(context)) {
                        locationPromptForAction = PendingWifiPermissionAction.START_HOTSPOT
                    } else {
                        viewModel.startOfflineHotspot()
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Nearby Wi-Fi & Location permissions are required for Offline Hotspot. You can still use Portable Hotspot.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            PendingWifiPermissionAction.SCAN_QR -> {
                if (!DropOfflineWifiManager.isLocationEnabled(context)) {
                    locationPromptForAction = PendingWifiPermissionAction.SCAN_QR
                } else {
                    showQrScanner = true
                }
            }
            PendingWifiPermissionAction.NONE -> Unit
        }
    }

    val qrBitmap: Bitmap? = remember(state.activeQrContent, state.isServerRunning) {
        val payload = state.activeQrContent
        if (state.isServerRunning && !payload.isNullOrBlank()) {
            DropQrGenerator.generateQrBitmap(content = payload, pixelSize = 512)
        } else {
            null
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("drop_screen_list"),
        contentPadding = effectiveContentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Optional Status Banner (with 1-tap Copy Password & Open Wi-Fi Settings fallback if auto-join fails)
        if (state.statusBanner != null) {
            item(key = "drop_status_banner") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .testTag("drop_status_banner"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = state.statusBanner.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = viewModel::dismissBanner) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss banner",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        if (state.showWifiFallbackAction && !state.fallbackWifiSsid.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    copyHotspotPasswordAndOpenWifiSettings(
                                        context = context,
                                        ssid = state.fallbackWifiSsid.orEmpty(),
                                        password = state.fallbackWifiPassword,
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("drop_wifi_fallback_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Wifi,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Copy Password & Open Wi-Fi Settings",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 0. Active Native Connected Peer Hub (when connected via QR Scan or akit://drop deep link)
        if (state.connectedPeerUrl != null) {
            item(key = "drop_connected_peer_card") {
                ConnectedPeerHubCard(
                    state = state,
                    snippetDraft = peerSnippetDraft,
                    onSnippetDraftChange = { peerSnippetDraft = it },
                    onSendSnippet = {
                        viewModel.sendSnippetToPeer(peerSnippetDraft)
                        peerSnippetDraft = ""
                    },
                    onUploadPhotos = {
                        peerPhotoUploadLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                        )
                    },
                    onUploadFiles = { peerFileUploadLauncher.launch(arrayOf("*/*")) },
                    onCopySnippet = { copyToClipboard(context, "Peer Snippet", it) },
                    onDownloadFile = viewModel::downloadPeerFile,
                    onOpenDownloadedFile = { openFileUri(context, it) },
                    onDisconnect = viewModel::disconnectPeer,
                    modifier = Modifier.widthIn(max = 640.dp),
                )
            }
        }

        // 1. Hero Card + Master Local Drop Server Toggle + Scan QR to Connect
        item(key = "drop_hero") {
            ModuleHeroCard(
                moduleCode = stringResource(R.string.drop_module_code),
                title = stringResource(R.string.drop_hero_title),
                subtitle = "100% offline P2P Wi-Fi & Hotspot sharing. Scan QR in A-Kit for native speed, or open in any browser with zero app install.",
                icon = Icons.Rounded.WifiTethering,
                accentColor = MaterialTheme.colorScheme.tertiary,
                accentContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onAccentContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .testTag("drop_hero_card"),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (state.isServerRunning) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Device Beacon Row — properly weighted so device model NEVER wraps vertically
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.deviceBeaconName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = state.localIpAddress?.let { "LAN: $it:${state.serverPort}" }
                                        ?: "Connect Wi-Fi or Hotspot",
                                    style = MonoLabelStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (state.isServerRunning) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (state.isServerRunning || state.isWifiEnabled) {
                                                Icons.Rounded.Wifi
                                            } else {
                                                Icons.Rounded.WifiOff
                                            },
                                            contentDescription = null,
                                            tint = if (state.isServerRunning) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = if (state.isServerRunning) {
                                                "LIVE • ${state.activePeerCount}"
                                            } else {
                                                "OFFLINE"
                                            },
                                            style = MonoLabelStyle,
                                            color = if (state.isServerRunning) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = viewModel::refreshNetwork,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("drop_refresh_ip_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Refresh local IP",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        // Primary Actions: Host Server + Built-in QR Scanner to Connect
                        Button(
                            onClick = viewModel::toggleDropServer,
                            shape = RoundedCornerShape(16.dp),
                            colors = if (state.isServerRunning) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("drop_server_toggle_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.isServerRunning) {
                                    "Stop Local Drop Server"
                                } else {
                                    "Start Local Drop Server"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    if (!DropOfflineWifiManager.hasHotspotPermission(context)) {
                                        pendingPermissionAction = PendingWifiPermissionAction.SCAN_QR
                                        hotspotPermissionLauncher.launch(
                                            DropOfflineWifiManager.requiredHotspotPermissions(),
                                        )
                                    } else if (!DropOfflineWifiManager.isLocationEnabled(context)) {
                                        locationPromptForAction = PendingWifiPermissionAction.SCAN_QR
                                    } else {
                                        showQrScanner = true
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("drop_scan_qr_connect_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Scan QR",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (state.isLocalHotspotActive) {
                                        viewModel.stopOfflineHotspot()
                                    } else if (!DropOfflineWifiManager.hasHotspotPermission(context)) {
                                        pendingPermissionAction = PendingWifiPermissionAction.START_HOTSPOT
                                        hotspotPermissionLauncher.launch(
                                            DropOfflineWifiManager.requiredHotspotPermissions(),
                                        )
                                    } else if (!DropOfflineWifiManager.isLocationEnabled(context)) {
                                        locationPromptForAction = PendingWifiPermissionAction.START_HOTSPOT
                                    } else {
                                        viewModel.startOfflineHotspot()
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (state.isLocalHotspotActive) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("drop_offline_hotspot_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WifiTethering,
                                    contentDescription = null,
                                    tint = if (state.isLocalHotspotActive) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (state.isLocalHotspotActive) "Stop Hotspot" else "Offline Hotspot",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }

                        // Always show copyable Offline Hotspot Credentials badge on sender screen as soon as active
                        if (state.isLocalHotspotActive && !state.hotspotSsid.isNullOrBlank()) {
                            HotspotCredentialsBadge(
                                ssid = state.hotspotSsid.orEmpty(),
                                password = state.hotspotPassword.orEmpty(),
                                onCopySsid = {
                                    copyToClipboard(context, "Hotspot Wi-Fi Name", state.hotspotSsid.orEmpty())
                                },
                                onCopyPassword = {
                                    copyToClipboard(context, "Hotspot Password", state.hotspotPassword.orEmpty())
                                },
                            )
                        }
                    }
                }
            }
        }

        // 2. Local URL & Scannable QR Code Card (Visible when Server is Running)
        item(key = "drop_qr_card_wrapper") {
            AnimatedVisibility(visible = state.isServerRunning) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .testTag("drop_qr_url_card"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    text = "Scan with A-Kit or Browser",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "15M AUTO-SLEEP",
                                style = MonoLabelStyle,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }

                        if (qrBitmap != null && state.serverUrl != null) {
                            // Mode switcher when Offline Hotspot is active: A-Kit 1-Tap Auto-Join vs Browser URL
                            if (state.isLocalHotspotActive && !state.hotspotSsid.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterChip(
                                        selected = state.useAutoConnectQr,
                                        onClick = { viewModel.setUseAutoConnectQr(true) },
                                        label = {
                                            Text(
                                                text = "A-Kit 1-Tap QR",
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("drop_qr_mode_autoconnect"),
                                    )
                                    FilterChip(
                                        selected = !state.useAutoConnectQr,
                                        onClick = { viewModel.setUseAutoConnectQr(false) },
                                        label = {
                                            Text(
                                                text = "Browser URL QR",
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("drop_qr_mode_browser"),
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White,
                                shadowElevation = 4.dp,
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR code for ${state.serverUrl}",
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier
                                        .size(210.dp)
                                        .padding(10.dp)
                                        .testTag("drop_qr_bitmap_image"),
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = state.serverUrl.orEmpty(),
                                        style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("drop_server_url_text"),
                                    )
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(context, "Drop URL", state.serverUrl.orEmpty())
                                        },
                                        modifier = Modifier.testTag("drop_copy_url_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = "Copy server URL",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            sharePlainText(context, "Open A-Kit Drop on Wi-Fi: ${state.serverUrl}")
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Share,
                                            contentDescription = "Share server URL",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        } else if (state.isLocalHotspotActive && state.isHotspotResolvingIp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Waiting for Offline Hotspot IP (ap0/wlan0)...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                text = "Server is listening on port ${state.serverPort}, waiting for a local Wi-Fi or Hotspot IPv4 address. Tap Offline Hotspot above, turn on Portable Hotspot, or join Wi-Fi, then tap refresh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // 2A. Live Host Transfer Progress Card (visible while Host is sending or receiving a file)
        val activeHostTransfer = state.activeHostTransfer
        if (activeHostTransfer != null) {
            item(key = "drop_host_live_transfer_card") {
                LiveTransferProgressCard(
                    progress = activeHostTransfer,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .testTag("drop_host_transfer_progress_card"),
                )
            }
        }

        // 2B. Unified Host PEER FILES Card (received peer uploads with explicit Open & Save buttons)
        if (receivedHostPeerFiles.isNotEmpty()) {
            item(key = "drop_host_peer_files_card") {
                HostPeerFilesCard(
                    files = receivedHostPeerFiles,
                    savingFileId = state.savingHostFileId,
                    onOpenFile = { openFileUri(context, it) },
                    onSaveFile = viewModel::saveHostReceivedFile,
                    modifier = Modifier.widthIn(max = 640.dp),
                )
            }
        }

        // 2C. Beginner-Friendly Offline Hotspot / PC Mode Helper Tip Card
        item(key = "drop_offline_hotspot_guide") {
            OfflineHotspotPcGuideCard(
                displayedUrl = state.serverUrl ?: "http://192.168.x.x:${state.serverPort}",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        // 3. Quick Text & Snippet Drop Section
        item(key = "drop_snippets_header") {
            SectionHeader(
                title = "Quick Text & Snippet Drop",
                badgeText = "${state.snippets.size} SYNCED",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "drop_snippet_composer") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .testTag("drop_snippet_composer_card"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = snippetDraft,
                        onValueChange = { snippetDraft = it },
                        placeholder = {
                            Text("Type or paste text, link, or code snippet to drop...")
                        },
                        shape = RoundedCornerShape(16.dp),
                        minLines = 2,
                        maxLines = 5,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val pasted = readFromClipboard(context)
                                    if (!pasted.isNullOrBlank()) {
                                        snippetDraft = pasted
                                    }
                                },
                                modifier = Modifier.testTag("drop_paste_clipboard_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = "Paste from clipboard",
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drop_snippet_input"),
                    )

                    FilledTonalButton(
                        onClick = {
                            viewModel.sendSnippetToPortal(snippetDraft)
                            snippetDraft = ""
                        },
                        enabled = snippetDraft.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("drop_send_snippet_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Send to Portal",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }

        if (state.snippets.isNotEmpty()) {
            items(items = state.snippets, key = { it.id }) { snippet ->
                DropSnippetCard(
                    snippet = snippet,
                    onCopy = { copyToClipboard(context, "Snippet", snippet.text) },
                    onShare = { sharePlainText(context, snippet.text) },
                    modifier = Modifier.widthIn(max = 640.dp),
                )
            }
        }

        // 4. Shared Media & Files Section (files staged from this phone)
        item(key = "drop_files_header") {
            Spacer(modifier = Modifier.height(2.dp))
            SectionHeader(
                title = "Shared Media & Files",
                badgeText = "${stagedPhoneFiles.size} ITEMS",
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        item(key = "drop_files_stage_buttons") {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("drop_add_photos_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share Photos",
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                OutlinedButton(
                    onClick = { documentPickerLauncher.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("drop_add_files_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share Files",
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        if (stagedPhoneFiles.isEmpty()) {
            item(key = "drop_files_empty") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .testTag("drop_files_empty_card"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "No Files Staged From This Phone",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Pick photos or files above to serve them to nearby peers, or receive peer uploads in the PEER FILES card above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(items = stagedPhoneFiles, key = { it.id }) { fileItem ->
                DropFileCard(
                    fileItem = fileItem,
                    onOpen = { openFileUri(context, fileItem) },
                    onShare = { shareFileUri(context, fileItem) },
                    modifier = Modifier.widthIn(max = 640.dp),
                )
            }
        }
    }

    // Built-in Offline CameraX + ZXing QR Scanner Sheet
    if (showQrScanner) {
        DropQrScannerSheet(
            onScannedPayload = { payload ->
                showQrScanner = false
                viewModel.connectFromQrPayload(payload)
            },
            onDismiss = { showQrScanner = false },
        )
    }

    // Prompt if device GPS/Location is OFF before starting Offline Hotspot or QR auto-join
    if (locationPromptForAction != PendingWifiPermissionAction.NONE) {
        val action = locationPromptForAction
        AlertDialog(
            onDismissRequest = { locationPromptForAction = PendingWifiPermissionAction.NONE },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = {
                Text(
                    text = "Turn On Location for Wi-Fi Direct",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = "Android requires device Location (GPS) to be turned ON to host an Offline Hotspot or auto-join a nearby hotspot via QR code. A-Kit never tracks or uploads your location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        locationPromptForAction = PendingWifiPermissionAction.NONE
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    },
                    modifier = Modifier.testTag("drop_enable_location_button"),
                ) {
                    Text("Enable Location")
                }
            },
            dismissButton = {
                if (action == PendingWifiPermissionAction.SCAN_QR) {
                    TextButton(
                        onClick = {
                            locationPromptForAction = PendingWifiPermissionAction.NONE
                            showQrScanner = true
                        },
                    ) {
                        Text("Scan Anyway")
                    }
                } else {
                    TextButton(
                        onClick = { locationPromptForAction = PendingWifiPermissionAction.NONE },
                    ) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
}

@Composable
private fun HotspotCredentialsBadge(
    ssid: String,
    password: String,
    onCopySsid: () -> Unit,
    onCopyPassword: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("drop_hotspot_credentials_badge"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "OFFLINE HOTSPOT CREDENTIALS (TAP TO COPY)",
                style = MonoLabelStyle,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Full-width SSID row so long AndroidShare_xxxx names are never clipped
            Surface(
                onClick = onCopySsid,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drop_hotspot_ssid_copy_row"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WI-FI NAME (SSID)",
                            style = MonoLabelStyle.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = ssid,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy hotspot SSID",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            // Full-width PASSWORD row so complete 15+ char passphrases display untruncated
            if (password.isNotBlank()) {
                Surface(
                    onClick = onCopyPassword,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drop_hotspot_password_copy_row"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FULL PASSWORD (${password.length} CHARS)",
                                style = MonoLabelStyle.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = password,
                                style = MonoLabelStyle.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    letterSpacing = 0.6.sp,
                                ),
                                color = Color(0xFF00E5FF),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy full hotspot password",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTransferProgressCard(
    progress: DropTransferProgress,
    modifier: Modifier = Modifier,
) {
    val electricCyan = Color(0xFF00E5FF)
    val animatedProgress by animateFloatAsState(
        targetValue = progress.progressFraction ?: 0f,
        animationSpec = tween(durationMillis = 140),
        label = "drop_transfer_progress_anim",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        border = BorderStroke(1.2.dp, electricCyan.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = electricCyan.copy(alpha = 0.16f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (progress.isSending) Icons.Rounded.UploadFile else Icons.Rounded.Download,
                                contentDescription = null,
                                tint = electricCyan,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = if (progress.isSending) "SENDING" else "RECEIVING",
                                style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = electricCyan,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    Text(
                        text = progress.fileName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = progress.percentInt?.let { "$it%" } ?: "LIVE",
                    style = MonoLabelStyle.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                    color = electricCyan,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            if (progress.progressFraction != null) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = electricCyan,
                    trackColor = electricCyan.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                )
            } else {
                LinearProgressIndicator(
                    color = electricCyan,
                    trackColor = electricCyan.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = progress.formatTransferredSummary(),
                    style = MonoLabelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = progress.formatSpeed(),
                    style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
                    color = electricCyan,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun HostPeerFilesCard(
    files: List<DropFileItem>,
    savingFileId: String?,
    onOpenFile: (DropFileItem) -> Unit,
    onSaveFile: (DropFileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val savedGreen = Color(0xFF00E676)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("drop_host_peer_files_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "PEER FILES (${files.size})",
                    style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "RECEIVED ON HOST",
                    style = MonoLabelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            files.forEach { fileItem ->
                val isSaving = savingFileId == fileItem.id
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drop_host_received_file_${fileItem.id}"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (fileItem.isImage) Icons.Rounded.Image else Icons.Rounded.Description,
                                    contentDescription = fileItem.fileName,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fileItem.fileName,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${fileItem.formatSize()} • ${fileItem.savedLocationLabel}",
                                    style = MonoLabelStyle,
                                    color = if (fileItem.isSavedToDownloads) savedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenFile(fileItem) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("drop_host_open_file_${fileItem.id}"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Open",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }

                            if (fileItem.isSavedToDownloads) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = savedGreen.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, savedGreen.copy(alpha = 0.55f)),
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .height(40.dp)
                                        .testTag("drop_host_saved_badge_${fileItem.id}"),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = savedGreen,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Saved to Downloads/A-Kit",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = savedGreen,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { onSaveFile(fileItem) },
                                    enabled = !isSaving,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("drop_host_save_file_${fileItem.id}"),
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Save",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineHotspotPcGuideCard(
    displayedUrl: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("drop_offline_hotspot_guide_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                ) {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Offline Hotspot / PC Mode",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "ZERO INTERNET OR MOBILE DATA REQUIRED",
                        style = MonoLabelStyle,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            OfflineGuideStepRow(
                stepNumber = "1",
                text = "Tap Offline Hotspot above or turn on Portable Hotspot (keep Mobile Data OFF — zero data is used).",
            )
            OfflineGuideStepRow(
                stepNumber = "2",
                text = "Connect your PC or second phone to the hotspot, or scan the QR inside A-Kit for 1-tap auto-join.",
            )
            OfflineGuideStepRow(
                stepNumber = "3",
                text = "Open $displayedUrl in any browser on PC/Mac/iPhone or transfer directly inside A-Kit.",
            )
        }
    }
}

@Composable
private fun OfflineGuideStepRow(
    stepNumber: String,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stepNumber,
                    style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ConnectedPeerHubCard(
    state: DropUiState,
    snippetDraft: String,
    onSnippetDraftChange: (String) -> Unit,
    onSendSnippet: () -> Unit,
    onUploadPhotos: () -> Unit,
    onUploadFiles: () -> Unit,
    onCopySnippet: (String) -> Unit,
    onDownloadFile: (PeerFileItem) -> Unit,
    onOpenDownloadedFile: (DropFileItem) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectedGreen = Color(0xFF00E676)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("drop_connected_peer_hub"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 4.dp,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (state.isPeerConnected) {
                connectedGreen.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (state.isPeerConnected) {
                            connectedGreen.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (state.isPeerConnected) Icons.Rounded.CheckCircle else Icons.Rounded.Smartphone,
                                contentDescription = null,
                                tint = if (state.isPeerConnected) {
                                    connectedGreen
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isPeerConnected) {
                                state.connectedPeerDeviceName?.let { "Connected • $it" } ?: "Connected"
                            } else {
                                "Connecting to Peer..."
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (state.isPeerConnected) connectedGreen else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.connectedPeerUrl.orEmpty(),
                            style = MonoLabelStyle,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!state.connectedWifiSsid.isNullOrBlank()) {
                            Text(
                                text = "HOTSPOT: ${state.connectedWifiSsid}",
                                style = MonoLabelStyle,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDisconnect,
                    modifier = Modifier.testTag("drop_peer_disconnect_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LinkOff,
                        contentDescription = "Disconnect from peer",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (!state.isPeerConnected && !state.fallbackWifiSsid.isNullOrBlank()) {
                val peerCtx = LocalContext.current
                FilledTonalButton(
                    onClick = {
                        copyHotspotPasswordAndOpenWifiSettings(
                            context = peerCtx,
                            ssid = state.fallbackWifiSsid.orEmpty(),
                            password = state.fallbackWifiPassword,
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drop_peer_wifi_fallback_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy Password & Open Wi-Fi Settings",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // Dynamic Live Transfer Progress Bar inside Peer Hub (Sending & Receiving)
            if (state.activePeerTransfer != null) {
                LiveTransferProgressCard(
                    progress = state.activePeerTransfer,
                    modifier = Modifier.testTag("drop_peer_transfer_progress_card"),
                )
            }

            // Send quick text to peer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = snippetDraft,
                    onValueChange = onSnippetDraftChange,
                    placeholder = { Text("Send snippet to peer...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onSendSnippet,
                    enabled = snippetDraft.isNotBlank() && state.isPeerConnected,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Send,
                        contentDescription = "Send snippet to peer",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Upload directly to peer's Downloads/A-Kit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onUploadPhotos,
                    enabled = state.isPeerConnected,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Photos", maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = onUploadFiles,
                    enabled = state.isPeerConnected,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Files", maxLines = 1, softWrap = false)
                }
            }

            // Remote Peer Snippets
            if (state.peerSnippets.isNotEmpty()) {
                Text(
                    text = "PEER SNIPPETS (${state.peerSnippets.size})",
                    style = MonoLabelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.peerSnippets.take(4).forEach { snip ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = snip.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onCopySnippet(snip.text) }) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy peer snippet",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Remote Peer Files (1-tap Save to Downloads/A-Kit + Open once saved)
            if (state.peerFiles.isNotEmpty()) {
                Text(
                    text = "PEER FILES (${state.peerFiles.size})",
                    style = MonoLabelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.peerFiles.take(8).forEach { pf ->
                    val isDownloading = state.downloadingPeerFileId == pf.id
                    val downloadedItem = state.downloadedPeerFiles[pf.id]
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pf.fileName,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (downloadedItem != null) {
                                            "${pf.sizeLabel} • Saved to Downloads/A-Kit"
                                        } else {
                                            "${pf.sizeLabel} • ${pf.sourceLabel}"
                                        },
                                        style = MonoLabelStyle,
                                        color = if (downloadedItem != null) connectedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else if (downloadedItem != null) {
                                    FilledTonalButton(
                                        onClick = { onOpenDownloadedFile(downloadedItem) },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.OpenInNew,
                                            contentDescription = "Open downloaded file",
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Open", maxLines = 1, softWrap = false)
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { onDownloadFile(pf) },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = "Download file",
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save", maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropSnippetCard(
    snippet: DropSnippet,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeStr = remember(snippet.timestampMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snippet.timestampMs))
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("drop_snippet_${snippet.id}"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (snippet.direction == DropDirection.FROM_BROWSER) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                    },
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = "${snippet.senderLabel.uppercase(Locale.US)} • $timeStr",
                        style = MonoLabelStyle,
                        color = if (snippet.direction == DropDirection.FROM_BROWSER) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }

                Row {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.testTag("drop_copy_snippet_${snippet.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy snippet",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share snippet",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = snippet.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun DropFileCard(
    fileItem: DropFileItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .testTag("drop_file_${fileItem.id}"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (fileItem.isImage) Icons.Rounded.Image else Icons.Rounded.Description,
                    contentDescription = fileItem.fileName,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileItem.fileName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${fileItem.formatSize()} • ${fileItem.savedLocationLabel}",
                    style = MonoLabelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onOpen) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = "Open file",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Share file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun copyHotspotPasswordAndOpenWifiSettings(
    context: Context,
    ssid: String,
    password: String?,
) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (!password.isNullOrBlank()) {
        cm?.setPrimaryClip(ClipData.newPlainText("Hotspot Password", password))
        Toast.makeText(
            context,
            "Password copied! Join \"$ssid\" in Wi-Fi Settings, then return to A-Kit.",
            Toast.LENGTH_LONG,
        ).show()
    } else {
        Toast.makeText(
            context,
            "Join \"$ssid\" in Wi-Fi Settings, then return to A-Kit.",
            Toast.LENGTH_LONG,
        ).show()
    }
    runCatching {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}

private fun readFromClipboard(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0)?.coerceToText(context)?.toString()
}

private fun sharePlainText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share via")) }
}

private fun openFileUri(context: Context, item: DropFileItem) {
    val resolvedUri = item.localFilePath
        ?.let { File(it) }
        ?.takeIf { it.exists() && it.canRead() }
        ?.let { file ->
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.drop.fileprovider",
                    file,
                )
            }.getOrNull()
        }
        ?: runCatching { Uri.parse(item.uriString) }.getOrNull()
        ?: return

    val resolvedMime = DropHubCoordinator.resolveProperMimeType(item.fileName, item.mimeType)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(resolvedUri, resolvedMime)
        clipData = ClipData.newRawUri(item.fileName, resolvedUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Open ${item.fileName}"))
    }.onFailure {
        Toast.makeText(context, "No app available to open this file type", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFileUri(context: Context, item: DropFileItem) {
    val resolvedUri = item.localFilePath
        ?.let { File(it) }
        ?.takeIf { it.exists() && it.canRead() }
        ?.let { file ->
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.drop.fileprovider",
                    file,
                )
            }.getOrNull()
        }
        ?: runCatching { Uri.parse(item.uriString) }.getOrNull()
        ?: return

    val resolvedMime = DropHubCoordinator.resolveProperMimeType(item.fileName, item.mimeType)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = resolvedMime
        putExtra(Intent.EXTRA_STREAM, resolvedUri)
        clipData = ClipData.newRawUri(item.fileName, resolvedUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share ${item.fileName}")) }
}
