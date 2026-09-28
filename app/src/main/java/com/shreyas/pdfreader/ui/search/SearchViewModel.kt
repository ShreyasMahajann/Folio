package com.shreyas.pdfreader.ui.search

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebSettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shreyas.pdfreader.ReaderApp
import com.shreyas.pdfreader.data.CoverWriter
import com.shreyas.pdfreader.data.HttpStatusException
import com.shreyas.pdfreader.data.linkKey
import com.shreyas.pdfreader.data.mergeLinks
import com.shreyas.pdfreader.data.LibraryRepository
import com.shreyas.pdfreader.data.NotAPdfException
import com.shreyas.pdfreader.data.PdfDownloader
import com.shreyas.pdfreader.data.PdfLink
import com.shreyas.pdfreader.data.RemotePdf
import com.shreyas.pdfreader.data.SearchBlockedException
import com.shreyas.pdfreader.data.SettingsStore
import com.shreyas.pdfreader.data.TooLargeException
import com.shreyas.pdfreader.data.SearchBrowser
import com.shreyas.pdfreader.data.SearchEngine
import com.shreyas.pdfreader.data.worthRetry
import com.shreyas.pdfreader.pdf.PageSource
import com.shreyas.pdfreader.pdf.PdfDocumentRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.UnknownHostException

/** A downloaded PDF that is not in the library yet. */
data class PdfPreview(
    val fileName: String,
    val host: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val firstPageAspect: Float,
)

/** What a result card shows in place of the first page. */
sealed interface Thumbnail {
    data object Waiting : Thumbnail

    /** [image] is a picture file of the first page. */
    data class Ready(val pageCount: Int, val sizeBytes: Long, val image: File) : Thumbnail

    /** The server gives only the whole file, and the file is larger than [SearchViewModel.THUMBNAIL_MAX_BYTES]. */
    data object TooLarge : Thumbnail

    /** The connection failed. A tap tries again. */
    data object Failed : Thumbnail
}

data class SearchResult(
    val url: String,
    val fileName: String,
    val host: String,
    val thumbnail: Thumbnail = Thumbnail.Waiting,
)

data class SearchUiState(
    /** Null before the first search. */
    val query: String? = null,
    val results: List<SearchResult> = emptyList(),
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val downloading: Boolean = false,
    /** 0..1, or null when the size is unknown. */
    val progress: Float? = null,
    val preview: PdfPreview? = null,
    val adding: Boolean = false,
)

sealed interface SearchEvent {
    data object Added : SearchEvent
    data class Failed(val message: String) : SearchEvent

    /** The request for results failed. The user can ask again. */
    data class SearchFailed(val message: String) : SearchEvent
}

/**
 * Web search flow: the PDF links of DuckDuckGo become cards. The file of a card on screen is
 * downloaded into the cache for its first page and page count. A tapped card opens as a preview.
 * The file enters the library only when the user confirms.
 */
