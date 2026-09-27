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
