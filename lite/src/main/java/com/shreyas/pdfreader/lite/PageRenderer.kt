package com.shreyas.pdfreader.lite

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import kotlin.math.max

/**
 * Draws PDF pages to bitmaps on one background thread. All results arrive on the main thread.
 *
 * @param open gives a new file descriptor for the PDF. pdfium closes it together with the document.
 */
class PageRenderer(context: Context, private val open: () -> ParcelFileDescriptor) {

    private val core = PdfiumCore(context)
    private val thread = HandlerThread("render").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    private var document: PdfDocument? = null
    private var openPages = 0

    /** Opens the PDF and reports the number of pages, or the error. */
    fun start(onReady: (pageCount: Int) -> Unit, onError: () -> Unit) {
        worker.post {
            try {
                val count = core.getPageCount(document())
                main.post { onReady(count) }
            } catch (e: Exception) {
                main.post(onError)
            }
        }
    }

    /** Draws one page with the given width in pixels. The height follows the shape of the page. */
    fun render(page: Int, width: Int, done: (Bitmap) -> Unit) {
        worker.post {
            try {
                val pdf = document()
                core.openPage(pdf, page)
                openPages++
                val height = width * core.getPageHeightPoint(pdf, page) / max(1, core.getPageWidthPoint(pdf, page))
                // RGB_565 takes half the memory of ARGB_8888. A page has no transparent parts.
                val bitmap = Bitmap.createBitmap(width, max(1, height), Bitmap.Config.RGB_565)
                core.renderPageBitmap(pdf, bitmap, page, 0, 0, bitmap.width, bitmap.height)
                main.post { done(bitmap) }
            } catch (e: Exception) {
                // A page that cannot be drawn stays empty. The other pages still work.
            } catch (e: OutOfMemoryError) {
                close()
            }
        }
    }

    /** Drops the pages that wait in the queue. Use it before a request for a new current page. */
    fun cancelPending() = worker.removeCallbacksAndMessages(null)

    fun stop() {
        cancelPending()
        worker.post {
            close()
            thread.quit()
        }
    }

    private fun document(): PdfDocument {
        // pdfium keeps each opened page in memory until the document closes, and this library
        // cannot close one page. A new document after some pages keeps the memory use low.
        if (openPages >= MAX_OPEN_PAGES) close()
        return document ?: core.newDocument(open()).also {
            document = it
            openPages = 0
        }
    }

    private fun close() {
        document?.let { core.closeDocument(it) }
        document = null
    }

    private companion object {
        const val MAX_OPEN_PAGES = 20
    }
}