class SearchViewModel(
    private val context: Context,
    private val repository: LibraryRepository,
    private val settings: SettingsStore,
    private val downloader: PdfDownloader,
    private val covers: CoverWriter,
) : ViewModel() {

    /** Past searches, newest first. */
    val history: StateFlow<List<String>> =
        settings.searchHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val userAgent by lazy { WebSettings.getDefaultUserAgent(context) }
    private val cache = File(context.cacheDir, "search")

    // Work of one search. A new search stops the work of the search before.
    private var work = newWork()
    // Each search has its own directory, so a stopped download cannot touch a file of the new search.
    private var searchNumber = 0
    private val duck = SearchBrowser(context, SearchEngine.DUCKDUCKGO)
    private val google = SearchBrowser(context, SearchEngine.GOOGLE)
    // True when an engine has no more results for this search.
    private var duckDone = false
    private var googleDone = false
    private var firstPage = true
    private val thumbnails = mutableMapOf<String, Job>()
    private val gate = Semaphore(THUMBNAILS_AT_ONCE)

    private var download: Job? = null
    private var file: File? = null
    private var pages: PageSource? = null

    fun clearHistory() {
        viewModelScope.launch { settings.clearSearchHistory() }
    }

    fun search(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        discard()
        work.cancel()
        work = newWork()
        searchNumber++
        duckDone = false
        googleDone = false
        firstPage = true
        thumbnails.clear()
        _state.value = SearchUiState(query = text)
        viewModelScope.launch { settings.addSearch(text) }
        loadMore(atLeast = FIRST_RESULTS)
    }

    /** Asks for result pages until [atLeast] new links are there. */
    fun loadMore(atLeast: Int = 1) {
        val current = _state.value
        val query = current.query ?: return
        if (current.loadingMore || current.endReached) return
        _state.update { it.copy(loadingMore = true) }
        val directory = searchDirectory()
        work.launch {
            withContext(Dispatchers.IO) {
                cache.listFiles()?.filter { it != directory }?.forEach { it.deleteRecursively() }
            }
            var added = 0
            var rounds = 0
            var duckFailed = false
            var googleFailed = false
            var error: Throwable? = null

            // The results of an engine show when they come. They do not wait for the other engine.
            fun add(links: List<String>) {
                val fresh = mergeLinks(listOf(links), _state.value.results.map { linkKey(it.url) }.toSet())
                added += fresh.size
                _state.update { it.copy(results = it.results + fresh.map(::result)) }
            }
            while (added < atLeast && !(duckDone && googleDone) && rounds < ROUNDS_AT_ONCE) {
                rounds++
                val first = firstPage
                // Both engines are asked at the same time. One engine that fails does not stop the other.
                coroutineScope {
                    if (!duckDone) launch {
                        try {
                            val page = duck.nextPage(query, first)
                            duckDone = !page.more
                            add(page.links)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e
                            duckFailed = true
                            duckDone = true
                        }
                    }
                    if (!googleDone) launch {
                        try {
                            val page = google.nextPage(query, first)
                            googleDone = !page.more
                            add(page.links)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = error ?: e
                            googleFailed = true
                            googleDone = true
                        }
                    }
                }
                firstPage = false
            }
            val failure = error.takeIf { added == 0 }
            if (failure != null) {
                // Nothing came. "Retry" asks the engines that failed again.
                if (duckFailed) duckDone = false
                if (googleFailed) googleDone = false
            }
            _state.update { it.copy(loadingMore = false, endReached = duckDone && googleDone) }
            if (failure != null) {
                _events.send(
                    SearchEvent.SearchFailed(
                        when (failure) {
                            is UnknownHostException -> "No internet connection."
                            is SearchBlockedException -> failure.message.orEmpty()
                            else -> "The search did not work."
                        },
                    ),
                )
            }
        }
    }

    /** Called by a card that comes on screen. */
    fun loadThumbnail(url: String) {
        val result = find(url) ?: return
        if (result.thumbnail != Thumbnail.Waiting || thumbnails[url]?.isActive == true) return
        val directory = target(result).parentFile
        // Not the file of the preview. A tap on the card can start the download for the preview at any time.
        val whole = File(directory, "whole-file-for-thumbnail")
        thumbnails[url] = work.launch {
            gate.withPermit {
                try {
                    // Only the parts of the file for the first page are read, when the server can give parts.
                    val remote = RemotePdf.open(context, downloader, link(result))
                    val renderer = if (remote != null) {
                        PdfDocumentRenderer.open(remote.first)
                    } else {
                        downloader.download(link(result), whole, THUMBNAIL_MAX_BYTES)
                        PdfDocumentRenderer.open(context, Uri.fromFile(whole))
                    }
                    val ready = try {
                        if (renderer.pageCount == 0) throw NotAPdfException()
                        val width = CoverWriter.COVER_WIDTH_PX
                        val page = renderer.render(0, width, width * width * 2)
                        val image = File(directory, "thumbnail.webp")
                        val size = remote?.second ?: whole.length()
                        withContext(Dispatchers.IO) { covers.write(page, image) }
                        Thumbnail.Ready(renderer.pageCount, size, image)
                    } finally {
                        renderer.close()
                    }
                    set(url, ready)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: TooLargeException) {
                    set(url, Thumbnail.TooLarge)
                } catch (e: Exception) {
                    if (worthRetry(e) || e is UnknownHostException) {
                        set(url, Thumbnail.Failed)
                    } else {
                        // Not a PDF, a dead link, or a PDF with a password. The user can do nothing with it.
                        _state.update { state -> state.copy(results = state.results.filter { it.url != url }) }
                    }
                } finally {
                    whole.delete()
                }
            }
        }
    }

    /** Called by a card that leaves the screen, so the cards on the screen do not wait for it. */
    fun cancelThumbnail(url: String) {
        thumbnails.remove(url)?.cancel()
    }

    fun openPreview(url: String) {
        val current = _state.value
        if (current.downloading || current.preview != null) return
        val result = find(url) ?: return
        val target = target(result)
        // A file from a preview before. A download that failed or stopped leaves no file.
        val cached = target.exists()
        _state.update { it.copy(downloading = !cached, progress = null) }
        download = work.launch {
            try {
                if (!cached) {
                    downloader.download(link(result), target) { progress -> _state.update { it.copy(progress = progress) } }
                }
                file = target
                trimCache()
                val opened = PageSource.open(context, Uri.fromFile(target))
                pages = opened
                if (opened.pageCount == 0) throw NotAPdfException()
                val preview = PdfPreview(
                    fileName = result.fileName,
                    host = result.host,
                    pageCount = opened.pageCount,
                    sizeBytes = target.length(),
                    firstPageAspect = opened.aspectRatio(0),
                )
                _state.update { it.copy(downloading = false, progress = null, preview = preview) }
            } catch (e: CancellationException) {
                discard()
                throw e
            } catch (e: NotAPdfException) {
                fail("This link is not a PDF file. The site may need a login.")
            } catch (e: SecurityException) {
                fail("This PDF needs a password.")
            } catch (e: Exception) {
                fail(downloadMessage(e))
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

    /** Closes the preview. The file stays in the cache, so the same preview opens at once. */
    fun discard() {
        pages?.close()
        pages = null
        file = null
        _state.update { it.copy(downloading = false, progress = null, preview = null, adding = false) }
    }

    private suspend fun fail(message: String) {
        discard()
        _events.send(SearchEvent.Failed(message))
    }

    override fun onCleared() {
        discard()
        duck.close()
        google.close()
        cache.deleteRecursively()
    }

    private fun newWork(): CoroutineScope = viewModelScope + SupervisorJob(viewModelScope.coroutineContext.job)

    private fun find(url: String): SearchResult? = _state.value.results.firstOrNull { it.url == url }

    private fun set(url: String, thumbnail: Thumbnail) {
        _state.update { state ->
            state.copy(results = state.results.map { if (it.url == url) it.copy(thumbnail = thumbnail) else it })
        }
    }

    private fun result(url: String): SearchResult {
        val address = Uri.parse(url)
        return SearchResult(
            url = url,
            fileName = PdfDownloader.safeFileName(address.lastPathSegment.orEmpty()),
            host = address.host.orEmpty(),
        )
    }

    private fun link(result: SearchResult) = PdfLink(result.url, result.fileName, userAgent, referer = "https://duckduckgo.com/")

    private fun searchDirectory() = File(cache, searchNumber.toString())

    // The file keeps its name, because the library takes the title from the name.
    private fun target(result: SearchResult) =
        File(File(searchDirectory(), result.url.hashCode().toUInt().toString(16)), result.fileName)

    /** Deletes the oldest files of previews while the cache is above [CACHE_MAX_BYTES]. The thumbnails stay. */
    private suspend fun trimCache() = withContext(Dispatchers.IO) {
        val files = cache.walk()
            .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) && it != file }
            .sortedBy { it.lastModified() }
            .toList()
        var total = files.sumOf { it.length() }
        for (old in files) {
            if (total <= CACHE_MAX_BYTES) break
            total -= old.length()
            old.delete()
        }
    }

    companion object {
        const val FIRST_RESULTS = 20
        const val THUMBNAIL_MAX_BYTES = 25_000_000L
        private const val THUMBNAILS_AT_ONCE = 3
        private const val ROUNDS_AT_ONCE = 5
        private const val CACHE_MAX_BYTES = 300_000_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ReaderApp
                SearchViewModel(app, app.container.repository, app.container.settings, PdfDownloader(app), app.container.covers)
            }
        }
    }
}

/** What the user reads when a download fails. The text of the exception is for developers. */
fun downloadMessage(error: Throwable): String = when {
    error is HttpStatusException -> "The site refused the download (status ${error.status}). Pick another result."
    error is UnknownHostException -> "No internet connection."
    worthRetry(error) -> "The site closed the connection. Try again, or pick another result."
    else -> "Download failed. ${error.message.orEmpty()}".trim()
}
