package com.shreyas.pdfreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shreyas.pdfreader.data.FitMode
import com.shreyas.pdfreader.data.PageTheme
import com.shreyas.pdfreader.data.ReaderSettings
import com.shreyas.pdfreader.data.ReadingMode
import com.shreyas.pdfreader.data.db.BookmarkEntity
import com.shreyas.pdfreader.data.db.HighlightEntity
import com.shreyas.pdfreader.pdf.HighlightColor
import com.shreyas.pdfreader.pdf.OcrScript
import java.text.DateFormat
import java.util.Date

@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    textMode: Boolean,
    ocrScript: OcrScript,
    canDeletePage: Boolean,
    onChange: (ReaderSettings) -> Unit,
    onTextMode: (Boolean) -> Unit,
    onOcrScript: (OcrScript) -> Unit,
    onCropPage: () -> Unit,
    onDeletePage: () -> Unit,
    onManagePages: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Toggle("Text mode (reads the page as text)", textMode, onTextMode)
            if (textMode) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Text size", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { onChange(settings.copy(textSize = settings.textSize - ReaderSettings.TEXT_SIZE_STEP)) },
                        enabled = settings.textSize > ReaderSettings.TEXT_SIZES.start,
                    ) {
                        Text("A\u2212", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("${settings.textSize.toInt()}", style = MaterialTheme.typography.bodyLarge)
                    TextButton(
                        onClick = { onChange(settings.copy(textSize = settings.textSize + ReaderSettings.TEXT_SIZE_STEP)) },
                        enabled = settings.textSize < ReaderSettings.TEXT_SIZES.endInclusive,
                    ) {
                        Text("A+", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Label("Language of the book")
                // Five names are wider than a small phone.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    OcrScript.entries.forEach { script ->
                        TextButton(onClick = { onOcrScript(script) }) {
                            Text(
                                text = script.label,
                                maxLines = 1,
                                color = if (script == ocrScript) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            Choice(
                label = "Reading mode",
                options = listOf(ReadingMode.PAGED to "Page turn", ReadingMode.SCROLL to "Scroll"),
                selected = settings.mode,
                onSelect = { onChange(settings.copy(mode = it)) },
            )
            Choice(
                label = "Page fit",
                options = listOf(FitMode.PAGE to "Whole page", FitMode.WIDTH to "Full width"),
                selected = settings.fit,
                onSelect = { onChange(settings.copy(fit = it)) },
            )
            Choice(
                label = "Theme",
                options = listOf(PageTheme.LIGHT to "Light", PageTheme.SEPIA to "Sepia", PageTheme.DARK to "Dark"),
                selected = settings.theme,
                onSelect = { onChange(settings.copy(theme = it)) },
            )

            Label("Brightness")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.brightness ?: 0.5f,
                    onValueChange = { onChange(settings.copy(brightness = it)) },
                    valueRange = 0.01f..1f,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onChange(settings.copy(brightness = null)) },
                    enabled = settings.brightness != null,
                ) {
                    Text("System")
                }
            }

            Toggle("Keep screen awake", settings.keepAwake) { onChange(settings.copy(keepAwake = it)) }
            Toggle("Lock orientation", settings.lockOrientation) { onChange(settings.copy(lockOrientation = it)) }

            Label("This page")
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = onCropPage) { Text("Crop") }
                TextButton(onClick = onDeletePage, enabled = canDeletePage) { Text("Delete") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onManagePages) { Text("Manage pages") }
            }
        }
    }
}

@Composable
fun BookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    highlights: List<HighlightEntity>,
    onOpen: (page: Int) -> Unit,
    onOpenHighlight: (HighlightEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Bookmarks",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (bookmarks.isEmpty() && highlights.isEmpty()) {
            Text(
                text = "No bookmarks yet. Tap the ribbon in the top bar to bookmark a page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            )
        } else {
            val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
            LazyColumn(Modifier.padding(bottom = 24.dp)) {
                items(bookmarks, key = { "bookmark ${it.id}" }) { bookmark ->
                    ListItem(
                        headlineContent = { Text("Page ${bookmark.page + 1}") },
                        supportingContent = { Text(dateFormat.format(Date(bookmark.createdAt))) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpen(bookmark.page) }.padding(horizontal = 8.dp),
                    )
                }
                if (highlights.isNotEmpty()) {
                    item(key = "highlights") {
                        Text(
                            text = "Highlights",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
                items(highlights, key = { "highlight ${it.id}" }) { highlight ->
                    ListItem(
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .background(HighlightColor.of(highlight.color).color, CircleShape),
                            )
                        },
                        headlineContent = { Text(highlight.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            val note = highlight.note?.let { "$it\n" }.orEmpty()
                            Text("${note}Page ${highlight.page + 1}")
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpenHighlight(highlight) }.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun <T> Choice(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Label(label)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, text) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(text)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
