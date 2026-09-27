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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.shreyas.pdfreader.ui.theme.ReaderTheme
import com.shreyas.pdfreader.ui.theme.palette
import kotlinx.coroutines.flow.MutableSharedFlow

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
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
            ReaderContent(state, loadedSettings, bookmarks, viewModel, onBack)
        }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    settings: ReaderSettings,
    bookmarks: List<BookmarkEntity>,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    var chromeVisible by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    val jumps = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    val loadPage: PageLoader = remember(viewModel) { viewModel::pageBitmap }

    fun jumpTo(page: Int) {
        viewModel.onPageChanged(page)
        jumps.tryEmit(page)
    }

    ReaderWindowEffects(settings, chromeVisible)

    Box(Modifier.fillMaxSize().background(settings.theme.palette.background)) {
        PdfPages(
            pageCount = state.pageCount,
            initialPage = state.currentPage,
            defaultAspect = state.defaultAspect,
            settings = settings,
            chromeVisible = chromeVisible,
            jumps = jumps,
            loadPage = loadPage,
            onPageChanged = viewModel::onPageChanged,
            onToggleChrome = { chromeVisible = !chromeVisible },
        )

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = state.title,
                bookmarked = bookmarks.any { it.page == state.currentPage },
                onBack = onBack,
                onToggleBookmark = viewModel::toggleBookmark,
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
                currentPage = state.currentPage,
                pageCount = state.pageCount,
                onJumpToPage = ::jumpTo,
            )
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = settings,
            onChange = viewModel::updateSettings,
            onDismiss = { showSettings = false },
        )
    }
    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = bookmarks,
            onOpen = { page ->
                showBookmarks = false
                jumpTo(page)
            },
            onDismiss = { showBookmarks = false },
        )
    }
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
