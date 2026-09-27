package com.shreyas.pdfreader.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.shreyas.pdfreader.data.PageTheme
import com.shreyas.pdfreader.ui.theme.palette

typealias PageLoader = suspend (page: Int, widthPx: Int, maxPixels: Int) -> Bitmap?

/**
 * One page. The caller sets the size through [modifier].
 *
 * A change of [widthPx] (zoom) starts a new render. The previous bitmap stays on screen until
 * the sharper one is ready. Leaving the screen cancels a render that has not started.
 */
@Composable
fun PdfPage(
    page: Int,
    widthPx: Int,
    maxPixels: Int,
    theme: PageTheme,
    loadPage: PageLoader,
    onAspectKnown: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(page) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(page, widthPx, maxPixels) {
        if (widthPx <= 0) return@LaunchedEffect
        loadPage(page, widthPx, maxPixels)?.let { rendered ->
            bitmap = rendered.asImageBitmap()
            onAspectKnown(rendered.width.toFloat() / rendered.height)
        }
    }

    val palette = theme.palette
    // The placeholder has the colour of an empty page under the same filter.
    val paper = if (theme == PageTheme.DARK) Color.Black else if (theme == PageTheme.SEPIA) Color(0xFFF4ECD8) else Color.White
    Box(modifier.background(paper)) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "Page ${page + 1}",
                contentScale = ContentScale.FillBounds,
                colorFilter = palette.filter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
