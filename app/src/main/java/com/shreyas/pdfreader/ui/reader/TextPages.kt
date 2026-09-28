package com.shreyas.pdfreader.ui.reader

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreyas.pdfreader.data.Dictionary
import com.shreyas.pdfreader.data.Meaning
import com.shreyas.pdfreader.data.ReaderSettings
import com.shreyas.pdfreader.data.db.HighlightEntity
import com.shreyas.pdfreader.data.lookupWord
import com.shreyas.pdfreader.pdf.HighlightColor
import com.shreyas.pdfreader.pdf.OcrScript
import com.shreyas.pdfreader.pdf.PageEdits
import com.shreyas.pdfreader.pdf.findHighlight
import com.shreyas.pdfreader.pdf.positionOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private const val ORIGINAL_MAX_PIXELS = 8_000_000
private const val MARK_ALPHA = 0.45f
private const val LINE_HEIGHT = 1.5f

/** Shown at full strength in lists, and with [MARK_ALPHA] behind text so the text stays readable in every theme. */
val HighlightColor.color: Color
    get() = when (this) {
        HighlightColor.YELLOW -> Color(0xFFFFD54F)
        HighlightColor.GREEN -> Color(0xFF81C784)
        HighlightColor.BLUE -> Color(0xFF64B5F6)
        HighlightColor.PINK -> Color(0xFFF06292)
    }

/** Selected text of [page]: characters [start] until [end] of the page text. */
private data class TextSelection(val page: Int, val start: Int, val end: Int, val text: String)

/** A note in the editor. It belongs to [highlight], or makes a new highlight of [selection]. */
private class NoteDraft(val highlight: HighlightEntity?, val selection: TextSelection?)

/**
 * The pages of a book as recognized text that flows to the width of the screen.
 * Page numbers work as in [PdfPages]. A page without text shows as the original page.
 */
