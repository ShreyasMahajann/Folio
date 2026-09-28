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
        NoteEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun pageEditDao(): PageEditDao
    abstract fun pageTextDao(): PageTextDao
    abstract fun highlightDao(): HighlightDao
    abstract fun noteDao(): NoteDao

    companion object {
        /**
         * Version 4 gives notes their own table, so a note can belong to a page without a highlight.
         * The notes of highlights move to the new table. The highlights lose the note column.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The old notes wait in a table without links to other tables,
                // so the drop of the old highlights cannot change them.
                db.execSQL(
                    "CREATE TABLE `notes_moving` AS SELECT `id` AS `highlightId`, `documentId`, `page`, " +
                        "`position`, `text` AS `quote`, `note` AS `text`, `createdAt` " +
                        "FROM `highlights` WHERE `note` IS NOT NULL",
                )
                // SQLite of Android 12 cannot drop a column: the table is made again.
                db.execSQL(
                    "CREATE TABLE `highlights_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `documentId` INTEGER NOT NULL, " +
                        "`page` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                        "`color` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "INSERT INTO `highlights_new` (`id`, `documentId`, `page`, `position`, `text`, `color`, `createdAt`) " +
                        "SELECT `id`, `documentId`, `page`, `position`, `text`, `color`, `createdAt` FROM `highlights`",
                )
                db.execSQL("DROP TABLE `highlights`")
                db.execSQL("ALTER TABLE `highlights_new` RENAME TO `highlights`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_documentId` ON `highlights` (`documentId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `documentId` INTEGER NOT NULL, " +
                        "`page` INTEGER NOT NULL, `position` INTEGER, `quote` TEXT, `highlightId` INTEGER, " +
                        "`text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`highlightId`) REFERENCES `highlights`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE SET NULL )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_documentId` ON `notes` (`documentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_highlightId` ON `notes` (`highlightId`)")
                db.execSQL(
                    "INSERT INTO `notes` (`documentId`, `page`, `position`, `quote`, `highlightId`, `text`, `createdAt`) " +
                        "SELECT `documentId`, `page`, `position`, `quote`, `highlightId`, `text`, `createdAt` " +
                        "FROM `notes_moving`",
                )
                db.execSQL("DROP TABLE `notes_moving`")
            }
        }

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
