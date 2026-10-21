package com.bitchat.android.features.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageUtils {
    fun downscaleAndSaveToAppFiles(context: Context, uri: Uri, maxDim: Int = 512, quality: Int = 85): String? =
        saveScaled(context, maxDim, quality) { context.contentResolver.openInputStream(uri) }

    fun downscalePathAndSaveToAppFiles(context: Context, path: String, maxDim: Int = 512, quality: Int = 85): String? =
        saveScaled(context, maxDim, quality) { File(path).inputStream() }

    /** Read dimensions before allocating pixels, including for very large camera originals. */
    private fun decodeSampled(maxDim: Int, open: () -> InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val probe = open() ?: return null
        probe.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return open()?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun saveScaled(context: Context, maxDim: Int, quality: Int, open: () -> InputStream?): String? {
        var bitmap: Bitmap? = null
        var target: File? = null
        return try {
            require(maxDim in 1..2048)
            val rotation = open()?.use { getRotationDegreesFromExif(it) } ?: 0
            bitmap = decodeSampled(maxDim, open) ?: return null
            if (rotation != 0) bitmap = rotateBitmap(bitmap, rotation)
            val current = requireNotNull(bitmap)
            val scale = (maxOf(current.width, current.height).toFloat() / maxDim).coerceAtLeast(1f)
            if (scale > 1f) {
                bitmap = Bitmap.createScaledBitmap(current,
                    (current.width / scale).toInt().coerceAtLeast(1),
                    (current.height / scale).toInt().coerceAtLeast(1), true)
                if (bitmap !== current) current.recycle()
            }
            val dir = File(context.filesDir, "images/outgoing").apply { check(isDirectory || mkdirs()) }
            target = File.createTempFile("img_", ".jpg", dir)
            var jpegQuality = quality.coerceIn(25, 95)
            while (true) {
                FileOutputStream(target).use { check(requireNotNull(bitmap).compress(Bitmap.CompressFormat.JPEG, jpegQuality, it)) }
                if (target.length() <= com.bitchat.android.util.AppConstants.Media.MAX_FILE_SIZE_BYTES) break
                if (jpegQuality > 25) {
                    jpegQuality = (jpegQuality - 15).coerceAtLeast(25)
                } else {
                    val old = requireNotNull(bitmap)
                    check(old.width > 1 || old.height > 1) { "Image cannot fit mesh transfer limit" }
                    bitmap = Bitmap.createScaledBitmap(old, (old.width / 2).coerceAtLeast(1), (old.height / 2).coerceAtLeast(1), true)
                    if (bitmap !== old) old.recycle()
                }
            }
            target.absolutePath
        } catch (_: Exception) {
            target?.delete()
            null
        } finally { bitmap?.let { if (!it.isRecycled) it.recycle() } }
    }

    fun loadBitmapWithExifOrientation(path: String): Bitmap? = try {
        val base = decodeSampled(1024) { File(path).inputStream() }
        val rotation = getRotationDegreesFromExif(path)
        if (base != null && rotation != 0) rotateBitmap(base, rotation) else base
    } catch (_: Exception) { null }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        return try {
            val m = Matrix()
            m.postRotate(degrees.toFloat())
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also {
                try { src.recycle() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            src
        }
    }

    private fun getRotationDegreesFromExif(path: String): Int = try {
        val exif = ExifInterface(path)
        orientationToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
    } catch (_: Exception) { 0 }

    private fun getRotationDegreesFromExif(stream: InputStream): Int = try {
        val exif = ExifInterface(stream)
        orientationToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
    } catch (_: Exception) { 0 }

    private fun orientationToDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        ExifInterface.ORIENTATION_TRANSPOSE -> 90
        ExifInterface.ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }
}
