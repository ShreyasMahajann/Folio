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
