/*
 * PhotoStore.kt
 * MacroDime
 *
 * Progress photos, kept in the app's private files directory.
 *
 * A picked photo is decoded, scaled to at most 2048 px on its long side and
 * re-encoded as JPEG. That does three things: the stored copy survives the
 * original being deleted from the gallery; a 12-megapixel photo costs about
 * 400 KB instead of 5 MB; and re-encoding drops the EXIF block, so the copy
 * carries no GPS location or camera serial even though it never leaves the
 * phone.
 *
 * The photo is chosen through the system photo picker, which needs no storage
 * permission: the app only ever sees the one image the user selected. That is
 * the access Google Play's photo and video permissions policy expects of an
 * app that reads a photo occasionally.
 */
package com.lungelo.macrodime.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlin.math.max

class PhotoStore(context: Context) {

    private val resolver = context.contentResolver
    private val directory = File(context.filesDir, "progress-photos")

    fun file(name: String): File = File(directory, name)

    /**
     * Copies the picked image in and returns its stored file name, or null when
     * the image cannot be read. Runs on the caller's thread: call off main.
     */
    fun importPicked(uri: Uri): String? {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)

        // Read the size first, so a huge image is decoded at a fraction of its
        // resolution rather than into a 50 MB bitmap.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val scaled = scaleAndRotate(decoded, orientation)
        directory.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        return runCatching {
            file(name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            name
        }.getOrNull()
    }

    fun delete(name: String) {
        file(name).delete()
    }

    /** Removes every stored photo. Part of Delete All My Data. */
    fun deleteAll() {
        directory.deleteRecursively()
    }

    private fun scaleAndRotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        val scale = if (longest > MAX_EDGE) MAX_EDGE.toFloat() / longest else 1f
        if (scale == 1f && degrees == 0) return bitmap
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(degrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val MAX_EDGE = 2048
    }
}
