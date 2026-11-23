package com.example.ticketier.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on a phone/emulator because Bitmap is an Android class.
 * The key test decodes the generated image with ZXing's reader: if the serial comes back
 * out, a real scanner can read the code too.
 */
class QrCodeTest {

    // The app shows the QR on a white box with padding (the "quiet zone" scanners need).
    // We add the same white border here before decoding.
    private fun withQuietZone(qr: Bitmap, pad: Int = 80): Bitmap {
        val out = Bitmap.createBitmap(qr.width + 2 * pad, qr.height + 2 * pad, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(qr, pad.toFloat(), pad.toFloat(), null)
        }
        return out
    }

    private fun decode(bitmap: Bitmap): String {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val source = RGBLuminanceSource(w, h, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test fun A_QR_01_decodesBackToSerial() {
        val serial = "TKT-001-7K3M9QXD"
        assertEquals(serial, decode(withQuietZone(generateQrBitmap(serial))))
    }

    @Test fun A_QR_02_hasRequestedSize() {
        val bitmap = generateQrBitmap("TKT-001-7K3M9QXD", sizePx = 300)
        assertEquals(300, bitmap.width)
        assertEquals(300, bitmap.height)
    }

    @Test fun A_QR_03_sameSerialProducesSameImage() {
        val a = generateQrBitmap("TKT-002-AAAAAAAA", 200)
        val b = generateQrBitmap("TKT-002-AAAAAAAA", 200)
        assertTrue(a.sameAs(b))
    }

    @Test fun A_QR_04_differentSerialsProduceDifferentImages() {
        val a = generateQrBitmap("TKT-002-AAAAAAAA", 200)
        val b = generateQrBitmap("TKT-002-BBBBBBBB", 200)
        assertFalse(a.sameAs(b))
    }

    @Test fun A_QR_05_usesOnlyBlackAndWhitePixels() {
        val bitmap = generateQrBitmap("TKT-003-CCCCCCCC", 200)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.all { it == Color.BLACK || it == Color.WHITE })
    }
}