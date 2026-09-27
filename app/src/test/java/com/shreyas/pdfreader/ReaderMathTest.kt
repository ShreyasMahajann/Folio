package com.shreyas.pdfreader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.shreyas.pdfreader.data.PdfDownloader
import com.shreyas.pdfreader.ui.reader.MAX_ZOOM
import com.shreyas.pdfreader.ui.reader.Zoom
import com.shreyas.pdfreader.ui.reader.isAlmostUnzoomed
import com.shreyas.pdfreader.ui.reader.listScrollForZoom
import com.shreyas.pdfreader.ui.reader.zoomAround
import com.shreyas.pdfreader.util.progressPercent
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderMathTest {

    private val viewport = Size(1000f, 2000f)
    private val centre = Offset(500f, 1000f)

    /** Where a point of the unscaled layer lands on screen. */
    private fun Zoom.toScreen(local: Offset) = centre + (local - centre) * scale + offset

    @Test
    fun progressCoversFirstAndLastPage() {
        assertEquals(0, progressPercent(page = 0, pageCount = 0))
        assertEquals(1, progressPercent(page = 0, pageCount = 100))
        assertEquals(50, progressPercent(page = 49, pageCount = 100))
        assertEquals(100, progressPercent(page = 99, pageCount = 100))
        assertEquals(100, progressPercent(page = 500, pageCount = 100))
        assertEquals(100, progressPercent(page = 0, pageCount = 1))
    }

    @Test
    fun downloadedFileNamesAreSafe() {
        assertEquals("book.pdf", PdfDownloader.safeFileName("book.pdf"))
        assertEquals("BOOK.PDF", PdfDownloader.safeFileName("BOOK.PDF"))
        assertEquals("download.bin.pdf", PdfDownloader.safeFileName("download.bin"))
        assertEquals("_.._etc_passwd.pdf", PdfDownloader.safeFileName("/../etc/passwd"))
        assertEquals("Document.pdf", PdfDownloader.safeFileName(" .. "))
    }

    @Test
    fun pinchKeepsContentUnderFingers() {
        val start = Zoom(scale = 2f, offset = Offset(100f, -50f))
        val centroid = Offset(600f, 900f)
        val local = centre + (centroid - centre - start.offset) / start.scale

        val zoomed = zoomAround(start, centroid, Offset.Zero, 1.5f, viewport, panVertically = true)

        assertEquals(3f, zoomed.scale, 0.001f)
        assertEquals(centroid.x, zoomed.toScreen(local).x, 0.01f)
        assertEquals(centroid.y, zoomed.toScreen(local).y, 0.01f)
    }

    @Test
    fun zoomStaysInsideLimits() {
        val zoomedIn = zoomAround(Zoom(), centre, Offset.Zero, 100f, viewport, panVertically = true)
        assertEquals(MAX_ZOOM, zoomedIn.scale, 0f)

        val zoomedOut = zoomAround(zoomedIn, Offset(10f, 10f), Offset(999f, 999f), 0.001f, viewport, panVertically = true)
        assertEquals(1f, zoomedOut.scale, 0f)
        assertEquals(0f, zoomedOut.offset.x, 0f)
        assertEquals(0f, zoomedOut.offset.y, 0f)
    }

    @Test
    fun zoomJustAboveOneCountsAsNoZoom() {
        assertEquals(true, isAlmostUnzoomed(1.016f))
        assertEquals(true, isAlmostUnzoomed(1f))
        assertEquals(false, isAlmostUnzoomed(1.2f))
    }

    @Test
    fun panStopsAtPageEdge() {
        val panned = zoomAround(Zoom(scale = 2f), centre, Offset(5000f, -5000f), 1f, viewport, panVertically = true)
        // Left edge of the layer sits on the left edge of the screen, bottom edge on the bottom edge.
        assertEquals(0f, panned.toScreen(Offset.Zero).x, 0.01f)
        assertEquals(2000f, panned.toScreen(Offset(1000f, 2000f)).y, 0.01f)
    }

    @Test
    fun scrollModeLeavesVerticalMovementToTheList() {
        val zoomed = zoomAround(Zoom(), Offset(500f, 300f), Offset(0f, 80f), 2f, viewport, panVertically = false)
        assertEquals(0f, zoomed.offset.y, 0f)
        assertEquals(150f, listScrollForZoom(centroidY = 300f, oldScale = 1f, newScale = 2f), 0.01f)
    }
}
