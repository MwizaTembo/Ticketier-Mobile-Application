package com.example.ticketier.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Turns text into a QR code image using the ZXing library.
 * How it works: ZXing returns a BitMatrix (a grid of true/false = dark/light squares).
 * We paint each square black or white into a Bitmap.
 *
 * The QR is always BLACK on WHITE, never themed. Scanners need strong contrast, and an
 * inverted (light-on-dark) code can fail to scan, so dark mode must not change it.
 */
fun generateQrBitmap(content: String, sizePx: Int = 768): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 0, // we add the white "quiet zone" in the UI instead
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M // still scans if slightly damaged
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

    val pixels = IntArray(sizePx * sizePx) { i ->
        val x = i % sizePx
        val y = i / sizePx
        if (matrix.get(x, y)) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
}