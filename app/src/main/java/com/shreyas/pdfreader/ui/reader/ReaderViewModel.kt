package com.shreyas.pdfreader.ui.reader

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shreyas.pdfreader.AppContainer
import com.shreyas.pdfreader.ReaderApp
import com.shreyas.pdfreader.data.ReaderSettings
import com.shreyas.pdfreader.data.db.BookmarkEntity
import com.shreyas.pdfreader.data.db.DocumentEntity
import com.shreyas.pdfreader.pdf.PageBitmapCache
import com.shreyas.pdfreader.pdf.PdfDocumentRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val pageCount: Int = 0,
    /** Zero-based. */
    val currentPage: Int = 0,
    /** Page shape used until the real shape of a page is known. */
    val defaultAspect: Float = 0.707f,
)

class ReaderViewModel(
    private val container: AppContainer,
    context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentId: Long = checkNotNull(savedStateHandle[DOCUMENT_ID_ARG])
    private val appContext = context.applicationContext
    private val repository = container.repository

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    /** Null until the stored settings are read. */
    val settings: StateFlow<ReaderSettings?> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        repository.observeBookmarks(documentId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var document: DocumentEntity? = null
    private var renderer: PdfDocumentRenderer? = null
    private val cache = PageBitmapCache(cacheBytes(appContext))

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val stored = repository.get(documentId) ?: throw IllegalStateException("Document is not in the library")
            document = stored
            val opened = PdfDocumentRenderer.open(appContext, Uri.parse(stored.uri))
            renderer = opened
            check(opened.pageCount > 0) { "This PDF has no pages" }
            repository.markOpened(documentId, opened.pageCount)
            _state.value = ReaderUiState(
                loading = false,
                title = stored.title,
                pageCount = opened.pageCount,
                currentPage = stored.currentPage.coerceIn(0, opened.pageCount - 1),
                defaultAspect = opened.aspectRatio(0),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            _state.update { it.copy(loading = false, error = "Cannot open this file. It needs a password, or access to it was removed.") }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = "Cannot open this file. It was moved, deleted, or is not a valid PDF.") }
        }
    }

    /** Returns null when the page cannot be rendered. */
    suspend fun pageBitmap(page: Int, widthPx: Int, maxPixels: Int): Bitmap? {
        cache.get(page, widthPx)?.let { return it }
        val open = renderer ?: return null
        return try {
            open.render(page, widthPx, maxPixels).also { cache.put(page, widthPx, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            cache.clear()
            null
        }
    }

    fun onPageChanged(page: Int) {
        if (page == _state.value.currentPage) return
        _state.update { it.copy(currentPage = page) }
        // App scope, so the write finishes even when the reader closes right after the page turn.
        container.appScope.launch { repository.saveProgress(documentId, page) }
    }

    fun toggleBookmark() {
        val page = _state.value.currentPage
        val bookmarked = bookmarks.value.any { it.page == page }
        viewModelScope.launch { repository.setBookmarked(documentId, page, !bookmarked) }
    }

    fun updateSettings(settings: ReaderSettings) {
        viewModelScope.launch { container.settings.update(settings) }
    }

    fun removeFromLibrary() {
        val stored = document ?: return
        container.appScope.launch { repository.remove(stored) }
    }

    override fun onCleared() {
        renderer?.close()
        cache.clear()
    }

    companion object {
        const val DOCUMENT_ID_ARG = "documentId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ReaderApp
                ReaderViewModel(app.container, app, createSavedStateHandle())
            }
        }

        private fun cacheBytes(context: Context): Int {
            val memoryClassMb = context.getSystemService(ActivityManager::class.java).memoryClass
            return (memoryClassMb / 4).coerceIn(32, 128) * 1024 * 1024
        }
    }
}
