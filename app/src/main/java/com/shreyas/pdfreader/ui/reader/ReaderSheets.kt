package com.shreyas.pdfreader.ui.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shreyas.pdfreader.data.FitMode
import com.shreyas.pdfreader.data.LineSpacing
import com.shreyas.pdfreader.data.PageMargins
import com.shreyas.pdfreader.data.PageTheme
import com.shreyas.pdfreader.data.ReaderFont
import com.shreyas.pdfreader.data.ReaderSettings
import com.shreyas.pdfreader.data.ReadingMode
import com.shreyas.pdfreader.data.db.BookmarkEntity
import com.shreyas.pdfreader.data.db.HighlightEntity
import com.shreyas.pdfreader.data.db.NoteEntity
import com.shreyas.pdfreader.pdf.HighlightColor
import com.shreyas.pdfreader.pdf.OcrScript
import com.shreyas.pdfreader.ui.theme.palette
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * The "Aa" panel. It opens with the settings that change often, and fits the screen without a scroll.
 * The other settings are one step away, behind "More settings".
 */
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
    var more by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        AnimatedContent(
            targetState = more,
            transitionSpec = {
                // "More" comes from the right and goes back to the right.
                val from: (Int) -> Int = { if (targetState) it / 4 else -it / 4 }
                (slideInHorizontally(Motion.standard(), from) + fadeIn(Motion.standard())) togetherWith
                    (slideOutHorizontally(Motion.standard()) { -from(it) } + fadeOut(Motion.fast()))
            },
            label = "settings page",
        ) { showMore ->
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
            ) {
                if (!showMore) {
                    if (textMode) {
                        StepDots(
                            steps = ReaderSettings.TEXT_SIZES.size,
                            selected = ReaderSettings.TEXT_SIZES.indexOf(settings.textSize).coerceAtLeast(0),
                            onSelect = { onChange(settings.copy(textSize = ReaderSettings.TEXT_SIZES[it])) },
                        )
                    }
                    ThemeDots(settings.theme) { onChange(settings.copy(theme = it)) }
                    Brightness(settings, onChange)
                    if (textMode) {
                        Choice(
                            label = "Font",
                            options = listOf(ReaderFont.SERIF to "Serif", ReaderFont.SANS to "Sans"),
                            selected = settings.font,
                            onSelect = { onChange(settings.copy(font = it)) },
                        )
                        Choice(
                            label = "Line spacing",
                            options = listOf(
                                LineSpacing.COMPACT to "Compact",
                                LineSpacing.COMFORTABLE to "Comfortable",
                                LineSpacing.SPACIOUS to "Spacious",
                            ),
                            selected = settings.spacing,
                            onSelect = { onChange(settings.copy(spacing = it)) },
                        )
                    } else {
                        PageChoices(settings, onChange)
                    }
                    TextButton(onClick = { more = true }, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) {
                        Text("More settings")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { more = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the first settings")
                        }
                        Text("More settings", style = MaterialTheme.typography.titleMedium)
                    }
                    Toggle("Text mode (reads the page as text)", textMode, onTextMode)
                    if (textMode) {
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
                        Choice(
                            label = "Margins",
                            options = listOf(
                                PageMargins.NARROW to "Narrow",
                                PageMargins.STANDARD to "Standard",
                                PageMargins.WIDE to "Wide",
                            ),
                            selected = settings.margins,
                            onSelect = { onChange(settings.copy(margins = it)) },
                        )
                        Toggle("Justify text", settings.justify) { onChange(settings.copy(justify = it)) }
                        // The first page has these when the reader shows the pages of the PDF.
                        PageChoices(settings, onChange)
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
    }
}

@Composable
private fun PageChoices(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
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
}

/**
 * Text size as steps between a small and a large "A". A tap or a drag picks the nearest step.
 * The mark of the picked step moves to its place.
 */
@Composable
private fun StepDots(steps: Int, selected: Int, onSelect: (Int) -> Unit) {
    val last = steps - 1
    val place by animateFloatAsState(selected.toFloat() / last, Motion.standard(), label = "step")
    val pick by rememberUpdatedState(onSelect)
    val current by rememberUpdatedState(selected)
    val line = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.onSurfaceVariant
    val mark = MaterialTheme.colorScheme.primary

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("A", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Serif)
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .padding(horizontal = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Text size"
                    stateDescription = "${selected + 1} of $steps"
                    progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..last.toFloat(), last - 1)
                    setProgress { value ->
                        val step = value.roundToInt().coerceIn(0, last)
                        if (step != selected) onSelect(step)
                        true
                    }
                }
                .pointerInput(steps) {
                    val edge = EDGE.toPx()
                    fun stepAt(x: Float) = ((x - edge) / (size.width - 2 * edge) * last).roundToInt().coerceIn(0, last)
                    detectTapGestures { pick(stepAt(it.x)) }
                }
                .pointerInput(steps) {
                    val edge = EDGE.toPx()
                    fun stepAt(x: Float) = ((x - edge) / (size.width - 2 * edge) * last).roundToInt().coerceIn(0, last)
                    detectHorizontalDragGestures { change, _ ->
                        val step = stepAt(change.position.x)
                        if (step != current) pick(step)
                    }
                }
                .drawBehind {
                    val edge = EDGE.toPx()
                    val width = size.width - 2 * edge
                    val y = size.height / 2
                    drawLine(line, Offset(edge, y), Offset(edge + width, y), strokeWidth = 2.dp.toPx())
                    for (step in 0..last) drawCircle(dot, 3.dp.toPx(), Offset(edge + width * step / last, y))
                    drawCircle(mark, 9.dp.toPx(), Offset(edge + width * place, y))
                },
        )
        Text("A", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
    }
}

