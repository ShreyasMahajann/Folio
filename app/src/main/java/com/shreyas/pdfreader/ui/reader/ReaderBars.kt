package com.shreyas.pdfreader.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shreyas.pdfreader.ui.components.ReaderIcons
import com.shreyas.pdfreader.util.progressPercent
import kotlin.math.roundToInt

@Composable
fun ReaderTopBar(
    title: String,
    bookmarked: Boolean,
    textMode: Boolean,
    onBack: () -> Unit,
    onToggleTextMode: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShowBookmarks: () -> Unit,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            // Names the view that a tap opens. In text mode it is the way back to the original pages.
            TextButton(onClick = onToggleTextMode) {
                Text(if (textMode) "PDF" else "Text", style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = onShowBookmarks) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Bookmarks, notes and highlights")
            }
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    imageVector = if (bookmarked) ReaderIcons.Bookmark else ReaderIcons.BookmarkOutline,
                    contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark this page",
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            TextButton(onClick = onShowSettings) {
                Text("Aa", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif)
            }
        }
    }
}

@Composable
fun ReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    /** The highlighter works on text. Null hides the button. */
    highlighter: Boolean?,
    onJumpToPage: (Int) -> Unit,
    onToggleHighlighter: () -> Unit,
    onNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Non-null while the thumb is dragged. The document jumps on release, not during the drag.
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shownPage = dragged?.roundToInt() ?: currentPage

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (pageCount > 1) {
                Slider(
                    value = dragged ?: currentPage.toFloat(),
                    onValueChange = { dragged = it },
                    onValueChangeFinished = {
                        dragged?.let { onJumpToPage(it.roundToInt()) }
                        dragged = null
                    },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    // The thumb sits at the screen edge on the first and last page.
                    // Without this, a drag from there is the system back gesture.
                    modifier = Modifier.systemGestureExclusion(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Page ${shownPage + 1} of $pageCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // At the bottom: the thumb reaches these while the hand holds the phone.
                if (highlighter != null) {
                    IconButton(onClick = onToggleHighlighter) {
                        Icon(
                            imageVector = ReaderIcons.Highlighter,
                            contentDescription = if (highlighter) "Turn the highlighter off" else "Turn the highlighter on",
                            tint = if (highlighter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                IconButton(onClick = onNote) {
                    Icon(ReaderIcons.Note, contentDescription = "Write a note about this page")
                }
                Text(
                    text = "${progressPercent(shownPage, pageCount)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
