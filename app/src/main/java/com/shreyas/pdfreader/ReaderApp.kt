package com.shreyas.pdfreader

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.shreyas.pdfreader.data.CoverWriter
import com.shreyas.pdfreader.data.LibraryRepository
import com.shreyas.pdfreader.data.UpdateChecker
import com.shreyas.pdfreader.data.SettingsStore
import com.shreyas.pdfreader.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel

class ReaderApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** Manual dependency container. One instance for the whole process. */
class AppContainer(context: Context) {
    /** Outlives screens, so a final progress write is not lost when the reader closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database = Room.databaseBuilder(context, AppDatabase::class.java, "library.db")
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

    val covers = CoverWriter(context)
    val repository = LibraryRepository(context, database, covers)
    val settings = SettingsStore(context)
    val updates = UpdateChecker(context)

    /** PDFs that arrive through "Open with" or the Share Sheet. */
    val incomingDocuments = Channel<Uri>(Channel.BUFFERED)
}
