package com.shreyas.pdfreader.ui.reader

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
import com.shreyas.pdfreader.data.db.HighlightEntity
import com.shreyas.pdfreader.data.db.NoteEntity
import com.shreyas.pdfreader.pdf.HighlightColor
import com.shreyas.pdfreader.pdf.OcrScript
import com.shreyas.pdfreader.pdf.PageCrop
import com.shreyas.pdfreader.pdf.PageEdits
import com.shreyas.pdfreader.pdf.PageOcr
import com.shreyas.pdfreader.pdf.PageSource
import com.shreyas.pdfreader.pdf.positionOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The place of a new note. Always a page. A note on selected text or on a highlight
 * also has [position] and [quote], and [highlightId] when the highlight exists.
 */
data class NoteDraft(
    val page: Int,
    val position: Int? = null,
    val quote: String? = null,
    val highlightId: Long? = null,
)

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    /** Zero-based page numbers of the file that the reader shows. Deleted pages are not in the list. */
    val pages: List<Int> = emptyList(),
    /** Zero-based page number of the file. */
    val currentPage: Int = 0,
    val edits: PageEdits = PageEdits(),
    /** Shape of an uncropped page, used until the real shape of a page is known. */
    val defaultAspect: Float = 0.707f,
    /** True when the reader shows recognized text in place of the pages. */
    val textMode: Boolean = false,
    val ocrScript: OcrScript = OcrScript.LATIN,
) {
    /** Position of the current page among the shown pages. */
    val position: Int get() = positionOf(pages, currentPage)
}

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

    val highlights: StateFlow<List<HighlightEntity>> =
        repository.observeHighlights(documentId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notes: StateFlow<List<NoteEntity>> =
        repository.observeNotes(documentId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var document: DocumentEntity? = null
    private var source: PageSource? = null
    private var ocr: PageOcr? = null

    /** A page that Undo brings back. The reader opens it when the page list has it again. */
    private var restoredPage: Int? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val stored = repository.get(documentId) ?: throw IllegalStateException("Document is not in the library")
            document = stored
            val opened = PageSource.open(appContext, Uri.parse(stored.uri))
            source = opened
            check(opened.pageCount > 0) { "This PDF has no pages" }
            repository.markOpened(documentId, opened.pageCount)

            val edits = repository.observePageEdits(documentId).first()
            val pages = edits.visiblePages(opened.pageCount)
            _state.value = ReaderUiState(
                loading = false,
                title = stored.title,
                pages = pages,
                currentPage = pages[positionOf(pages, stored.currentPage)],
                edits = edits,
                defaultAspect = opened.aspectRatio(0),
                textMode = stored.textMode,
                ocrScript = OcrScript.of(stored.ocrScript),
            )
            // Edits also change in the page manager while the reader is open behind it.
            repository.observePageEdits(documentId).collect { changed ->
                val shown = changed.visiblePages(opened.pageCount)
                // A restored page opens at once. A deleted current page gives way to the next page that is left.
                val target = restoredPage?.takeIf { it in shown } ?: shown[positionOf(shown, _state.value.currentPage)]
                restoredPage = null
                // One update: the screen must never see the new page list with the old position.
                _state.update { it.copy(edits = changed, pages = shown, currentPage = target) }
                container.appScope.launch { repository.saveProgress(documentId, target) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            _state.update { it.copy(loading = false, error = "Cannot open this file. It needs a password, or access to it was removed.") }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = "Cannot open this file. It was moved, deleted, or is not a valid PDF.") }
        }
    }

    /** The page as the reader shows it, with its crop. Returns null when the page cannot be rendered. */
    suspend fun pageBitmap(page: Int, widthPx: Int, maxPixels: Int): Bitmap? =
        source?.bitmap(page, widthPx, maxPixels, _state.value.edits.cropFor(page))

    /** The whole page, without crop. For the crop screen. */
    suspend fun fullPageBitmap(page: Int, widthPx: Int, maxPixels: Int): Bitmap? =
        source?.bitmap(page, widthPx, maxPixels)

    suspend fun fullPageAspect(page: Int): Float? = runCatching { source?.aspectRatio(page) }.getOrNull()

    /**
     * The recognized text of [page] as the reader shows it, with its crop. Empty when the page has no text.
     * Returns null when the page cannot be read. Each page is recognized once, then the text comes from storage.
     */
    suspend fun pageText(page: Int): String? {
        val script = _state.value.ocrScript
        repository.pageText(documentId, page, script.name)?.let { return it }
        val crop = _state.value.edits.cropFor(page)
        val bitmap = source?.bitmap(page, OCR_WIDTH_PX, OCR_MAX_PIXELS, crop, cached = false) ?: return null
        val text = try {
            recognizer(script).recognize(bitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        // The crop changed during the recognition: the text is of the old crop. The page asks again.
        if (crop == _state.value.edits.cropFor(page)) repository.savePageText(documentId, page, script.name, text)
        return text
    }

    private fun recognizer(script: OcrScript): PageOcr {
        ocr?.let { if (it.script == script) return it else it.close() }
        return PageOcr(script).also { ocr = it }
    }

    fun setTextMode(on: Boolean) {
        _state.update { it.copy(textMode = on) }
        container.appScope.launch { repository.setTextMode(documentId, on) }
    }

    fun setOcrScript(script: OcrScript) {
        _state.update { it.copy(ocrScript = script) }
        container.appScope.launch { repository.setOcrScript(documentId, script.name) }
    }

    private fun highlight(page: Int, position: Int, text: String, color: HighlightColor) = HighlightEntity(
        documentId = documentId,
        page = page,
        position = position,
        text = text,
        color = color.name,
        createdAt = System.currentTimeMillis(),
    )

    fun addHighlight(page: Int, position: Int, text: String, color: HighlightColor) {
        viewModelScope.launch { repository.addHighlight(highlight(page, position, text, color)) }
    }

    /** Stores a note for the place of [draft]. */
    fun addNote(draft: NoteDraft, text: String) {
        val note = NoteEntity(
            documentId = documentId,
            page = draft.page,
            position = draft.position,
            quote = draft.quote,
            highlightId = draft.highlightId,
            text = text,
            createdAt = System.currentTimeMillis(),
        )
        // Selected text with a note becomes a highlight, so the page shows where the note is.
        val mark = if (draft.highlightId == null && draft.position != null && draft.quote != null) {
            highlight(draft.page, draft.position, draft.quote, HighlightColor.YELLOW)
        } else {
            null
        }
        viewModelScope.launch { repository.addNote(note, mark) }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch { repository.updateNote(note) }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { repository.deleteNote(note.id) }
    }

    fun updateHighlight(highlight: HighlightEntity) {
        viewModelScope.launch { repository.updateHighlight(highlight) }
    }

    fun deleteHighlight(highlight: HighlightEntity) {
        viewModelScope.launch { repository.deleteHighlight(highlight.id) }
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

    /**
     * Deletes the current page from the reader's view. The PDF file is not changed.
     * Returns the deleted page, or null when it is the only page left.
     */
    fun deleteCurrentPage(): Int? {
        val current = _state.value
        if (current.pages.size <= 1) return null
        val page = current.currentPage
        viewModelScope.launch { repository.setPagesHidden(documentId, listOf(page), hidden = true) }
        return page
    }

    fun restorePage(page: Int) {
        restoredPage = page
        viewModelScope.launch { repository.setPagesHidden(documentId, listOf(page), hidden = false) }
    }

    /** Stores [crop] for [page], or for the whole book. A crop that covers the full page removes the crop. */
    fun applyCrop(page: Int, crop: PageCrop, allPages: Boolean) {
        val bookCrop = _state.value.edits.bookCrop
        viewModelScope.launch {
            when {
                allPages -> repository.setBookCrop(documentId, crop.takeUnless { it.isFull })
                // A full crop on one page must stay stored when it overrides a crop of the whole book.
                crop.isFull && bookCrop == null -> repository.setPageCrop(documentId, page, null)
                else -> repository.setPageCrop(documentId, page, crop)
            }
        }
    }

    fun removeFromLibrary() {
        val stored = document ?: return
        container.appScope.launch { repository.remove(stored) }
    }

    override fun onCleared() {
        source?.close()
        ocr?.close()
    }

    companion object {
        const val DOCUMENT_ID_ARG = "documentId"

        // Wide enough for small print. A page of a book at this width has letters of about 25 pixels.
        private const val OCR_WIDTH_PX = 1600
        private const val OCR_MAX_PIXELS = 4_000_000

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ReaderApp
                ReaderViewModel(app.container, app, createSavedStateHandle())
            }
        }
    }
}
