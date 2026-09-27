package com.shreyas.pdfreader.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents", indices = [Index(value = ["uri"], unique = true)])
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** `content://` URI with a persisted read grant, or `file://` URI of a copy in app storage. */
    val uri: String,
    val title: String,
    val fileName: String,
    val pageCount: Int,
    /** Zero-based. */
    val currentPage: Int = 0,
    val addedAt: Long,
    /** Null until the document is opened for the first time. */
    val lastOpenedAt: Long? = null,
)

/**
 * What the reader changed about one page. The PDF file is never changed.
 * Page -1 holds the crop for all pages of the book.
 */
@Entity(
    tableName = "page_edits",
    primaryKeys = ["documentId", "page"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PageEditEntity(
    val documentId: Long,
    /** Zero-based page of the file, or -1 for all pages. */
    val page: Int,
    val hidden: Boolean = false,
    /** Fractions 0..1 of the page. All four are null when the page has no crop. */
    val cropLeft: Float? = null,
    val cropTop: Float? = null,
    val cropRight: Float? = null,
    val cropBottom: Float? = null,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId", "page"], unique = true)],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    /** Zero-based. */
    val page: Int,
    val createdAt: Long,
)
