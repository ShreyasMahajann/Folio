package com.shreyas.pdfreader.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.shreyas.pdfreader.data.db.AppDatabase
import com.shreyas.pdfreader.data.db.BookmarkEntity
import com.shreyas.pdfreader.data.db.DocumentEntity
import com.shreyas.pdfreader.pdf.PdfDocumentRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
)

class LibraryRepository(private val context: Context, database: AppDatabase) {

    private val documents = database.documentDao()
    private val bookmarks = database.bookmarkDao()
    private val resolver get() = context.contentResolver
    private val copiesDir = File(context.filesDir, "documents")
    private val coversDir = File(context.filesDir, "covers")

    fun observeLibrary(): Flow<List<LibraryItem>> = documents.observeAll()
        .map { list -> list.map { LibraryItem(it, isAvailable(it), coverFile(it.uri)) } }
        .flowOn(Dispatchers.IO)

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
        coversDir.mkdirs()
        val bitmap = renderer.render(page = 0, widthPx = COVER_WIDTH_PX, maxPixels = COVER_WIDTH_PX * COVER_WIDTH_PX * 2)
        coverFile(uri).outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }
    }

    private companion object {
        const val COVER_WIDTH_PX = 400
    }
}
