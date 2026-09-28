package com.shreyas.pdfreader.pdf

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CancellationException

/** One open PDF with a cache of its rendered pages. Shared by every screen that draws pages. */
class PageSource private constructor(
    private val renderer: PdfDocumentRenderer,
    private val cache: PageBitmapCache,
) {
    val pageCount: Int get() = renderer.pageCount

    suspend fun aspectRatio(page: Int): Float = renderer.aspectRatio(page)

    /**
     * Returns null when the page cannot be rendered.
     * [cached] false is for a large bitmap that is used once, such as the input of text recognition.
     */
    suspend fun bitmap(
        page: Int,
        widthPx: Int,
        maxPixels: Int,
        crop: PageCrop? = null,
        cached: Boolean = true,
    ): Bitmap? {
        if (cached) cache.get(page, widthPx, crop)?.let { return it }
        return try {
            renderer.render(page, widthPx, maxPixels, crop).also { if (cached) cache.put(page, widthPx, crop, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            cache.clear()
            null
        }
    }

    fun close() {
        renderer.close()
        cache.clear()
    }

    companion object {
        /** @throws java.io.IOException or SecurityException, see [PdfDocumentRenderer.open] */
        suspend fun open(context: Context, uri: Uri): PageSource {
            val memoryClassMb = context.getSystemService(ActivityManager::class.java).memoryClass
            val cacheBytes = (memoryClassMb / 4).coerceIn(32, 128) * 1024 * 1024
            return PageSource(PdfDocumentRenderer.open(context, uri), PageBitmapCache(cacheBytes))
        }
    }
}
