package com.shreyas.pdfreader.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.shreyas.pdfreader.data.db.AppDatabase
import com.shreyas.pdfreader.data.db.BookmarkEntity
import com.shreyas.pdfreader.data.db.DocumentEntity
import com.shreyas.pdfreader.data.db.HighlightEntity
import com.shreyas.pdfreader.data.db.NoteEntity
import com.shreyas.pdfreader.data.db.PageTextEntity
import androidx.room.withTransaction
import com.shreyas.pdfreader.pdf.ALL_PAGES
import com.shreyas.pdfreader.pdf.PageCrop
import com.shreyas.pdfreader.pdf.PageEdits
import com.shreyas.pdfreader.pdf.PdfDocumentRenderer
import com.shreyas.pdfreader.pdf.positionOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class LibraryItem(
    val document: DocumentEntity,
    /** False when the read grant is gone or the copied file was deleted. */
    val available: Boolean,
    val cover: File,
    /** Changes when the cover file is replaced. The file path stays the same. */
    val coverVersion: Long,
    /** Pages the reader shows: pages of the file without deleted pages. */
    val pageCount: Int,
    /** One-based position of the reader among the shown pages. */
    val pageNumber: Int,
)

class LibraryRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val covers: CoverWriter,
) {

    private val documents = database.documentDao()
    private val bookmarks = database.bookmarkDao()
    private val pageEdits = database.pageEditDao()
    private val pageTexts = database.pageTextDao()
    private val highlights = database.highlightDao()
    private val notes = database.noteDao()
    private val resolver get() = context.contentResolver
    private val copiesDir = File(context.filesDir, "documents")
    private val coversDir = File(context.filesDir, "covers")

    fun observeLibrary(): Flow<List<LibraryItem>> =
        combine(documents.observeAll(), pageEdits.observeHidden()) { list, hidden ->
            val hiddenPages = hidden.groupBy({ it.documentId }, { it.page })
            list.map { document ->
                val cover = coverFile(document.uri)
                val pages = PageEdits(hidden = hiddenPages[document.id].orEmpty().toSet())
                    .visiblePages(document.pageCount)
                LibraryItem(
                    document = document,
                    available = isAvailable(document),
                    cover = cover,
                    coverVersion = cover.lastModified(),
                    pageCount = pages.size,
                    pageNumber = positionOf(pages, document.currentPage) + 1,
                )
            }
        }.flowOn(Dispatchers.IO)

    suspend fun get(id: Long): DocumentEntity? = documents.get(id)

    /**
     * Adds a PDF to the library and returns its id. A PDF already in the library returns the existing id.
     *
     * The file stays where it is when Android gives a persistable read grant (file picker).
     * Otherwise (Open with, Share Sheet) the grant ends with the activity, so the file is copied.
     *
     * @throws IOException or SecurityException when the file is unreadable, not a PDF, or password-protected
     */
    suspend fun import(source: Uri): Long = withContext(Dispatchers.IO) {
        val persisted = source.scheme == "content" && runCatching {
            resolver.takePersistableUriPermission(source, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        val (fileName, size) = queryNameAndSize(source)
        val stored = if (persisted) source else copyToAppStorage(source, fileName, size)

        documents.findByUri(stored.toString())?.let { return@withContext it.id }

        try {
            val renderer = PdfDocumentRenderer.open(context, stored)
            try {
                // Before the insert: the library shows the new row at once and reads the cover once.
                // A missing cover is not a failed import. The card shows a placeholder.
                runCatching { writeCover(renderer, stored.toString()) }
                documents.insert(
                    DocumentEntity(
                        uri = stored.toString(),
                        title = fileName.removeSuffix(".pdf").removeSuffix(".PDF"),
                        fileName = fileName,
                        pageCount = renderer.pageCount,
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            } finally {
                renderer.close()
            }
        } catch (e: Exception) {
            releaseStorage(stored)
            coverFile(stored.toString()).delete()
            throw e
        }
    }

    suspend fun remove(document: DocumentEntity) = withContext(Dispatchers.IO) {
        documents.delete(document.id)
        releaseStorage(Uri.parse(document.uri))
        coverFile(document.uri).delete()
    }

    suspend fun rename(id: Long, title: String) = documents.rename(id, title)

    suspend fun markOpened(id: Long, pageCount: Int) =
        documents.markOpened(id, System.currentTimeMillis(), pageCount)

    suspend fun saveProgress(id: Long, page: Int) = documents.setCurrentPage(id, page)

    fun observeBookmarks(documentId: Long): Flow<List<BookmarkEntity>> = bookmarks.observe(documentId)

    suspend fun setBookmarked(documentId: Long, page: Int, bookmarked: Boolean) {
        if (bookmarked) {
            bookmarks.insert(BookmarkEntity(documentId = documentId, page = page, createdAt = System.currentTimeMillis()))
        } else {
            bookmarks.delete(documentId, page)
        }
    }

    /** Replaces the cover of [document] with [image]. */
    suspend fun setCover(document: DocumentEntity, image: Bitmap) = withContext(Dispatchers.IO) {
        covers.write(image, coverFile(document.uri))
        documents.touch(document.id)
    }

    fun observePageEdits(documentId: Long): Flow<PageEdits> = pageEdits.observe(documentId).map { rows ->
        PageEdits(
            hidden = rows.filter { it.hidden }.map { it.page }.toSet(),
            crops = rows.mapNotNull { row ->
                val left = row.cropLeft ?: return@mapNotNull null
                val top = row.cropTop ?: return@mapNotNull null
                val right = row.cropRight ?: return@mapNotNull null
                val bottom = row.cropBottom ?: return@mapNotNull null
                row.page to PageCrop(left, top, right, bottom)
            }.toMap(),
        )
    }

    /** Deletes [pages] from the reader's view, or restores them. The PDF file is not changed. */
    suspend fun setPagesHidden(documentId: Long, pages: Collection<Int>, hidden: Boolean) = database.withTransaction {
        pages.forEach { page ->
            pageEdits.ensureRow(documentId, page)
            pageEdits.setHidden(documentId, page, hidden)
        }
        pageEdits.prune(documentId)
    }

    suspend fun restoreAllPages(documentId: Long) = database.withTransaction {
        pageEdits.showAll(documentId)
        pageEdits.prune(documentId)
    }

    /** Sets the crop of one page. [crop] null removes it. Use [setBookCrop] for all pages. */
    suspend fun setPageCrop(documentId: Long, page: Int, crop: PageCrop?) = database.withTransaction {
        pageEdits.ensureRow(documentId, page)
        pageEdits.setCrop(documentId, page, crop?.left, crop?.top, crop?.right, crop?.bottom)
        pageEdits.prune(documentId)
        // Text is recognized from the cropped page.
        pageTexts.clearPage(documentId, page)
    }

    /** Sets one crop for all pages and removes the crops of single pages. [crop] null removes every crop. */
    suspend fun setBookCrop(documentId: Long, crop: PageCrop?) = database.withTransaction {
        pageEdits.clearCrops(documentId)
        if (crop != null) {
            pageEdits.ensureRow(documentId, ALL_PAGES)
            pageEdits.setCrop(documentId, ALL_PAGES, crop.left, crop.top, crop.right, crop.bottom)
        }
        pageEdits.prune(documentId)
        pageTexts.clear(documentId)
    }

    suspend fun setTextMode(documentId: Long, on: Boolean) = documents.setTextMode(documentId, on)

    suspend fun setOcrScript(documentId: Long, script: String) = documents.setOcrScript(documentId, script)

    /** Recognized text of a page, or null when the page was not recognized yet. */
    suspend fun pageText(documentId: Long, page: Int, script: String): String? =
        pageTexts.get(documentId, page, script)

    suspend fun savePageText(documentId: Long, page: Int, script: String, text: String) =
        pageTexts.save(PageTextEntity(documentId, page, script, text))

    fun observeHighlights(documentId: Long): Flow<List<HighlightEntity>> = highlights.observe(documentId)

    suspend fun addHighlight(highlight: HighlightEntity) = highlights.insert(highlight)

    suspend fun updateHighlight(highlight: HighlightEntity) = highlights.update(highlight)

    suspend fun deleteHighlight(id: Long) = highlights.delete(id)

    fun observeNotes(documentId: Long): Flow<List<NoteEntity>> = notes.observe(documentId)

    /** Stores [note]. With [highlight], the highlight is stored first and the note belongs to it. */
    suspend fun addNote(note: NoteEntity, highlight: HighlightEntity? = null) = database.withTransaction {
        val highlightId = if (highlight != null) highlights.insert(highlight) else note.highlightId
        notes.insert(note.copy(highlightId = highlightId))
    }

    suspend fun updateNote(note: NoteEntity) = notes.update(note)

    suspend fun deleteNote(id: Long) = notes.delete(id)

    /** Named after the URI, so the cover can be written before the document has an id. */
    private fun coverFile(uri: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
        return File(coversDir, digest.take(12).joinToString("") { "%02x".format(it) } + ".webp")
    }

    private fun isAvailable(document: DocumentEntity): Boolean {
        val uri = Uri.parse(document.uri)
        return if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).exists()
        } else {
            resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = -1L
        if (uri.scheme == "content") {
            val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            runCatching {
                resolver.query(uri, columns, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0)
                        if (!cursor.isNull(1)) size = cursor.getLong(1)
                    }
                }
            }
        }
        if (uri.scheme == "file") size = File(requireNotNull(uri.path)).length()
        return (name ?: uri.lastPathSegment ?: "Document.pdf") to size
    }

    private fun copyToAppStorage(source: Uri, fileName: String, size: Long): Uri {
        copiesDir.mkdirs()
        // Size in the name makes a second share of the same file land on the same copy.
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(copiesDir, "${size}_$safeName")
        if (!target.exists() || target.length() != size) {
            val partial = File(copiesDir, "${target.name}.part")
            try {
                val input = resolver.openInputStream(source) ?: throw IOException("Cannot read $source")
                input.use { partial.outputStream().use { output -> input.copyTo(output) } }
                target.delete()
                if (!partial.renameTo(target)) throw IOException("Cannot store $fileName")
            } finally {
                partial.delete()
            }
        }
        return Uri.fromFile(target)
    }

    private fun releaseStorage(uri: Uri) {
        if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).delete()
        } else {
            runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    private suspend fun writeCover(renderer: PdfDocumentRenderer, uri: String) {
        val width = CoverWriter.COVER_WIDTH_PX
        covers.write(renderer.render(page = 0, widthPx = width, maxPixels = width * width * 2), coverFile(uri))
    }
}
