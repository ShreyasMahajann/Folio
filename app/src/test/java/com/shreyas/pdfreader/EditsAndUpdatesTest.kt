package com.shreyas.pdfreader

import com.shreyas.pdfreader.data.SEARCH_HISTORY_SIZE
import com.shreyas.pdfreader.data.isNewer
import com.shreyas.pdfreader.data.updatedHistory
import com.shreyas.pdfreader.pdf.ALL_PAGES
import com.shreyas.pdfreader.pdf.CropCorner
import com.shreyas.pdfreader.pdf.MIN_CROP_SIZE
import com.shreyas.pdfreader.pdf.PageCrop
import com.shreyas.pdfreader.pdf.PageEdits
import com.shreyas.pdfreader.pdf.dragCorner
import com.shreyas.pdfreader.pdf.moveBy
import com.shreyas.pdfreader.pdf.positionOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditsAndUpdatesTest {

    @Test
    fun newerVersionIsFound() {
        assertTrue(isNewer("1.1.0", "1.0.0"))
        assertTrue(isNewer("v1.0.1", "1.0.0"))
        assertTrue(isNewer("2.0", "1.9.9"))
        assertTrue(isNewer("1.10.0", "1.9.0"))
        assertTrue(isNewer("1.0.0.1", "1.0.0"))
    }

    @Test
    fun sameOrOlderVersionIsNotNewer() {
        assertFalse(isNewer("1.0.0", "1.0.0"))
        assertFalse(isNewer("1.0", "1.0.0"))
        assertFalse(isNewer("v1.0.0", "1.0"))
        assertFalse(isNewer("0.9.9", "1.0.0"))
        assertFalse(isNewer("1.1.0-beta", "1.1.0"))
    }

    @Test
    fun versionThatIsNotANumberIsNeverNewer() {
        assertFalse(isNewer("latest", "1.0.0"))
        assertFalse(isNewer("", "1.0.0"))
        assertFalse(isNewer("2.0.0", "unknown"))
    }

    @Test
    fun historyKeepsNewestFirstWithoutRepeats() {
        assertEquals(listOf("b", "a"), updatedHistory(listOf("a"), "b"))
        assertEquals(listOf("A", "b"), updatedHistory(listOf("b", "a"), " A "))
        assertEquals(listOf("a"), updatedHistory(listOf("a"), "   "))
        assertEquals(listOf("two lines"), updatedHistory(emptyList(), "two\nlines"))
    }

    @Test
    fun historyHasALimit() {
        val full = (1..SEARCH_HISTORY_SIZE).map { "query $it" }
        val updated = updatedHistory(full, "new")
        assertEquals(SEARCH_HISTORY_SIZE, updated.size)
        assertEquals("new", updated.first())
        assertFalse("query $SEARCH_HISTORY_SIZE" in updated)
    }

    @Test
    fun deletedPagesLeaveTheReader() {
        val edits = PageEdits(hidden = setOf(0, 2, 4))
        assertEquals(listOf(1, 3), edits.visiblePages(pageCount = 5))
        // A book never becomes empty, even when every page is marked deleted.
        assertEquals(listOf(0, 1), PageEdits(hidden = setOf(0, 1)).visiblePages(pageCount = 2))
    }

    @Test
    fun deletedPageMapsToNextPageThatIsLeft() {
        val pages = listOf(1, 3, 6)
        assertEquals(0, positionOf(pages, 0))
        assertEquals(0, positionOf(pages, 1))
        assertEquals(1, positionOf(pages, 2))
        assertEquals(2, positionOf(pages, 6))
        assertEquals(2, positionOf(pages, 9))
        assertEquals(0, positionOf(emptyList(), 3))
    }

    @Test
    fun cropOfOnePageWinsOverCropOfTheBook() {
        val book = PageCrop(0.1f, 0.1f, 0.9f, 0.9f)
        val single = PageCrop(0.2f, 0.2f, 0.8f, 0.8f)
        val edits = PageEdits(crops = mapOf(ALL_PAGES to book, 3 to single, 4 to PageCrop.FULL))
        assertEquals(book, edits.cropFor(0))
        assertEquals(single, edits.cropFor(3))
        // A full crop on one page switches the book crop off for that page.
        assertNull(edits.cropFor(4))
        assertNull(PageEdits().cropFor(0))
    }

    @Test
    fun cornerDragStaysOnPageAndAboveMinimumSize() {
        val box = PageCrop(0.2f, 0.2f, 0.8f, 0.8f)

        val grown = box.dragCorner(CropCorner.TOP_LEFT, -5f, -5f)
        assertEquals(PageCrop(0f, 0f, 0.8f, 0.8f), grown)

        val shrunk = box.dragCorner(CropCorner.TOP_LEFT, 5f, 5f)
        assertEquals(MIN_CROP_SIZE, shrunk.width, 0.0001f)
        assertEquals(MIN_CROP_SIZE, shrunk.height, 0.0001f)
        assertEquals(0.8f, shrunk.right, 0f)

        val other = box.dragCorner(CropCorner.BOTTOM_RIGHT, 0.1f, 5f)
        assertEquals(0.9f, other.right, 0.0001f)
        assertEquals(1f, other.bottom, 0f)
        assertEquals(0.2f, other.left, 0f)
    }

    @Test
    fun boxMoveKeepsSize() {
        val box = PageCrop(0.2f, 0.2f, 0.6f, 0.7f)
        val moved = box.moveBy(5f, -5f)
        assertEquals(1f, moved.right, 0.0001f)
        assertEquals(0f, moved.top, 0.0001f)
        assertEquals(box.width, moved.width, 0.0001f)
        assertEquals(box.height, moved.height, 0.0001f)
    }
}
