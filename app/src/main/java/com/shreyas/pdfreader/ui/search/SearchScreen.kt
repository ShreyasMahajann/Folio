package com.shreyas.pdfreader.ui.search

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The next results are asked for when this number of cards, or fewer, is below the screen.
private const val CARDS_BEFORE_THE_END = 6

/**
 * Web search for PDF files. Shows the DuckDuckGo results for `filetype:pdf <query>` as cards
 * with the first page and the page count. A tap on a card opens a preview.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var query by rememberSaveable { mutableStateOf("") }
    val searched = state.query != null

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SearchEvent.Added -> onAdded()
                // A snackbar waits for its end. The next event must not wait for that.
                is SearchEvent.Failed -> launch { snackbar.showSnackbar(event.message) }
                is SearchEvent.SearchFailed -> launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val answer = snackbar.showSnackbar(event.message, actionLabel = "Retry")
                    if (answer == SnackbarResult.ActionPerformed) viewModel.loadMore()
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (!searched) focus.requestFocus() }

    fun search() {
        if (query.isBlank()) return
        keyboard?.hide()
        viewModel.search(query)
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                    }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Book or document name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).padding(end = 12.dp).focusRequester(focus),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (searched) {
                SearchResults(
                    state = state,
                    onVisible = viewModel::loadThumbnail,
                    onHidden = viewModel::cancelThumbnail,
                    onOpen = viewModel::openPreview,
                    onEnd = viewModel::loadMore,
                )
            } else if (history.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                ) {
                    Text("Find a PDF on the web", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif)
                    Text(
                        text = "Type a name and search. Tap a PDF in the results to preview it, then add it to your library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                SearchHistory(
                    history = history,
                    onSearch = {
                        query = it
                        search()
                    },
                    onClear = viewModel::clearHistory,
                )
            }
        }
    }

    // Drawn over the search results, so the results are still there after a discard.
    state.preview?.let { preview ->
        PdfPreviewScreen(
            preview = preview,
            adding = state.adding,
            loadPage = remember(viewModel) { viewModel::pageBitmap },
            onAdd = viewModel::addToLibrary,
            onDiscard = viewModel::discard,
        )
    }
    if (state.downloading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Loading preview") },
            text = {
                val progress = state.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = viewModel::cancelDownload) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    onVisible: (String) -> Unit,
    onHidden: (String) -> Unit,
    onOpen: (String) -> Unit,
    onEnd: () -> Unit,
) {
    val grid = rememberLazyGridState()
    // A new search starts at the top.
    LaunchedEffect(state.query) { grid.scrollToItem(0) }
    LaunchedEffect(grid) {
        snapshotFlow {
            val layout = grid.layoutInfo
            layout.totalItemsCount - (layout.visibleItemsInfo.lastOrNull()?.index ?: 0)
        }.collect { below -> if (below <= CARDS_BEFORE_THE_END) onEnd() }
    }

    if (state.results.isEmpty() && !state.loadingMore) {
        Box(Modifier.fillMaxSize()) {
            Text(
                text = "No PDF found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.results, key = { it.url }) { result ->
            DisposableEffect(result.url) {
                onVisible(result.url)
                onDispose { onHidden(result.url) }
            }
            ResultCard(result = result, onOpen = { onOpen(result.url) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                when {
                    state.loadingMore -> CircularProgressIndicator(Modifier.size(28.dp))
                    state.endReached -> Text(
                        text = "No more results",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: SearchResult, onOpen: () -> Unit) {
    val thumbnail = result.thumbnail
    val image by produceState<ImageBitmap?>(initialValue = null, thumbnail) {
        value = (thumbnail as? Thumbnail.Ready)?.let {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it.image.path)?.asImageBitmap() }
        }
    }

    Column(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClickLabel = "Preview", onClick = onOpen)
            .padding(6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .shadow(2.dp, RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            val picture = image
            when {
                picture != null -> Image(
                    bitmap = picture,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
                thumbnail == Thumbnail.TooLarge || thumbnail == Thumbnail.Failed -> Text(
                    text = if (thumbnail == Thumbnail.TooLarge) "No picture\nTap to preview" else "Did not load\nTap to try again",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp),
                )
                else -> CircularProgressIndicator(Modifier.size(28.dp))
            }
        }

        Text(
            text = result.fileName.removeSuffix(".pdf").removeSuffix(".PDF"),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = when (thumbnail) {
                is Thumbnail.Ready -> "${thumbnail.pageCount} pages · ${formatSize(thumbnail.sizeBytes)} · ${result.host}"
                else -> result.host
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchHistory(history: List<String>, onSearch: (String) -> Unit, onClear: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            ) {
                Text(
                    text = "Recent searches",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("Clear history") }
            }
        }
        items(history, key = { it }) { entry ->
            Text(
                text = entry,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSearch(entry) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}
