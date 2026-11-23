package com.example.ticketier.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.example.ticketier.data.remote.SupabaseConfig
import com.example.ticketier.data.remote.SupabaseProvider
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * CONCEPT: Supabase Storage keeps files (here, event pictures) in a "bucket".
 * Steps: shrink the photo on the phone (a phone photo can be 5 MB or more), upload it
 * to the `event-images` bucket, and return its public web address. That address is what
 * we save in the event's `image_url` column and what Coil downloads to show the picture.
 */
object ImageUploader {
    private const val TAG = "ImageUploader"
    private const val BUCKET = "event-images"
    private const val MAX_SIDE = 1280 // longest side in pixels after shrinking

    /** Returns the picture's public URL, or null if anything went wrong. */
    suspend fun uploadEventImage(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val bytes = prepareJpeg(context, uri) ?: return@withContext null
                val path = "events/${UUID.randomUUID()}.jpg"
                SupabaseProvider.client.storage.from(BUCKET).upload(path, bytes)
                "${SupabaseConfig.URL}/storage/v1/object/public/$BUCKET/$path"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image upload failed", e)
                null
            }
        }

    /** Reads the chosen picture, fixes its rotation, shrinks it and turns it into JPEG bytes. */
    private fun prepareJpeg(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver

        // 1) Read only the picture's size (cheap), to decide how much to shrink it while loading.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2

        // 2) Load the (already smaller) picture.
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val loaded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        // 3) Phone cameras store "rotate me" in the file. Read it so portrait photos stay upright.
        val rotation = try {
            resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }

        // 4) Rotate and shrink to the final size.
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(loaded.width, loaded.height))
        val finalBitmap = if (rotation != 0f || scale < 1f) {
            val matrix = Matrix().apply {
                if (rotation != 0f) postRotate(rotation)
                if (scale < 1f) postScale(scale, scale)
            }
            Bitmap.createBitmap(loaded, 0, 0, loaded.width, loaded.height, matrix, true)
        } else {
            loaded
        }

        // 5) JPEG at 80% quality is small but still looks good.
        val out = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }
}