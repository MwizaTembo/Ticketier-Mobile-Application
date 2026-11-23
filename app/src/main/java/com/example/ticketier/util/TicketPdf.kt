package com.example.ticketier.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.example.ticketier.R
import com.example.ticketier.data.Ticket
import com.example.ticketier.data.formatMwk
import com.example.ticketier.ui.theme.TicketierPinkDeep
import com.example.ticketier.ui.theme.TicketierPurple

/**
 * Draws the ticket onto a one-page PDF and writes it to `uri`
 * (the file location the user picked in the system "Save as" screen).
 * Uses Android's built-in PdfDocument, so no extra library is needed.
 * Returns true if it worked.
 *
 * A PDF page is measured in points: 595 x 842 is A4. We draw with the classic Android
 * Canvas (not Compose): drawRoundRect, drawText, drawBitmap...
 */
fun writeTicketPdf(context: Context, uri: Uri, ticket: Ticket, qr: Bitmap): Boolean {
    val pdf = PdfDocument()
    return try {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        drawTicket(context, page.canvas, ticket, qr)
        pdf.finishPage(page)

        val output = context.contentResolver.openOutputStream(uri) ?: return false
        output.use { pdf.writeTo(it) }
        true
    } catch (e: Exception) {
        false
    } finally {
        pdf.close()
    }
}

private fun drawTicket(context: Context, c: Canvas, t: Ticket, qr: Bitmap) {
    val left = 40f
    val right = 555f
    val centerX = 297.5f
    val dark = Color.rgb(29, 27, 32)
    val gray = Color.rgb(107, 101, 119)
    val wordmarkFont = ResourcesCompat.getFont(context, R.font.grand_hotel) ?: Typeface.DEFAULT_BOLD

    fun paint(size: Float, color: Int, face: Typeface = Typeface.DEFAULT, align: Paint.Align = Paint.Align.LEFT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = face; textAlign = align
        }

    // Cuts text with "…" if it's too wide for the card.
    fun fit(text: String, p: TextPaint, width: Float) =
        TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString()

    c.drawColor(Color.WHITE)

    // --- Header: purple-to-pink gradient card ---
    val gradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(left, 60f, right, 300f,
            TicketierPurple.toArgb(), TicketierPinkDeep.toArgb(), Shader.TileMode.CLAMP)
    }
    c.drawRoundRect(RectF(left, 60f, right, 300f), 28f, 28f, gradient)

    c.drawText("Ticketier", 70f, 120f, paint(40f, Color.WHITE, wordmarkFont))
    val artistPaint = paint(30f, Color.WHITE, Typeface.DEFAULT_BOLD)
    c.drawText(fit(t.artist, artistPaint, 455f), 70f, 200f, artistPaint)
    c.drawText("${t.day} ${t.month} · ${t.time}", 70f, 236f, paint(18f, Color.WHITE))
    val venuePaint = paint(16f, Color.argb(230, 255, 255, 255))
    c.drawText(fit("${t.venue}, ${t.location}", venuePaint, 455f), 70f, 264f, venuePaint)

    // --- Ticket type ---
    c.drawText("ADMIT ONE", centerX, 345f, paint(13f, gray, Typeface.DEFAULT_BOLD, Paint.Align.CENTER))
    val tierPaint = paint(20f, dark, Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
    c.drawText(fit("${t.tierName} · ${formatMwk(t.price)}", tierPaint, 455f), centerX, 376f, tierPaint)

    // --- Dashed "tear here" line ---
    val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(203, 196, 207); strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    c.drawLine(left + 10f, 408f, right - 10f, 408f, dashed)

    // --- QR code (white box so the quiet zone is preserved) ---
    val box = RectF(centerX - 120f, 430f, centerX + 120f, 670f)
    c.drawRoundRect(box, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    c.drawRoundRect(box, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(203, 196, 207); style = Paint.Style.STROKE; strokeWidth = 1.5f
    })
    val qrRect = RectF(box.left + 14f, box.top + 14f, box.right - 14f, box.bottom - 14f)
    c.drawBitmap(qr, null, qrRect, Paint(Paint.FILTER_BITMAP_FLAG))

    // --- Serial number ---
    val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    c.drawText(t.serial, centerX, 712f, paint(22f, dark, mono, Paint.Align.CENTER))
    c.drawText("SERIAL NUMBER", centerX, 734f, paint(12f, gray, Typeface.DEFAULT, Paint.Align.CENTER))

    // --- Footer ---
    c.drawText("Show this QR code at the entrance.", centerX, 790f,
        paint(13f, gray, Typeface.DEFAULT, Paint.Align.CENTER))
    c.drawText("Each code works once and is invalid after it has been scanned.", centerX, 810f,
        paint(13f, gray, Typeface.DEFAULT, Paint.Align.CENTER))
}