package com.shreyas.pdfreader.ui.search

import android.annotation.SuppressLint
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shreyas.pdfreader.data.PdfDownloader
import com.shreyas.pdfreader.data.PdfLink

/**
 * Web search for PDF files. Shows Google results for `filetype:pdf <query>` in a WebView.
 * A tap on a PDF link offers to add the file to the library.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var query by rememberSaveable { mutableStateOf("") }
    var searched by rememberSaveable { mutableStateOf(false) }
    var engine by rememberSaveable { mutableStateOf(SearchEngine.GOOGLE) }
    var pageProgress by remember { mutableFloatStateOf(1f) }
    val webView = rememberPdfSearchWebView(onPdf = viewModel::openPreview, onProgress = { pageProgress = it })

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SearchEvent.Added -> onAdded()
                is SearchEvent.Failed -> snackbar.showSnackbar(event.message)
            }
        }
    }
    LaunchedEffect(Unit) { if (!searched) focus.requestFocus() }

    // Back walks the browser history first, then leaves the screen.
    BackHandler(enabled = searched && state.preview == null && webView.canGoBack()) { webView.goBack() }

    fun search() {
        if (query.isBlank()) return
        keyboard?.hide()
        searched = true
        webView.loadUrl(engine.searchUrl(query))
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 56.dp, bottom = 4.dp),
                    ) {
                        SearchEngine.entries.forEach { option ->
                            FilterChip(
                                selected = option == engine,
                                onClick = {
                                    engine = option
                                    if (searched) search()
                                },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (searched) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                if (pageProgress < 1f) {
                    LinearProgressIndicator(progress = { pageProgress }, modifier = Modifier.fillMaxWidth())
                }
            } else {
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

/**
 * A WebView that browses normally and reports PDF links instead of opening them.
 * JavaScript is on because search result pages need it. The page gets no access to files
 * or to app code.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun rememberPdfSearchWebView(onPdf: (PdfLink) -> Unit, onProgress: (Float) -> Unit): WebView {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            fun offer(url: String, contentDisposition: String?, mimeType: String?) {
                val name = PdfDownloader.safeFileName(URLUtil.guessFileName(url, contentDisposition, mimeType))
                onPdf(PdfLink(url, name, settings.userAgentString))
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    return when {
                        url.scheme != "https" && url.scheme != "http" -> true // no app links, no intents
                        url.path.orEmpty().endsWith(".pdf", ignoreCase = true) -> {
                            offer(url.toString(), null, "application/pdf")
                            true
                        }
                        else -> false
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress / 100f)
            }
            // PDF links without ".pdf" in the address arrive here.
            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                if (mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)) {
                    offer(url, contentDisposition, mimeType)
                }
            }
        }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }
    return webView
}
