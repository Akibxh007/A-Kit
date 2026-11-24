package com.example.feature.drop

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * Offline ISO/IEC 18004 QR Code Generator backed by ZXing [QRCodeWriter] (Versions 1..40).
 *
 * Supports full, untruncated UTF-8 payloads (up to 2,953 bytes) so A-Kit 1-Tap QR payloads
 * containing the server URL, complete hotspot SSID, and full 15–63 character WPA2 passphrase
 * are ALWAYS encoded completely with zero byte clipping.
 */
object DropQrGenerator {

    fun generateQrBitmap(
        content: String,
        pixelSize: Int = 512,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE,
    ): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 2)
        }

        // Encode the raw unscaled module matrix first, then nearest-neighbor scale by an integer
        // factor so every QR module stays razor-sharp on high-DPI displays.
        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            hints,
        )

        val matrixWidth = bitMatrix.width
        val matrixHeight = bitMatrix.height
        val scale = (pixelSize / maxOf(matrixWidth, matrixHeight)).coerceAtLeast(4)
        val outWidth = matrixWidth * scale
        val outHeight = matrixHeight * scale
        val pixels = IntArray(outWidth * outHeight) { lightColor }

        for (y in 0 until matrixHeight) {
            for (x in 0 until matrixWidth) {
                if (bitMatrix.get(x, y)) {
                    val startX = x * scale
                    val startY = y * scale
                    for (dy in 0 until scale) {
                        val rowOffset = (startY + dy) * outWidth + startX
                        for (dx in 0 until scale) {
                            pixels[rowOffset + dx] = darkColor
                        }
                    }
                }
            }
        }

        return Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, outWidth, 0, 0, outWidth, outHeight)
        }
    }
}
