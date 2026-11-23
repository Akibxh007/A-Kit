package com.example.feature.drop

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MonoLabelStyle
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.delay
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropQrScannerSheet(
    onScannedPayload: (DropQrPayload) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var manualUrlInput by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Clear transient invalid-QR error after 2 seconds so user knows scanner is still live
    LaunchedEffect(scanError) {
        if (scanError != null) {
            delay(2_200L)
            scanError = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("drop_qr_scanner_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Scan Drop QR Code",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close scanner",
                    )
                }
            }

            // CameraX + ZXing Offline QR Viewfinder
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF070A0E),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f),
            ) {
                if (hasCameraPermission) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        OfflineCameraQrPreview(
                            torchEnabled = torchEnabled,
                            onQrDetected = { rawPayload ->
                                val parsed = DropUrlParser.parseQrPayload(rawPayload)
                                if (parsed != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    triggerScanPulseVibration(context)
                                    onScannedPayload(parsed)
                                    true
                                } else {
                                    scanError = "Not an A-Kit Drop or Wi-Fi Hotspot QR code"
                                    false
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp)),
                        )

                        // Precision corner brackets viewfinder overlay
                        val bracketColor = MaterialTheme.colorScheme.primary
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val boxSize = size.minDimension * 0.64f
                            val left = (size.width - boxSize) / 2f
                            val top = (size.height - boxSize) / 2f
                            val right = left + boxSize
                            val bottom = top + boxSize
                            val corner = 26.dp.toPx()
                            val stroke = 3.5.dp.toPx()

                            // Top-left
                            drawLine(bracketColor, Offset(left, top + corner), Offset(left, top), stroke, StrokeCap.Round)
                            drawLine(bracketColor, Offset(left, top), Offset(left + corner, top), stroke, StrokeCap.Round)
                            // Top-right
                            drawLine(bracketColor, Offset(right - corner, top), Offset(right, top), stroke, StrokeCap.Round)
                            drawLine(bracketColor, Offset(right, top), Offset(right, top + corner), stroke, StrokeCap.Round)
                            // Bottom-left
                            drawLine(bracketColor, Offset(left, bottom - corner), Offset(left, bottom), stroke, StrokeCap.Round)
                            drawLine(bracketColor, Offset(left, bottom), Offset(left + corner, bottom), stroke, StrokeCap.Round)
                            // Bottom-right
                            drawLine(bracketColor, Offset(right - corner, bottom), Offset(right, bottom), stroke, StrokeCap.Round)
                            drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - corner), stroke, StrokeCap.Round)
                        }

                        // Torch toggle button
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.52f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                        ) {
                            IconButton(
                                onClick = { torchEnabled = !torchEnabled },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("drop_qr_torch_button"),
                            ) {
                                Icon(
                                    imageVector = if (torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                                    contentDescription = if (torchEnabled) "Turn off flashlight" else "Turn on flashlight",
                                    tint = if (torchEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        // Live scanner status pill
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.58f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                        ) {
                            Text(
                                text = "SCANNING A-KIT OR HOTSPOT QR...",
                                style = MonoLabelStyle,
                                color = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0A0E14))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Camera Permission Needed",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Grant camera access to scan another device's A-Kit Drop or Offline Hotspot QR code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        FilledTonalButton(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Grant Camera Access")
                        }
                    }
                }
            }

            if (scanError != null) {
                Text(
                    text = scanError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Manual LAN URL fallback
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = manualUrlInput,
                    onValueChange = {
                        manualUrlInput = it
                        scanError = null
                    },
                    placeholder = { Text("Or enter IP e.g. 192.168.43.1:8080") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("drop_manual_peer_url_input"),
                )
                Button(
                    onClick = {
                        val parsed = DropUrlParser.parseQrPayload(manualUrlInput)
                        if (parsed != null) {
                            onScannedPayload(parsed)
                        } else {
                            scanError = "Enter a valid local IP address (e.g. 192.168.1.5:8080)"
                        }
                    },
                    enabled = manualUrlInput.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("drop_manual_peer_connect_button"),
                ) {
                    Text(
                        text = "Connect",
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * Live CameraX + ZXing QR analyzer.
 *
 * Key fixes in v1.1.0:
 * - Tightly repacks CameraX YUV_420_888 Y-plane rows into `width * height` bytes so
 *   `PlanarYUVLuminanceSource` never throws `IllegalArgumentException` when `rowStride > width`.
 * - Multi-pass decoding (`HybridBinarizer` -> `GlobalHistogramBinarizer` -> inverted luminance)
 *   with `DecodeHintType.TRY_HARDER = true` for reliable scanning off glossy/dark screens.
 * - Unbinds CameraX immediately upon valid scan so the sheet dismisses cleanly without hanging.
 */
@Composable
private fun OfflineCameraQrPreview(
    torchEnabled: Boolean,
    onQrDetected: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnQrDetected by rememberUpdatedState(onQrDetected)
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanAccepted = remember { AtomicBoolean(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    val qrReader = remember {
        MultiFormatReader().apply {
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
                put(DecodeHintType.TRY_HARDER, true)
                put(DecodeHintType.CHARACTER_SET, "UTF-8")
            }
            setHints(hints)
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(torchEnabled, boundCamera) {
        runCatching {
            val cam = boundCamera
            if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(torchEnabled)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val listener = Runnable {
            runCatching {
                val cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                            try {
                                if (!scanAccepted.get()) {
                                    val rawText = decodeQrFromImageProxy(imageProxy, qrReader)
                                    if (!rawText.isNullOrBlank()) {
                                        mainExecutor.execute {
                                            if (!scanAccepted.get()) {
                                                val accepted = latestOnQrDetected(rawText)
                                                if (accepted) {
                                                    scanAccepted.set(true)
                                                    runCatching { cameraProvider.unbindAll() }
                                                }
                                            }
                                        }
                                    }
                                }
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                cameraProvider.unbindAll()
                boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }

        providerFuture.addListener(listener, mainExecutor)

        onDispose {
            runCatching {
                if (providerFuture.isDone) {
                    providerFuture.get().unbindAll()
                }
            }
            boundCamera = null
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

private fun decodeQrFromImageProxy(
    imageProxy: ImageProxy,
    reader: MultiFormatReader,
): String? {
    val width = imageProxy.width
    val height = imageProxy.height
    if (width <= 0 || height <= 0) return null

    val yBytes = copyYPlaneCompact(imageProxy, width, height) ?: return null

    val source = PlanarYUVLuminanceSource(
        yBytes,
        width,
        height,
        0,
        0,
        width,
        height,
        false,
    )

    // Pass 1: HybridBinarizer (best for uneven lighting / phone screens)
    decodeWithReader(reader, BinaryBitmap(HybridBinarizer(source)))?.let { return it }
    // Pass 2: GlobalHistogramBinarizer (faster/cleaner on high-contrast matte prints & bright screens)
    decodeWithReader(reader, BinaryBitmap(GlobalHistogramBinarizer(source)))?.let { return it }
    // Pass 3: Inverted luminance (dark-mode QR codes or reflections)
    decodeWithReader(reader, BinaryBitmap(HybridBinarizer(source.invert())))?.let { return it }

    return null
}

private fun decodeWithReader(reader: MultiFormatReader, bitmap: BinaryBitmap): String? {
    return try {
        reader.decodeWithState(bitmap).text
    } catch (_: Throwable) {
        null
    } finally {
        reader.reset()
    }
}

/**
 * Safely copies the CameraX YUV_420_888 Y-plane into a tightly packed [width] x [height]
 * grayscale byte array, stripping any row-stride alignment padding so ZXing never throws
 * `IllegalArgumentException` on padded buffers.
 */
private fun copyYPlaneCompact(
    imageProxy: ImageProxy,
    width: Int,
    height: Int,
): ByteArray? {
    val plane = imageProxy.planes.firstOrNull() ?: return null
    val buffer = plane.buffer.duplicate()
    buffer.rewind()
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val output = ByteArray(width * height)

    if (pixelStride == 1 && rowStride == width) {
        val count = minOf(output.size, buffer.remaining())
        buffer.get(output, 0, count)
        return output
    }

    val rowBuffer = ByteArray(rowStride)
    val bufferLimit = buffer.limit()
    for (row in 0 until height) {
        val rowStart = row * rowStride
        if (rowStart >= bufferLimit) break
        val available = minOf(rowStride, bufferLimit - rowStart)
        buffer.position(rowStart)
        buffer.get(rowBuffer, 0, available)

        val destOffset = row * width
        if (pixelStride == 1) {
            val copyLen = minOf(width, available)
            System.arraycopy(rowBuffer, 0, output, destOffset, copyLen)
        } else {
            var col = 0
            while (col < width) {
                val srcIdx = col * pixelStride
                if (srcIdx >= available) break
                output[destOffset + col] = rowBuffer[srcIdx]
                col++
            }
        }
    }
    return output
}

private fun triggerScanPulseVibration(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
