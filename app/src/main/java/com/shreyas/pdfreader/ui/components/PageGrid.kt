package com.shreyas.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shreyas.pdfreader.data.PageTheme
import com.shreyas.pdfreader.ui.reader.PageLoader
import com.shreyas.pdfreader.ui.reader.PdfPage

private const val THUMBNAIL_WIDTH_PX = 240
private const val THUMBNAIL_MAX_PIXELS = 240 * 480

/** What the grid shows about one page besides its picture. */
data class PageMark(val selected: Boolean = false, val deleted: Boolean = false, val version: Any? = null)

/** All pages of a PDF as small pictures. Pages are rendered when they scroll into view. */
@Composable
fun PageGrid(
    pageCount: Int,
    aspect: Float,
    loadPage: PageLoader,
    onClick: (page: Int) -> Unit,
    modifier: Modifier = Modifier,
    markOf: (page: Int) -> PageMark = { PageMark() },
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        items(count = pageCount, key = { it }) { page ->
            val mark = markOf(page)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onClick(page) },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .border(
                            width = if (mark.selected) 3.dp else 1.dp,
                            color = if (mark.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                ) {
                    PdfPage(
                        page = page,
                        widthPx = THUMBNAIL_WIDTH_PX,
                        maxPixels = THUMBNAIL_MAX_PIXELS,
                        theme = PageTheme.LIGHT,
                        loadPage = loadPage,
                        onAspectKnown = {},
                        version = mark.version,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (mark.deleted) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), Alignment.Center) {
                            Text("Deleted", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }
                }
                Text(
                    text = "${page + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
