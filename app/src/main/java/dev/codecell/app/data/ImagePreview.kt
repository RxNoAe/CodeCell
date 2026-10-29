package dev.codecell.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImagePreview {

    private const val MAX_DIMENSION = 512

    private val cache = LinkedHashMap<String, ByteArray>(16, 0.75f, true)

    private fun cached(key: String): ByteArray? = synchronized(cache) { cache[key] }

    private fun putCache(key: String, value: ByteArray) {
        synchronized(cache) {
            cache[key] = value
            while (cache.size > 48) {
                val eldest = cache.entries.firstOrNull() ?: break
                cache.remove(eldest.key)
            }
        }
    }

    fun create(bytes: ByteArray, mime: String, maxDimension: Int = MAX_DIMENSION): ByteArray? {
        if (!mime.startsWith("image/", ignoreCase = true)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension &&
            bounds.outHeight / (sample * 2) >= maxDimension
        ) {
            sample *= 2
        }

        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        val scale = minOf(
            1f,
            maxDimension.toFloat() / decoded.width,
            maxDimension.toFloat() / decoded.height,
        )
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val format = if (mime.equals("image/png", ignoreCase = true)) {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
        val output = ByteArrayOutputStream()
        val compressed = resized.compress(format, if (format == Bitmap.CompressFormat.PNG) 100 else 82, output)
        resized.recycle()
        return if (compressed) output.toByteArray() else null
    }

    fun fromDataUrl(url: String, mime: String, key: String = ""): ByteArray? {
        if (!mime.startsWith("image/", ignoreCase = true) || !url.startsWith("data:", ignoreCase = true)) return null
        if (key.isNotBlank()) {
            cached(key)?.let { return it }
        }
        val marker = ";base64,"
        val index = url.indexOf(marker, ignoreCase = true)
        if (index < 0) return null
        val bytes = runCatching {
            Base64.decode(url.substring(index + marker.length), Base64.DEFAULT)
        }.getOrNull() ?: return null
        val preview = runCatching { create(bytes, mime) }.getOrNull() ?: return null
        if (key.isNotBlank()) {
            putCache(key, preview)
        }
        return preview
    }
}
