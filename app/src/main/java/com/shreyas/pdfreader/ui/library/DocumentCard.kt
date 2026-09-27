package com.shreyas.pdfreader.ui.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shreyas.pdfreader.data.LibraryItem
import com.shreyas.pdfreader.util.progressPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentCard(
    item: LibraryItem,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onChangeCover: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val document = item.document
    var menuOpen by remember { mutableStateOf(false) }
    val cover by produceState<ImageBitmap?>(initialValue = null, item.cover, item.coverVersion) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(item.cover.path)?.asImageBitmap() }
    }
    val percent = progressPercent(item.pageNumber - 1, item.pageCount)
    val opened = document.lastOpenedAt != null

    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { menuOpen = true },
                onLongClickLabel = "Document options",
            )
            .padding(6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .shadow(2.dp, RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            val image = cover
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp),
                )
            }
            if (opened) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    drawStopIndicator = {},
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Change cover") },
                    enabled = item.available,
                    onClick = {
                        menuOpen = false
                        onChangeCover()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove from library") },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }

        Text(
            text = document.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = when {
                !item.available -> "File not available"
                opened -> "$percent% · page ${item.pageNumber} of ${item.pageCount}"
                else -> "New · ${item.pageCount} pages"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (item.available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
