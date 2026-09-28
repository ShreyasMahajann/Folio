package com.shreyas.pdfreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY COALESCE(lastOpenedAt, addedAt) DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE uri = :uri")
    suspend fun findByUri(uri: String): DocumentEntity?

    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Query("UPDATE documents SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE documents SET currentPage = :page WHERE id = :id")
    suspend fun setCurrentPage(id: Long, page: Int)

    @Query("UPDATE documents SET lastOpenedAt = :time, pageCount = :pageCount WHERE id = :id")
    suspend fun markOpened(id: Long, time: Long, pageCount: Int)

    @Query("UPDATE documents SET textMode = :on WHERE id = :id")
    suspend fun setTextMode(id: Long, on: Boolean)

    @Query("UPDATE documents SET ocrScript = :script WHERE id = :id")
    suspend fun setOcrScript(id: Long, script: String)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)

    /** Changes nothing. Makes [observeAll] emit again, for changes outside the database such as a new cover. */
    @Query("UPDATE documents SET title = title WHERE id = :id")
    suspend fun touch(id: Long)
}

@Dao
interface PageEditDao {
    @Query("SELECT * FROM page_edits WHERE documentId = :documentId")
    fun observe(documentId: Long): Flow<List<PageEditEntity>>

    @Query("SELECT * FROM page_edits WHERE hidden = 1")
    fun observeHidden(): Flow<List<PageEditEntity>>

    @Query("INSERT OR IGNORE INTO page_edits (documentId, page, hidden) VALUES (:documentId, :page, 0)")
    suspend fun ensureRow(documentId: Long, page: Int)

    @Query("UPDATE page_edits SET hidden = :hidden WHERE documentId = :documentId AND page = :page")
    suspend fun setHidden(documentId: Long, page: Int, hidden: Boolean)

    @Query("UPDATE page_edits SET hidden = 0 WHERE documentId = :documentId")
    suspend fun showAll(documentId: Long)

    @Query(
        "UPDATE page_edits SET cropLeft = :left, cropTop = :top, cropRight = :right, cropBottom = :bottom " +
            "WHERE documentId = :documentId AND page = :page",
    )
    suspend fun setCrop(documentId: Long, page: Int, left: Float?, top: Float?, right: Float?, bottom: Float?)

    @Query(
        "UPDATE page_edits SET cropLeft = NULL, cropTop = NULL, cropRight = NULL, cropBottom = NULL " +
            "WHERE documentId = :documentId",
    )
    suspend fun clearCrops(documentId: Long)

    /** Removes rows that say nothing: page visible and not cropped. */
    @Query("DELETE FROM page_edits WHERE documentId = :documentId AND hidden = 0 AND cropLeft IS NULL")
    suspend fun prune(documentId: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY page")
    fun observe(documentId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE documentId = :documentId AND page = :page")
    suspend fun delete(documentId: Long, page: Int)
}

@Dao
interface PageTextDao {
    @Query("SELECT text FROM page_text WHERE documentId = :documentId AND page = :page AND script = :script")
    suspend fun get(documentId: Long, page: Int, script: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(text: PageTextEntity)

    @Query("DELETE FROM page_text WHERE documentId = :documentId AND page = :page")
    suspend fun clearPage(documentId: Long, page: Int)

    @Query("DELETE FROM page_text WHERE documentId = :documentId")
    suspend fun clear(documentId: Long)
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE documentId = :documentId ORDER BY page, position")
    fun observe(documentId: Long): Flow<List<HighlightEntity>>

    @Insert
    suspend fun insert(highlight: HighlightEntity): Long

    @Update
    suspend fun update(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun delete(id: Long)
}
