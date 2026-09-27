package com.shreyas.pdfreader.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shreyas.pdfreader.ui.components.PageGrid
import com.shreyas.pdfreader.ui.components.PageMark
import com.shreyas.pdfreader.ui.reader.PageLoader

/** All pages of a book. Tap pages to select them, then delete or restore them. */
@Composable
fun PagesScreen(
    onBack: () -> Unit,
    viewModel: PagesViewModel = viewModel(factory = PagesViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val loadPage: PageLoader = remember(viewModel) { viewModel::pageBitmap }
    val selection = state.selected
    val hidden = state.edits.hidden

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selection.isEmpty()) "Manage pages" else "${selection.size} selected",
                        fontFamily = FontFamily.Serif,
                    )
                },
                navigationIcon = {
                    if (selection.isEmpty()) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the book")
                        }
                    } else {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    if (selection.isEmpty()) {
                        TextButton(onClick = viewModel::removeAllCrops, enabled = state.edits.crops.isNotEmpty()) {
                            Text("Remove all crops")
                        }
                        TextButton(onClick = viewModel::restoreAll, enabled = hidden.isNotEmpty()) {
                            Text("Restore all pages")
                        }
                    } else {
                        TextButton(onClick = viewModel::restoreSelected, enabled = selection.any { it in hidden }) {
                            Text("Restore")
                        }
                        TextButton(onClick = viewModel::deleteSelected, enabled = selection.any { it !in hidden }) {
                            Text("Delete")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.pageCount == 0 -> Text(
                    text = "This PDF cannot be opened.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> PageGrid(
                    pageCount = state.pageCount,
                    aspect = state.pageAspect,
                    loadPage = loadPage,
                    onClick = viewModel::toggle,
                    markOf = { page -> PageMark(selected = page in selection, deleted = page in hidden) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
