package com.shreyas.pdfreader.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shreyas.pdfreader.data.PageTheme
import com.shreyas.pdfreader.ui.reader.PageLoader
import com.shreyas.pdfreader.ui.reader.PdfPage

private const val PREVIEW_MAX_PIXELS = 4_000_000

/**
 * Shows the pages of a downloaded PDF, so the user can check that it is the right file
 * before it enters the library.
 */
@Composable
fun PdfPreviewScreen(
    preview: PdfPreview,
    adding: Boolean,
    loadPage: PageLoader,
    onAdd: () -> Unit,
    onDiscard: () -> Unit,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val aspects = remember(preview) { mutableStateMapOf<Int, Float>() }

    BackHandler(onBack = onDiscard)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        text = preview.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Serif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Preview · ${preview.pageCount} pages · ${formatSize(preview.sizeBytes)} · ${preview.host}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .onSizeChanged { widthPx = it.width },
            ) {
                items(count = preview.pageCount, key = { it }) { page ->
                    PdfPage(
                        page = page,
                        widthPx = widthPx,
                        maxPixels = PREVIEW_MAX_PIXELS,
                        theme = PageTheme.LIGHT,
                        loadPage = loadPage,
                        onAspectKnown = { aspects[page] = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspects[page] ?: preview.firstPageAspect),
                    )
                }
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    OutlinedButton(onClick = onDiscard, enabled = !adding, modifier = Modifier.weight(1f)) {
                        Text("Discard")
                    }
                    Button(onClick = onAdd, enabled = !adding, modifier = Modifier.weight(1f)) {
                        Text(if (adding) "Adding" else "Add to library")
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "${(bytes / 1000).coerceAtLeast(1)} kB"
}
