package com.shreyas.pdfreader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One open PDF. Wraps the framework [PdfRenderer].
 *
 * [PdfRenderer] allows one open page at a time and is not thread-safe before API 35.
 * All access runs on a dispatcher with parallelism 1, and no call suspends while a page is open.
 */
class PdfDocumentRenderer private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Width divided by height of [page]. */
    suspend fun aspectRatio(page: Int): Float = withContext(dispatcher) {
        ensureOpen()
        renderer.openPage(page).use { it.width.toFloat() / it.height }
    }

    /**
     * Renders [page] at [widthPx] wide. The bitmap is scaled down when it would exceed [maxPixels],
     * so a huge page or a high zoom cannot exhaust memory.
     */
    suspend fun render(page: Int, widthPx: Int, maxPixels: Int, crop: PageCrop? = null): Bitmap = withContext(dispatcher) {
        ensureOpen()
        renderer.openPage(page).use { pdfPage ->
            val area = crop ?: PageCrop.FULL
            val areaWidth = pdfPage.width * area.width
            val areaHeight = pdfPage.height * area.height
            val aspect = areaWidth / areaHeight
            var width = widthPx.coerceAtLeast(1)
            var height = (width / aspect).roundToInt().coerceAtLeast(1)
            val pixels = width.toLong() * height
            if (pixels > maxPixels) {
                val shrink = sqrt(maxPixels.toDouble() / pixels)
                width = (width * shrink).toInt().coerceAtLeast(1)
                height = (height * shrink).toInt().coerceAtLeast(1)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // PDF pages have no background of their own. Without this, pages come out transparent.
            bitmap.eraseColor(Color.WHITE)
            // The matrix maps page points to bitmap pixels. With a crop, only the cropped area
            // lands on the bitmap, at full sharpness.
            val transform = crop?.let {
                val scale = width / areaWidth
                Matrix().apply {
                    setTranslate(-it.left * pdfPage.width, -it.top * pdfPage.height)
                    postScale(scale, scale)
                }
            }
            pdfPage.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    /** Closes after any render in progress. Safe to call from any thread. */
    @OptIn(DelicateCoroutinesApi::class)
    fun close() {
        GlobalScope.launch(dispatcher) {
            if (closed) return@launch
            closed = true
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }

    private fun ensureOpen() {
        if (closed) throw CancellationException("Document is closed")
    }

    companion object {
        /**
         * @throws IOException when the file is missing, unreadable, or not a PDF
         * @throws SecurityException when the PDF needs a password or the URI grant is gone
         */
        suspend fun open(context: Context, uri: Uri): PdfDocumentRenderer = withContext(Dispatchers.IO) {
            val descriptor = if (uri.scheme == "file") {
                ParcelFileDescriptor.open(File(requireNotNull(uri.path)), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")
            } ?: throw IOException("Cannot open $uri")
            try {
                PdfDocumentRenderer(descriptor, PdfRenderer(descriptor))
            } catch (e: Exception) {
                runCatching { descriptor.close() }
                throw e
            }
        }
    }
}
