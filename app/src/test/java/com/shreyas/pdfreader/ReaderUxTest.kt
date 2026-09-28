package com.shreyas.pdfreader

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.shreyas.pdfreader.data.HttpStatusException
import com.shreyas.pdfreader.data.NotAPdfException
import com.shreyas.pdfreader.data.ReaderSettings
import com.shreyas.pdfreader.data.linkKey
import com.shreyas.pdfreader.data.mergeLinks
import com.shreyas.pdfreader.data.resultLink
import com.shreyas.pdfreader.data.secureUrl
import com.shreyas.pdfreader.data.worthRetry
import com.shreyas.pdfreader.ui.reader.placeAnchored
import com.shreyas.pdfreader.ui.search.downloadMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ReaderUxTest {

    private val screen = IntRect(0, 100, 1000, 2000)
    private val menu = IntSize(400, 200)

    @Test
    fun menuSitsAboveTheSelectionWhenThereIsRoom() {
        val word = IntRect(400, 1000, 600, 1050)
        assertEquals(IntOffset(300, 790), placeAnchored(word, menu, screen, gap = 10))
    }

    @Test
    fun menuGoesBelowASelectionAtTheTop() {
        val word = IntRect(400, 150, 600, 200)
        assertEquals(IntOffset(300, 210), placeAnchored(word, menu, screen, gap = 10))
    }

    @Test
    fun menuStaysInsideTheSidesOfTheScreen() {
        assertEquals(0, placeAnchored(IntRect(0, 1000, 60, 1050), menu, screen, gap = 10).x)
        assertEquals(600, placeAnchored(IntRect(950, 1000, 1000, 1050), menu, screen, gap = 10).x)
    }

    @Test
    fun menuTakesTheSideWithMoreRoomWhenTheSelectionFillsTheScreen() {
        val small = IntRect(0, 100, 1000, 500)
        assertEquals(100, placeAnchored(IntRect(0, 150, 1000, 480), menu, small, gap = 10).y)
        assertEquals(300, placeAnchored(IntRect(0, 120, 1000, 450), menu, small, gap = 10).y)
        // A selection that is outside the screen after a scroll.
        assertEquals(100, placeAnchored(IntRect(400, -500, 600, -450), menu, screen, gap = 10).y)
    }

    @Test
    fun storedTextSizeBecomesTheNearestPreset() {
        assertEquals(18f, ReaderSettings.nearestTextSize(18f))
        assertEquals(14f, ReaderSettings.nearestTextSize(12f))
        assertEquals(23f, ReaderSettings.nearestTextSize(22f))
        assertEquals(30f, ReaderSettings.nearestTextSize(40f))
    }

    @Test
    fun downloadUsesAnEncryptedConnection() {
        assertEquals("https://a.org/b.pdf", secureUrl("http://a.org/b.pdf"))
        assertEquals("https://a.org/b.pdf", secureUrl("HTTP://a.org/b.pdf"))
        assertEquals("https://a.org/http://b.pdf", secureUrl("https://a.org/http://b.pdf"))
    }

    @Test
    fun onlyAFailedConnectionIsTriedAgain() {
        assertTrue(worthRetry(SocketException("Connection reset")))
        assertTrue(worthRetry(SocketTimeoutException()))
        assertTrue(worthRetry(SSLHandshakeException("closed")))
        assertFalse(worthRetry(HttpStatusException(403)))
        assertFalse(worthRetry(NotAPdfException()))
        assertFalse(worthRetry(UnknownHostException()))
    }

    @Test
    fun linkOfAResultLeavesTheSearchEngine() {
        assertEquals("https://a.org/b.pdf", resultLink("https://www.google.com/url?sa=t&q=https%3A%2F%2Fa.org%2Fb.pdf&usg=1"))
        assertEquals("https://a.org/b.pdf?q=1", resultLink("https://a.org/b.pdf?q=1"))
        assertEquals(null, resultLink("https://www.google.com/search?q=more"))
        assertEquals(null, resultLink("https://maps.google.co.in/"))
        assertEquals(null, resultLink("javascript:void(0)"))
        assertEquals(null, resultLink(""))
        assertEquals("https://a.org/one-book.pdf", resultLink("//duckduckgo.com/l/?uddg=https%3A%2F%2Fa.org%2Fone%2Dbook.pdf&rut=12"))
        assertEquals(null, resultLink("https://duckduckgo.com/y.js?ad_domain=c.com&q=x"))
        assertEquals("https://b.org/two.pdf", resultLink("http://b.org/two.pdf"))
    }

    @Test
    fun aFileThatBothEnginesFoundShowsOneTime() {
        val duck = listOf("https://a.org/1.pdf", "https://www.b.org/2.pdf", "https://c.org/3.pdf")
        val google = listOf("https://b.org/2.pdf#page=4", "https://d.org/4.pdf")
        assertEquals(
            listOf("https://a.org/1.pdf", "https://b.org/2.pdf#page=4", "https://d.org/4.pdf", "https://c.org/3.pdf"),
            mergeLinks(listOf(duck, google)),
        )
        // The other engine still gives results when one engine gives none.
        assertEquals(google, mergeLinks(listOf(emptyList(), google)))
        assertEquals(listOf("https://d.org/4.pdf"), mergeLinks(listOf(google), known = setOf(linkKey("https://b.org/2.pdf"))))
    }

    @Test
    fun userReadsNoExceptionTextForAFailedConnection() {
        assertFalse(downloadMessage(SocketException("Connection reset")).contains("Connection reset"))
        assertEquals("No internet connection.", downloadMessage(UnknownHostException("a.org")))
        assertTrue(downloadMessage(HttpStatusException(403)).contains("403"))
        assertEquals("Download failed. The file is larger than 500 MB", downloadMessage(IOException("The file is larger than 500 MB")))
    }
}
