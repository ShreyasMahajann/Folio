package com.shreyas.pdfreader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DocumentEntity::class,
        BookmarkEntity::class,
        PageEditEntity::class,
        PageTextEntity::class,
        HighlightEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun pageEditDao(): PageEditDao
    abstract fun pageTextDao(): PageTextDao
    abstract fun highlightDao(): HighlightDao

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

        /** Version 3 adds text mode: recognized page text and highlights. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `textMode` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `ocrScript` TEXT NOT NULL DEFAULT 'LATIN'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `page_text` (" +
                        "`documentId` INTEGER NOT NULL, `page` INTEGER NOT NULL, `script` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "PRIMARY KEY(`documentId`, `page`, `script`), " +
                        "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `highlights` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `documentId` INTEGER NOT NULL, " +
                        "`page` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                        "`color` TEXT NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_documentId` ON `highlights` (`documentId`)")
            }
        }
    }
}
