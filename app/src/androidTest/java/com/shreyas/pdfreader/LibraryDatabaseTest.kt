package com.shreyas.pdfreader

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shreyas.pdfreader.data.db.AppDatabase
import com.shreyas.pdfreader.data.db.BookmarkEntity
import com.shreyas.pdfreader.data.db.DocumentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Needs a device or emulator: `gradlew connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class LibraryDatabaseTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java,
    ).build()
    private val documents = database.documentDao()
    private val bookmarks = database.bookmarkDao()

    @After
    fun close() = database.close()

    private fun document(uri: String, addedAt: Long) =
        DocumentEntity(uri = uri, title = uri, fileName = "$uri.pdf", pageCount = 10, addedAt = addedAt)

    @Test
    fun progressSurvivesAndOrdersLibrary() = runTest {
        val old = documents.insert(document("old", addedAt = 1))
        val new = documents.insert(document("new", addedAt = 2))
        assertEquals(listOf(new, old), documents.observeAll().first().map { it.id })

        documents.setCurrentPage(old, 7)
        documents.markOpened(old, time = 3, pageCount = 10)

        assertEquals(7, documents.get(old)?.currentPage)
        assertEquals(listOf(old, new), documents.observeAll().first().map { it.id })
    }

    @Test
    fun pageEditsKeepCropWhenPageIsDeletedAndLeaveWithDocument() = runTest {
        val edits = database.pageEditDao()
        val id = documents.insert(document("doc", addedAt = 1))

        edits.ensureRow(id, 3)
        edits.setCrop(id, 3, 0.1f, 0.2f, 0.9f, 0.8f)
        edits.ensureRow(id, 3)
        edits.setHidden(id, 3, true)
        val row = edits.observe(id).first().single()
        assertEquals(true, row.hidden)
        assertEquals(0.1f, row.cropLeft)

        // A page that is visible and not cropped needs no row.
        edits.setHidden(id, 3, false)
        edits.clearCrops(id)
        edits.prune(id)
        assertEquals(0, edits.observe(id).first().size)

        edits.ensureRow(id, 5)
        edits.setHidden(id, 5, true)
        documents.delete(id)
        assertEquals(0, edits.observe(id).first().size)
    }

    @Test
    fun bookmarksAreUniquePerPageAndLeaveWithDocument() = runTest {
        val id = documents.insert(document("doc", addedAt = 1))
        bookmarks.insert(BookmarkEntity(documentId = id, page = 4, createdAt = 1))
        bookmarks.insert(BookmarkEntity(documentId = id, page = 4, createdAt = 2))
        bookmarks.insert(BookmarkEntity(documentId = id, page = 2, createdAt = 3))
        assertEquals(listOf(2, 4), bookmarks.observe(id).first().map { it.page })

        documents.delete(id)

        assertNull(documents.get(id))
        assertEquals(emptyList<BookmarkEntity>(), bookmarks.observe(id).first())
    }
}