// ponytail: text mode always turns pages, also when the reading mode is Scroll.
// Add a LazyColumn of page texts if continuous scroll is wanted in text mode.
@Composable
fun TextPages(
    pages: List<Int>,
    edits: PageEdits,
    currentPage: Int,
    defaultAspect: Float,
    settings: ReaderSettings,
    script: OcrScript,
    highlights: List<HighlightEntity>,
    chromeVisible: Boolean,
    jumps: Flow<Int>,
    loadText: suspend (page: Int) -> String?,
    loadPage: PageLoader,
    onPageChanged: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onAddHighlight: (page: Int, position: Int, text: String, color: HighlightColor, note: String?) -> Unit,
    onUpdateHighlight: (HighlightEntity) -> Unit,
    onDeleteHighlight: (HighlightEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownPages by rememberUpdatedState(pages)
    val current by rememberUpdatedState(currentPage)
    val pageChanged by rememberUpdatedState(onPageChanged)
    val pagerState = rememberPagerState(initialPage = positionOf(pages, currentPage)) { shownPages.size }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current

    var selection by remember { mutableStateOf<TextSelection?>(null) }
    var pickedId by remember { mutableStateOf<Long?>(null) }
    val picked = highlights.firstOrNull { it.id == pickedId }
    var lookup by remember { mutableStateOf<String?>(null) }
    var noteDraft by remember { mutableStateOf<NoteDraft?>(null) }

    LaunchedEffect(pagerState, pages) {
        pagerState.scrollToPage(positionOf(pages, current))
        snapshotFlow { pagerState.currentPage }.collect { position ->
            pages.getOrNull(position)?.let { pageChanged(it) }
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        selection = null
        pickedId = null
    }
    LaunchedEffect(jumps) { jumps.collect { pagerState.scrollToPage(positionOf(shownPages, it)) } }

    val onTap by rememberUpdatedState { x: Float, width: Int ->
        val position = pagerState.currentPage
        when {
            selection != null || pickedId != null -> {
                selection = null
                pickedId = null
            }
            chromeVisible -> onToggleChrome()
            x < width * 0.25f && position > 0 -> {
                scope.launch { pagerState.animateScrollToPage(position - 1) }
                Unit
            }
            x > width * 0.75f && position < pages.size - 1 -> {
                scope.launch { pagerState.animateScrollToPage(position + 1) }
                Unit
            }
            else -> onToggleChrome()
        }
    }
    val onSelect by rememberUpdatedState { selected: TextSelection ->
        if (selection == null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        // The bars of the reader would cover the actions for the selection.
        if (chromeVisible) onToggleChrome()
        pickedId = null
        selection = selected
    }

    Box(modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { pages.getOrElse(it) { -1 } },
            modifier = Modifier.fillMaxSize(),
        ) { position ->
            val page = pages.getOrNull(position) ?: return@HorizontalPager
            val crop = edits.cropFor(page)
            TextPage(
                page = page,
                script = script,
                version = crop,
                aspect = if (crop == null) defaultAspect else defaultAspect * crop.width / crop.height,
                settings = settings,
                highlights = remember(highlights, page) { highlights.filter { it.page == page } },
                selection = selection?.takeIf { it.page == page },
                loadText = loadText,
                loadPage = loadPage,
                onTap = { x, width -> onTap(x, width) },
                onSelect = { onSelect(it) },
                onPick = {
                    selection = null
                    pickedId = it.id
                },
            )
        }

        val selected = selection
        if (selected != null || picked != null) {
            MarkBar(
                selectedColor = picked?.let { HighlightColor.of(it.color) },
                note = picked?.note,
                onColor = { color ->
                    if (picked != null) {
                        onUpdateHighlight(picked.copy(color = color.name))
                    } else if (selected != null) {
                        onAddHighlight(selected.page, selected.start, selected.text, color, null)
                        selection = null
                    }
                },
                onNote = { noteDraft = NoteDraft(picked, selected) },
                // One word only: the dictionary has no sentences.
                onMeaning = selected?.let { lookupWord(it.text) }
                    ?.takeIf { word -> word.isNotEmpty() && word.none { it.isWhitespace() } }
                    ?.let { word -> { lookup = word } },
                onCopy = selected?.let {
                    {
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Text", it.text))) }
                        selection = null
                    }
                },
                onDelete = picked?.let {
                    {
                        onDeleteHighlight(it)
                        pickedId = null
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    lookup?.let { word -> MeaningSheet(word, onDismiss = { lookup = null }) }
    noteDraft?.let { draft ->
        NoteDialog(
            initial = draft.highlight?.note.orEmpty(),
            onSave = { note ->
                val highlight = draft.highlight
                val marked = draft.selection
                if (highlight != null) {
                    onUpdateHighlight(highlight.copy(note = note))
                } else if (marked != null) {
                    onAddHighlight(marked.page, marked.start, marked.text, HighlightColor.YELLOW, note)
                    selection = null
                }
                noteDraft = null
            },
            onDismiss = { noteDraft = null },
        )
    }
}

@Composable
private fun TextPage(
    page: Int,
    script: OcrScript,
    /** Changes when the page must be recognized again. */
    version: Any?,
    aspect: Float,
    settings: ReaderSettings,
    highlights: List<HighlightEntity>,
    selection: TextSelection?,
    loadText: suspend (page: Int) -> String?,
    loadPage: PageLoader,
    onTap: (x: Float, width: Int) -> Unit,
    onSelect: (TextSelection) -> Unit,
    onPick: (HighlightEntity) -> Unit,
) {
    var text by remember(page, script, version) { mutableStateOf<String?>(null) }
    var loaded by remember(page, script, version) { mutableStateOf(false) }
    LaunchedEffect(page, script, version) {
        text = loadText(page)
        loaded = true
    }

    val pageText = text.orEmpty()
    // Highlights with the place where they are in the text now.
    val marks = remember(pageText, highlights) {
        highlights.mapNotNull { mark -> findHighlight(pageText, mark.text, mark.position)?.let { mark to it } }
    }

    var slot by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var slotSize by remember { mutableStateOf(IntSize.Zero) }
    var textBox by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentMarks by rememberUpdatedState(marks)
    val tap by rememberUpdatedState(onTap)
    val pick by rememberUpdatedState(onPick)
    val select by rememberUpdatedState(onSelect)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { slotSize = it }
            .onGloballyPositioned { slot = it }
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    val from = slot
                    val box = textBox?.takeIf { it.isAttached }
                    val character = if (from != null && box != null) {
                        layout?.characterAt(box.localPositionOf(from, position))
                    } else {
                        null
                    }
                    val mark = character?.let { index ->
                        currentMarks.firstOrNull { (_, place) -> index >= place.first && index < place.second }
                    }
                    if (mark != null) pick(mark.first) else tap(position.x, size.width)
                }
            },
    ) {
        when {
            !loaded -> CircularProgressIndicator()
            pageText.isBlank() -> OriginalPage(page, slotSize.width, aspect, version, settings, loadPage)
            else -> {
                val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                val shown = remember(pageText, marks, selection, selectionColor) {
                    buildAnnotatedString {
                        append(pageText)
                        marks.forEach { (mark, place) ->
                            val style = SpanStyle(
                                background = HighlightColor.of(mark.color).color.copy(alpha = MARK_ALPHA),
                                // The line tells that the highlight has a note.
                                textDecoration = TextDecoration.Underline.takeIf { mark.note != null },
                            )
                            addStyle(style, place.first, place.second)
                        }
                        if (selection != null && selection.end <= pageText.length) {
                            addStyle(SpanStyle(background = selectionColor), selection.start, selection.end)
                        }
                    }
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .safeDrawingPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    Text(
                        text = shown,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Serif,
                        fontSize = settings.textSize.sp,
                        lineHeight = (settings.textSize * LINE_HEIGHT).sp,
                        onTextLayout = { layout = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { textBox = it }
                            .pointerInput(page, pageText) {
                                fun selectWords(words: TextRange) {
                                    val picked = pageText.substring(words.start, words.end)
                                    select(TextSelection(page, words.start, words.end, picked))
                                }
                                // The word under the long press. A drag selects from this word to the word under the finger.
                                var anchor: TextRange? = null
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { position ->
                                        anchor = layout?.wordAt(position)?.also(::selectWords)
                                    },
                                    onDrag = { change, _ ->
                                        val first = anchor
                                        val word = layout?.wordAt(change.position)
                                        if (first != null && word != null) {
                                            selectWords(TextRange(min(first.start, word.start), max(first.end, word.end)))
                                        }
                                    },
                                )
                            },
                    )
                }
            }
        }
    }
}

/** The page of the PDF, for a page where no text was found: a picture, an empty page, or a failed recognition. */
@Composable
private fun OriginalPage(
    page: Int,
    widthPx: Int,
    aspect: Float,
    version: Any?,
    settings: ReaderSettings,
    loadPage: PageLoader,
) {
    var shape by remember(page, version) { mutableFloatStateOf(aspect) }
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        PdfPage(
            page = page,
            widthPx = widthPx,
            maxPixels = ORIGINAL_MAX_PIXELS,
            theme = settings.theme,
            loadPage = loadPage,
            onAspectKnown = { shape = it },
            version = version,
            modifier = Modifier.fillMaxWidth().aspectRatio(shape),
        )
    }
}

/** Actions for selected text, or for a highlight when [selectedColor] is not null. A null action is not shown. */
@Composable
private fun MarkBar(
    selectedColor: HighlightColor?,
    note: String?,
    onColor: (HighlightColor) -> Unit,
    onNote: () -> Unit,
    onMeaning: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
        modifier = modifier.navigationBarsPadding().padding(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            // Two rows: one row is wider than a small phone.
            Row(verticalAlignment = Alignment.CenterVertically) {
                HighlightColor.entries.forEach { color ->
                    val ring = if (color == selectedColor) MaterialTheme.colorScheme.onSurface else Color.Transparent
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { onColor(color) }
                            .semantics { contentDescription = "Highlight ${color.name.lowercase()}" },
                    ) {
                        Box(Modifier.size(26.dp).background(color.color, CircleShape).border(2.dp, ring, CircleShape))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNote) { Text("Note") }
                if (onMeaning != null) TextButton(onClick = onMeaning) { Text("Meaning") }
                if (onCopy != null) TextButton(onClick = onCopy) { Text("Copy") }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun NoteDialog(initial: String, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note") },
        text = { OutlinedTextField(value = note, onValueChange = { note = it }, minLines = 3, maxLines = 8) },
        confirmButton = { TextButton(onClick = { onSave(note.trim().ifEmpty { null }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MeaningSheet(word: String, onDismiss: () -> Unit) {
    var loading by remember(word) { mutableStateOf(true) }
    // Null when the dictionary cannot be reached.
    var meanings by remember(word) { mutableStateOf<List<Meaning>?>(null) }
    LaunchedEffect(word) {
        meanings = Dictionary.define(word)
        loading = false
    }
    val found = meanings

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(word, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
            when {
                loading -> CircularProgressIndicator(Modifier.padding(top = 16.dp).size(24.dp))
                found == null -> Message("No internet connection. The dictionary is online.")
                found.isEmpty() -> Message("No meaning found.")
                else -> found.forEach { meaning ->
                    Text(
                        text = listOf(meaning.language, meaning.partOfSpeech).filter { it.isNotEmpty() }.joinToString(", "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    meaning.definitions.forEachIndexed { index, definition ->
                        Text("${index + 1}. $definition", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Text(
                text = "From Wiktionary",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
}

/** The character under [position], or null when the position is not on a character. */
private fun TextLayoutResult.characterAt(position: Offset): Int? {
    // The nearest caret place is before or after the character under the position.
    val caret = getOffsetForPosition(position)
    return listOf(caret, caret - 1).firstOrNull {
        it >= 0 && it < layoutInput.text.length && getBoundingBox(it).contains(position)
    }
}

/** The word under [position], or null when the position is on a space or outside the text. */
private fun TextLayoutResult.wordAt(position: Offset): TextRange? {
    val word = getWordBoundary(characterAt(position) ?: return null)
    return word.takeIf { !it.collapsed && layoutInput.text.substring(it.start, it.end).isNotBlank() }
}