/** Room between the ends of the step line and the box, so the mark is not cut. */
private val EDGE = 12.dp

/** The three page themes as the colors of their pages. */
@Composable
private fun ThemeDots(selected: PageTheme, onSelect: (PageTheme) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        PageTheme.entries.forEach { theme ->
            val picked = theme == selected
            val ring = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 72.dp, height = 48.dp)
                    .clickable { onSelect(theme) }
                    .semantics {
                        contentDescription = "Theme ${theme.name.lowercase()}"
                        this.selected = picked
                    },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(theme.palette.background, CircleShape)
                        .border(if (picked) 2.dp else 1.dp, ring, CircleShape),
                ) {
                    Text(
                        text = "A",
                        color = if (theme == PageTheme.DARK) Color.White else Color.Black,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Serif,
                    )
                }
            }
        }
    }
}

@Composable
private fun Brightness(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = settings.brightness ?: 0.5f,
            onValueChange = { onChange(settings.copy(brightness = it)) },
            valueRange = 0.01f..1f,
            modifier = Modifier.weight(1f).semantics { contentDescription = "Brightness" },
        )
        TextButton(
            onClick = { onChange(settings.copy(brightness = null)) },
            enabled = settings.brightness != null,
        ) {
            Text("System")
        }
    }
}

/**
 * Notes of one page. Opens ready to write: a note needs no selected text.
 * A draft with a quote makes a note about that text.
 */
@Composable
fun NotesSheet(
    draft: NoteDraft,
    /** All notes of the book. */
    notes: List<NoteEntity>,
    onAdd: (String) -> Unit,
    onUpdate: (NoteEntity) -> Unit,
    onDelete: (NoteEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    // A highlight has one note. The note opens again when the highlight has it already.
    var editing by remember {
        mutableStateOf(notes.firstOrNull { draft.highlightId != null && it.highlightId == draft.highlightId })
    }
    var text by remember { mutableStateOf(editing?.text.orEmpty()) }
    val others = notes.filter { it.page == draft.page && it.id != editing?.id }
    val quote = editing?.quote ?: draft.quote
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "${if (editing != null) "Note" else "New note"} · Page ${draft.page + 1}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (quote != null) Quote(quote, Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Write a note about this page") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).focusRequester(focus),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val note = editing
                        if (note != null) onUpdate(note.copy(text = text.trim())) else onAdd(text.trim())
                        onDismiss()
                    },
                ) {
                    Text("Save")
                }
            }

            if (others.isNotEmpty()) Label("Notes on this page")
            others.forEach { note ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable {
                                editing = note
                                text = note.text
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        note.quote?.let { Quote(it) }
                        Text(note.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    IconButton(onClick = { onDelete(note) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete this note")
                    }
                }
            }
        }
    }
}

/** The text of the book that a note is about. */
@Composable
private fun Quote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = "“$text”",
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun BookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    highlights: List<HighlightEntity>,
    notes: List<NoteEntity>,
    onOpen: (page: Int) -> Unit,
    onOpenHighlight: (HighlightEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Title("Bookmarks")
        if (bookmarks.isEmpty() && highlights.isEmpty() && notes.isEmpty()) {
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
                if (notes.isNotEmpty()) item(key = "notes") { Title("Notes") }
                items(notes, key = { "note ${it.id}" }) { note ->
                    ListItem(
                        headlineContent = { Text(note.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                note.quote?.let { Quote(it) }
                                Text("Page ${note.page + 1}")
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpen(note.page) }.padding(horizontal = 8.dp),
                    )
                }
                if (highlights.isNotEmpty()) item(key = "highlights") { Title("Highlights") }
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
                        supportingContent = { Text("Page ${highlight.page + 1}") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpenHighlight(highlight) }.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
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
                Text(text, maxLines = 1)
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
