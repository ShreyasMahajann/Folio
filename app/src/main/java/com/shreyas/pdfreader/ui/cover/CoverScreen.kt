package com.shreyas.pdfreader.ui.cover

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shreyas.pdfreader.ui.components.PageGrid
import com.shreyas.pdfreader.ui.reader.PageLoader

private val TABS = listOf("PDF pages", "Gallery", "Web")

/** Lets the user pick the cover of a book: a page of the PDF, a photo from the phone, or an image from the web. */
@Composable
fun CoverScreen(
    onDone: () -> Unit,
    viewModel: CoverViewModel = viewModel(factory = CoverViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val loadPage: PageLoader = remember(viewModel) { viewModel::pageBitmap }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.useImage(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CoverEvent.Saved -> onDone()
                is CoverEvent.Failed -> snackbar.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Choose cover", fontFamily = FontFamily.Serif) },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    TABS.forEachIndexed { index, title ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                    }
                }
                if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                tab == 0 && state.pageCount == 0 -> Hint("This PDF cannot be opened. Use a photo or an image from the web.")
                tab == 0 -> PageGrid(
                    pageCount = state.pageCount,
                    aspect = state.pageAspect,
                    loadPage = loadPage,
                    onClick = viewModel::usePage,
                    modifier = Modifier.fillMaxSize(),
                )
                tab == 1 -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                ) {
                    Text(
                        text = "Use a photo from your phone as the cover.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(
                        onClick = {
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    ) { Text("Open gallery") }
                }
                else -> WebImages(title = state.title, onImage = viewModel::loadWebImage)
            }
        }
    }

    state.webImage?.let { image ->
        AlertDialog(
            onDismissRequest = viewModel::dismissWebImage,
            title = { Text("Use as cover?") },
            text = {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Image from the web",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmWebImage) { Text("Use as cover") } },
            dismissButton = { TextButton(onClick = viewModel::dismissWebImage) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

/** Image search in a WebView. A long press on an image offers it as the cover. */
@Composable
private fun WebImages(title: String, onImage: (link: String, userAgent: String?) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    var link by rememberSaveable { mutableStateOf("") }
    val webView = rememberImageSearchWebView(onImage)

    LaunchedEffect(webView, title) {
        webView.loadUrl("https://duckduckgo.com/?q=" + Uri.encode("$title book cover") + "&iax=images&ia=images")
    }

    fun useLink() {
        if (link.isBlank()) return
        keyboard?.hide()
        onImage(link, webView.settings.userAgentString)
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = "Long-press an image to use it. Or paste the link of an image:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                placeholder = { Text("https://") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { useLink() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = ::useLink, enabled = link.isNotBlank()) { Text("Use") }
        }
        AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

/** Same limits as the PDF search: the page gets no access to files or to app code. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun rememberImageSearchWebView(onImage: (link: String, userAgent: String?) -> Unit): WebView {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme != "https" && request.url.scheme != "http"
            }
            setOnLongClickListener {
                val hit = hitTestResult
                val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                    hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                val source = hit.extra
                if (isImage && source != null) {
                    onImage(source, settings.userAgentString)
                    true
                } else {
                    false
                }
            }
        }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }
    return webView
}
