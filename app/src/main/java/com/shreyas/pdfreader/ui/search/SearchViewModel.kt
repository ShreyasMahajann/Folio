package com.shreyas.pdfreader.ui.search

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shreyas.pdfreader.ReaderApp
import com.shreyas.pdfreader.data.LibraryRepository
import com.shreyas.pdfreader.data.NotAPdfException
import com.shreyas.pdfreader.data.PdfDownloader
import com.shreyas.pdfreader.data.PdfLink
import com.shreyas.pdfreader.data.SettingsStore
import com.shreyas.pdfreader.pdf.PageSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** A downloaded PDF that is not in the library yet. */
data class PdfPreview(
    val fileName: String,
    val host: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val firstPageAspect: Float,
)

data class SearchUiState(
    val downloading: Boolean = false,
    /** 0..1, or null when the size is unknown. */
    val progress: Float? = null,
    val preview: PdfPreview? = null,
    val adding: Boolean = false,
)

sealed interface SearchEvent {
    data object Added : SearchEvent
    data class Failed(val message: String) : SearchEvent
}

/**
 * Web search flow: a tapped PDF link is downloaded into the cache and shown as a preview.
 * The file enters the library only when the user confirms. Otherwise it is deleted.
 */
class SearchViewModel(
    private val context: Context,
    private val repository: LibraryRepository,
    private val settings: SettingsStore,
    private val downloader: PdfDownloader,
) : ViewModel() {

    /** Past searches, newest first. */
    val history: StateFlow<List<String>> =
        settings.searchHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var download: Job? = null
    private var file: File? = null
    private var pages: PageSource? = null

    fun recordSearch(query: String) {
        viewModelScope.launch { settings.addSearch(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { settings.clearSearchHistory() }
    }

    fun openPreview(link: PdfLink) {
        val current = _state.value
        if (current.downloading || current.preview != null) return
        _state.value = SearchUiState(downloading = true)
        download = viewModelScope.launch {
            try {
                val downloaded = downloader.download(link) { progress -> _state.update { it.copy(progress = progress) } }
                file = downloaded
                val opened = PageSource.open(context, Uri.fromFile(downloaded))
                pages = opened
                if (opened.pageCount == 0) throw NotAPdfException()
                _state.value = SearchUiState(
                    preview = PdfPreview(
                        fileName = link.fileName,
                        host = Uri.parse(link.url).host.orEmpty(),
                        pageCount = opened.pageCount,
                        sizeBytes = downloaded.length(),
                        firstPageAspect = opened.aspectRatio(0),
                    ),
                )
            } catch (e: CancellationException) {
                discard()
                throw e
            } catch (e: NotAPdfException) {
                fail("This link is not a PDF file. The site may need a login.")
            } catch (e: SecurityException) {
                fail("This PDF needs a password.")
            } catch (e: Exception) {
                fail("Download failed. ${e.message.orEmpty()}".trim())
            }
        }
    }

    fun cancelDownload() {
        download?.cancel()
    }

    /** Returns null when the page cannot be rendered. */
    suspend fun pageBitmap(page: Int, widthPx: Int, maxPixels: Int): Bitmap? =
        pages?.bitmap(page, widthPx, maxPixels)

    fun addToLibrary() {
        val downloaded = file ?: return
        if (_state.value.adding) return
        _state.update { it.copy(adding = true) }
        viewModelScope.launch {
            try {
                repository.import(Uri.fromFile(downloaded))
                discard()
                _events.send(SearchEvent.Added)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("Cannot add this file to the library.")
            }
        }
    }

    /** Closes the preview and deletes the downloaded file. */
    fun discard() {
        pages?.close()
        pages = null
        file?.delete()
        file = null
        _state.value = SearchUiState()
    }

    private suspend fun fail(message: String) {
        discard()
        _events.send(SearchEvent.Failed(message))
    }

    override fun onCleared() = discard()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ReaderApp
                SearchViewModel(app, app.container.repository, app.container.settings, PdfDownloader(app))
            }
        }
    }
}

/** Both engines understand the `filetype:pdf` operator. */
enum class SearchEngine(val label: String, private val queryUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),

    // Google sometimes asks for a CAPTCHA inside an app. This engine is the way around the wait.
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=");

    fun searchUrl(query: String): String = queryUrl + Uri.encode("filetype:pdf ${query.trim()}")
}
