package com.shreyas.pdfreader.ui.library

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.runtime.rememberCoroutineScope
import com.shreyas.pdfreader.MainActivity
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shreyas.pdfreader.data.Release
import com.shreyas.pdfreader.data.db.DocumentEntity

@Composable
fun LibraryScreen(
    onOpen: (documentId: Long) -> Unit,
    onSearch: () -> Unit,
    onChangeCover: (documentId: Long) -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val context = LocalContext.current
    val update by viewModel.update.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<DocumentEntity?>(null) }
    var removing by remember { mutableStateOf<DocumentEntity?>(null) }
    var sharing by remember { mutableStateOf<DocumentEntity?>(null) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    val pickPdf = { picker.launch(arrayOf("application/pdf")) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(viewModel) {
        viewModel.shares.collect { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // The Share Sheet reads the grant and the preview from the clip data.
            send.clipData = ClipData.newRawUri(file.name, uri)
            // Folio accepts shared PDFs, so without this it is in its own list.
            val chooser = Intent.createChooser(send, null)
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
            context.startActivity(chooser)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library", fontFamily = FontFamily.Serif) },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Find a PDF on the web")
                    }
                    IconButton(onClick = pickPdf, enabled = !importing) {
                        Icon(Icons.Filled.Add, contentDescription = "Import PDF")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            update?.let { release ->
                UpdateBanner(
                    release = release,
                    onDownload = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url))) },
                    onLater = { viewModel.dismissUpdate(release) },
                )
            }
            Box(Modifier.fillMaxSize()) {
                val loaded = items
                when {
                    loaded == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    loaded.isEmpty() -> EmptyLibrary(onImport = pickPdf, modifier = Modifier.align(Alignment.Center))
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 132.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(loaded, key = { it.document.id }) { item ->
                            DocumentCard(
                                item = item,
                                onOpen = { onOpen(item.document.id) },
                                onRename = { renaming = item.document },
                                onChangeCover = { onChangeCover(item.document.id) },
                                onShare = {
                                    scope.launch {
                                        if (viewModel.hasEdits(item.document)) {
                                            sharing = item.document
                                        } else {
                                            viewModel.share(item.document, edited = false)
                                        }
                                    }
                                },
                                onRemove = { removing = item.document },
                            )
                        }
                    }
                }
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    renaming?.let { document ->
        RenameDialog(
            document = document,
            onConfirm = {
                viewModel.rename(document, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    removing?.let { document ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from library?") },
            text = { Text("\"${document.title}\" leaves the library with its bookmarks and progress. The original file stays on the phone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.remove(document)
                        removing = null
                    },
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    sharing?.let { document ->
        val share = { edited: Boolean ->
            viewModel.share(document, edited)
            sharing = null
        }
        AlertDialog(
            onDismissRequest = { sharing = null },
            title = { Text("Share book") },
            text = { Text("The edited copy has your crops and does not have the pages you deleted. The original is the file as you added it.") },
            confirmButton = { TextButton(onClick = { share(true) }) { Text("Edited copy") } },
            dismissButton = { TextButton(onClick = { share(false) }) { Text("Original") } },
        )
    }
}

@Composable
private fun UpdateBanner(release: Release, onDownload: () -> Unit, onLater: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
        ) {
            Text(
                text = "Version ${release.version} is available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onLater) { Text("Later") }
            TextButton(onClick = onDownload) { Text("Download") }
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(32.dp),
    ) {
        Text("Your library is empty", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif)
        Text(
            text = "Import a PDF from your phone. The file stays where it is.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onImport) { Text("Import PDF") }
    }
}

@Composable
private fun RenameDialog(document: DocumentEntity, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember(document.id) { mutableStateOf(document.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }, enabled = title.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
