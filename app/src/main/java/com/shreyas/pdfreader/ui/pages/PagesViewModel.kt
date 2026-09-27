package com.shreyas.pdfreader.ui.pages

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
import com.shreyas.pdfreader.pdf.PageEdits
import com.shreyas.pdfreader.pdf.PageSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PagesUiState(
    val loading: Boolean = true,
    /** 0 when the PDF cannot be opened. */
    val pageCount: Int = 0,
    val pageAspect: Float = 0.707f,
    val edits: PageEdits = PageEdits(),
    /** Zero-based page numbers of the file. */
    val selected: Set<Int> = emptySet(),
)

/** Deletes and restores pages of one book. The PDF file is not changed. */
class PagesViewModel(
    container: AppContainer,
    private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentId: Long = checkNotNull(savedStateHandle[DOCUMENT_ID_ARG])
    private val repository = container.repository

    private val _state = MutableStateFlow(PagesUiState())
    val state: StateFlow<PagesUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var pages: PageSource? = null

    init {
        viewModelScope.launch {
            val opened = repository.get(documentId)
                ?.let { runCatching { PageSource.open(context, Uri.parse(it.uri)) }.getOrNull() }
            pages = opened
            _state.update {
                it.copy(
                    loading = false,
                    pageCount = opened?.pageCount ?: 0,
                    pageAspect = opened?.takeIf { source -> source.pageCount > 0 }?.aspectRatio(0) ?: 0.707f,
                )
            }
        }
        viewModelScope.launch {
            repository.observePageEdits(documentId).collect { edits -> _state.update { it.copy(edits = edits) } }
        }
    }

    /** Thumbnails show the whole page, without crop, so a page is easy to recognise. */
    suspend fun pageBitmap(page: Int, widthPx: Int, maxPixels: Int): Bitmap? = pages?.bitmap(page, widthPx, maxPixels)

    fun toggle(page: Int) = _state.update {
        it.copy(selected = if (page in it.selected) it.selected - page else it.selected + page)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun deleteSelected() {
        val current = _state.value
        val remaining = (0 until current.pageCount).count { it !in current.edits.hidden && it !in current.selected }
        if (remaining == 0) {
            _messages.trySend("A book needs one page or more. Select fewer pages.")
            return
        }
        change { repository.setPagesHidden(documentId, current.selected, hidden = true) }
    }

    fun restoreSelected() {
        val selected = _state.value.selected
        change { repository.setPagesHidden(documentId, selected, hidden = false) }
    }

    fun restoreAll() = change { repository.restoreAllPages(documentId) }

    fun removeAllCrops() = change { repository.setBookCrop(documentId, null) }

    private fun change(action: suspend () -> Unit) {
        viewModelScope.launch {
            action()
            clearSelection()
        }
    }

    override fun onCleared() {
        pages?.close()
    }

    companion object {
        const val DOCUMENT_ID_ARG = "documentId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ReaderApp
                PagesViewModel(app.container, app, createSavedStateHandle())
            }
        }
    }
}
