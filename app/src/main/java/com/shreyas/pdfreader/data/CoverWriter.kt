package com.shreyas.pdfreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class NotAnImageException : IOException("The content is not an image")

/** Loads images for book covers and stores covers. */
class CoverWriter(private val context: Context) {

    /** An image the user picked on the phone. */
    suspend fun load(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read $uri")
        decode(input.use { it.readLimited() })
    }

    /** An image from the web: an `https:` link, or a `data:` image as search pages embed them. */
    suspend fun loadFromWeb(link: String, userAgent: String?): Bitmap = withContext(Dispatchers.IO) {
        val trimmed = link.trim()
        when {
            trimmed.startsWith("data:image/") -> {
                val encoded = trimmed.substringAfter("base64,", missingDelimiterValue = "")
                if (encoded.isEmpty() || encoded.length > MAX_BYTES * 4 / 3) throw NotAnImageException()
                val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrElse { throw NotAnImageException() }
                decode(bytes)
            }
            trimmed.startsWith("https://") -> {
                val connection = URL(trimmed).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    userAgent?.let { connection.setRequestProperty("User-Agent", it) }
                    CookieManager.getInstance().getCookie(trimmed)?.let { connection.setRequestProperty("Cookie", it) }
                    val status = connection.responseCode
                    if (status !in 200..299) throw IOException("The server answered with status $status")
                    decode(connection.inputStream.use { it.readLimited() })
                } finally {
                    connection.disconnect()
                }
            }
            else -> throw IOException("The link must start with https://")
        }
    }

    /** Stores [image] as a cover: [COVER_WIDTH_PX] wide, WebP. */
    fun write(image: Bitmap, target: File) {
        target.parentFile?.mkdirs()
        val height = (COVER_WIDTH_PX.toFloat() * image.height / image.width).roundToInt().coerceIn(1, COVER_WIDTH_PX * 3)
        val scaled = Bitmap.createScaledBitmap(image, COVER_WIDTH_PX, height, true)
        // Write to a second file first. A failed write must not destroy the cover in use.
        val partial = File(target.parentFile, "${target.name}.part")
        try {
            partial.outputStream().use { scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }
            target.delete()
            if (!partial.renameTo(target)) throw IOException("Cannot store the cover")
        } finally {
            partial.delete()
        }
    }

    private fun InputStream.readLimited(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > MAX_BYTES) throw IOException("The image is larger than ${MAX_BYTES / 1_000_000} MB")
        }
        return output.toByteArray()
    }

    /** Decodes at reduced size, so a huge photo cannot exhaust memory. */
    private fun decode(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw NotAnImageException()
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (bounds.outWidth / (inSampleSize * 2) >= COVER_WIDTH_PX) inSampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw NotAnImageException()
    }

    companion object {
        const val COVER_WIDTH_PX = 400
        private const val MAX_BYTES = 15_000_000
    }
}
