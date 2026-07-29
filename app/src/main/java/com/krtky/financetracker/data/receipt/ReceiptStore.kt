package com.krtky.financetracker.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-private receipt photos under `files/receipts/`.
 * Domain field stores a relative path (`receipts/<id>.jpg`) or a content URI string.
 */
@Singleton
class ReceiptStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val receiptsDir: File
        get() = File(context.filesDir, "receipts").also { it.mkdirs() }

    fun createCameraTempFile(): File {
        val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
        return File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    }

    /**
     * Copy [source] into permanent storage. Returns relative path for Room.
     */
    suspend fun persistFromUri(source: Uri, preferredName: String? = null): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = (preferredName?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString())
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val dest = File(receiptsDir, "$name.jpg")
                context.contentResolver.openInputStream(source)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                // Downscale huge camera shots to keep storage light
                compressIfNeeded(dest)
                "receipts/${dest.name}"
            }.getOrNull()
        }

    suspend fun persistFromFile(source: File, preferredName: String? = null): String? =
        withContext(Dispatchers.IO) {
            if (!source.exists()) return@withContext null
            runCatching {
                val name = (preferredName?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString())
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val dest = File(receiptsDir, "$name.jpg")
                source.copyTo(dest, overwrite = true)
                compressIfNeeded(dest)
                "receipts/${dest.name}"
            }.getOrNull()
        }

    fun resolveFile(stored: String?): File? {
        if (stored.isNullOrBlank()) return null
        if (stored.startsWith("content:") || stored.startsWith("file:")) return null
        val relative = stored.removePrefix("/")
        val file = if (relative.startsWith("receipts/")) {
            File(context.filesDir, relative)
        } else {
            File(stored)
        }
        return file.takeIf { it.exists() }
    }

    fun resolveDisplayUri(stored: String?): Uri? {
        if (stored.isNullOrBlank()) return null
        if (stored.startsWith("content:") || stored.startsWith("file:")) {
            return Uri.parse(stored)
        }
        return resolveFile(stored)?.let { Uri.fromFile(it) }
    }

    suspend fun delete(stored: String?) = withContext(Dispatchers.IO) {
        resolveFile(stored)?.delete()
    }

    private fun compressIfNeeded(file: File, maxSide: Int = 1600, quality: Int = 85) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return
        if (w <= maxSide && h <= maxSide && file.length() < 800_000) return
        var sample = 1
        while (w / sample > maxSide * 2 || h / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return
        val scale = maxOf(bmp.width, bmp.height).toFloat() / maxSide
        val out = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width / scale).toInt().coerceAtLeast(1),
                (bmp.height / scale).toInt().coerceAtLeast(1),
                true,
            ).also { if (it != bmp) bmp.recycle() }
        } else {
            bmp
        }
        FileOutputStream(file).use { fos ->
            out.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
        out.recycle()
    }
}
