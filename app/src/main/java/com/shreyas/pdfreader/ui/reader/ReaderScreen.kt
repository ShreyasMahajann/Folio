package com.shreyas.pdfreader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shreyas.pdfreader.data.ReaderSettings
import com.shreyas.pdfreader.data.db.BookmarkEntity
import com.shreyas.pdfreader.data.db.HighlightEntity
import com.shreyas.pdfreader.pdf.PageCrop
import com.shreyas.pdfreader.pdf.positionOf
import com.shreyas.pdfreader.ui.theme.ReaderTheme
import com.shreyas.pdfreader.ui.theme.palette
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    onManagePages: () -> Unit,
    viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val loadedSettings = settings
    val error = state.error

    when {
        error != null -> ReaderError(
            message = error,
            onBack = onBack,
            onRemove = {
                viewModel.removeFromLibrary()
                onBack()
            },
        )
        state.loading || loadedSettings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> ReaderTheme(loadedSettings.theme) {
            ReaderContent(state, loadedSettings, bookmarks, highlights, viewModel, onBack, onManagePages)
        }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    settings: ReaderSettings,
    bookmarks: List<BookmarkEntity>,
    highlights: List<HighlightEntity>,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onManagePages: () -> Unit,
) {
    var chromeVisible by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    // Page number of the file that is open in the crop screen.
    var cropping by remember { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val jumps = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    val loadPage: PageLoader = remember(viewModel) { viewModel::pageBitmap }

    fun jumpTo(page: Int) {
        // A bookmark can point at a deleted page. The reader opens the next page that is left.
        val target = state.pages.getOrNull(positionOf(state.pages, page)) ?: return
        viewModel.onPageChanged(target)
        jumps.tryEmit(target)
    }

    ReaderWindowEffects(settings, chromeVisible)

    Box(Modifier.fillMaxSize().background(settings.theme.palette.background)) {
        if (state.textMode) {
            TextPages(
                pages = state.pages,
                edits = state.edits,
                currentPage = state.currentPage,
                defaultAspect = state.defaultAspect,
                settings = settings,
                script = state.ocrScript,
                highlights = highlights,
                chromeVisible = chromeVisible,
                jumps = jumps,
                loadText = remember(viewModel) { viewModel::pageText },
                loadPage = loadPage,
                onPageChanged = viewModel::onPageChanged,
                onToggleChrome = { chromeVisible = !chromeVisible },
                onAddHighlight = viewModel::addHighlight,
                onUpdateHighlight = viewModel::updateHighlight,
                onDeleteHighlight = viewModel::deleteHighlight,
            )
        } else {
            PdfPages(
                pages = state.pages,
                edits = state.edits,
                currentPage = state.currentPage,
                defaultAspect = state.defaultAspect,
                settings = settings,
                chromeVisible = chromeVisible,
                jumps = jumps,
                loadPage = loadPage,
                onPageChanged = viewModel::onPageChanged,
                onToggleChrome = { chromeVisible = !chromeVisible },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = state.title,
                bookmarked = bookmarks.any { it.page == state.currentPage },
                textMode = state.textMode,
                onBack = onBack,
                onToggleTextMode = { viewModel.setTextMode(!state.textMode) },
                onToggleBookmark = viewModel::toggleBookmark,
                onSwitchTheme = { viewModel.updateSettings(settings.copy(theme = settings.theme.next())) },
                onShowBookmarks = { showBookmarks = true },
                onShowSettings = { showSettings = true },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(
                currentPage = state.position,
                pageCount = state.pages.size,
                onJumpToPage = { position -> state.pages.getOrNull(position)?.let(::jumpTo) },
            )
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = settings,
            textMode = state.textMode,
            ocrScript = state.ocrScript,
            canDeletePage = state.pages.size > 1,
            onChange = viewModel::updateSettings,
            onTextMode = viewModel::setTextMode,
            onOcrScript = viewModel::setOcrScript,
            onCropPage = {
                showSettings = false
                cropping = state.currentPage
            },
            onDeletePage = {
                showSettings = false
                viewModel.deleteCurrentPage()?.let { deleted ->
                    scope.launch {
                        val answer = snackbar.showSnackbar(
                            message = "Page ${deleted + 1} deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Long,
                        )
                        if (answer == SnackbarResult.ActionPerformed) viewModel.restorePage(deleted)
                    }
                }
            },
            onManagePages = {
                showSettings = false
                onManagePages()
            },
            onDismiss = { showSettings = false },
        )
    }
    cropping?.let { page -> CropOverlay(page, state, viewModel, onClose = { cropping = null }) }
    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = bookmarks,
            highlights = highlights,
            onOpen = { page ->
                showBookmarks = false
                jumpTo(page)
            },
            onOpenHighlight = { highlight ->
                showBookmarks = false
                // A highlight shows only in text mode.
                viewModel.setTextMode(true)
                jumpTo(highlight.page)
            },
            onDismiss = { showBookmarks = false },
        )
    }
}

@Composable
private fun CropOverlay(
    page: Int,
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    onClose: () -> Unit,
) {
    CropScreen(
        page = page,
        initial = state.edits.cropFor(page) ?: PageCrop.FULL,
        loadAspect = viewModel::fullPageAspect,
        loadPage = remember(viewModel) { viewModel::fullPageBitmap },
        onApply = { crop, allPages ->
            viewModel.applyCrop(page, crop, allPages)
            onClose()
        },
        onCancel = onClose,
    )
}

@Composable
private fun ReaderError(message: String, onBack: () -> Unit, onRemove: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onBack) { Text("Back to library") }
            TextButton(onClick = onRemove) { Text("Remove from library") }
        }
    }
}
