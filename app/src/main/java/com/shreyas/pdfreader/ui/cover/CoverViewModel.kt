package com.shreyas.pdfreader.ui.cover

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
import com.shreyas.pdfreader.data.CoverWriter
import com.shreyas.pdfreader.data.NotAnImageException
import com.shreyas.pdfreader.data.db.DocumentEntity
import com.shreyas.pdfreader.pdf.PageSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoverUiState(
    val loading: Boolean = true,
    val title: String = "",
    /** 0 when the PDF cannot be opened. Gallery and web still work then. */
    val pageCount: Int = 0,
    val pageAspect: Float = 0.707f,
    /** True while an image is loaded or stored. */
    val working: Boolean = false,
    /** An image from the web that waits for confirmation. */
    val webImage: Bitmap? = null,
)

sealed interface CoverEvent {
    data object Saved : CoverEvent
    data class Failed(val message: String) : CoverEvent
}

class CoverViewModel(
    container: AppContainer,
    private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentId: Long = checkNotNull(savedStateHandle[DOCUMENT_ID_ARG])
    private val repository = container.repository
    private val covers = container.covers

    private val _state = MutableStateFlow(CoverUiState())
    val state: StateFlow<CoverUiState> = _state.asStateFlow()

    private val _events = Channel<CoverEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var document: DocumentEntity? = null
    private var pages: PageSource? = null

    init {
        viewModelScope.launch {
            val stored = repository.get(documentId)
            document = stored
            val opened = stored?.let { runCatching { PageSource.open(context, Uri.parse(it.uri)) }.getOrNull() }
            pages = opened
            _state.value = CoverUiState(
                loading = false,
                title = stored?.title.orEmpty(),
                pageCount = opened?.pageCount ?: 0,
                pageAspect = opened?.takeIf { it.pageCount > 0 }?.aspectRatio(0) ?: 0.707f,
            )
        }
    }

    suspend fun pageBitmap(page: Int, widthPx: Int, maxPixels: Int): Bitmap? = pages?.bitmap(page, widthPx, maxPixels)

    fun usePage(page: Int) = save("Cannot use this page.") {
        val width = CoverWriter.COVER_WIDTH_PX
        pages?.bitmap(page, width, width * width * 2) ?: throw IllegalStateException("Page cannot be rendered")
    }

    fun useImage(uri: Uri) = save("Cannot use this image.") { covers.load(uri) }

    /** Loads an image from the web and shows it for confirmation. */
    fun loadWebImage(link: String, userAgent: String?) {
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                val image = covers.loadFromWeb(link, userAgent)
                _state.update { it.copy(working = false, webImage = image) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotAnImageException) {
                fail("This link is not an image.")
            } catch (e: Exception) {
                fail("Cannot load the image. ${e.message.orEmpty()}".trim())
            }
        }
    }

    fun confirmWebImage() {
        val image = _state.value.webImage ?: return
        _state.update { it.copy(webImage = null) }
        save("Cannot use this image.") { image }
    }

    fun dismissWebImage() = _state.update { it.copy(webImage = null) }

    private fun save(errorMessage: String, image: suspend () -> Bitmap) {
        val stored = document ?: return
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                repository.setCover(stored, image())
                _state.update { it.copy(working = false) }
                _events.send(CoverEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(errorMessage)
            }
        }
    }

    private suspend fun fail(message: String) {
        _state.update { it.copy(working = false) }
        _events.send(CoverEvent.Failed(message))
    }

    override fun onCleared() {
        pages?.close()
    }

    companion object {
        const val DOCUMENT_ID_ARG = "documentId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ReaderApp
                CoverViewModel(app.container, app, createSavedStateHandle())
            }
        }
    }
}
