package com.shreyas.pdfreader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentEntity::class, BookmarkEntity::class, PageEditEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun pageEditDao(): PageEditDao

    companion object {
        /** Version 2 adds deleted and cropped pages. Existing tables stay as they are. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `page_edits` (" +
                        "`documentId` INTEGER NOT NULL, `page` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, " +
                        "`cropLeft` REAL, `cropTop` REAL, `cropRight` REAL, `cropBottom` REAL, " +
                        "PRIMARY KEY(`documentId`, `page`), " +
                        "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
            }
        }
    }
}
