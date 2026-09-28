package com.shreyas.pdfreader.ui.reader

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.positionInRoot
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import com.shreyas.pdfreader.data.Dictionary
import com.shreyas.pdfreader.data.Meaning
import com.shreyas.pdfreader.data.ReaderFont
import com.shreyas.pdfreader.data.ReaderSettings
import com.shreyas.pdfreader.data.db.HighlightEntity
import com.shreyas.pdfreader.data.db.NoteEntity
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

/** Definitions on the card. The full list is behind "More". */
private const val CARD_DEFINITIONS = 2

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

/** What the menu is about: selected text, or a highlight. */
private data class MenuTarget(val selected: TextSelection?, val picked: HighlightEntity?)

/** A word in the dictionary. [meanings] is null when the dictionary cannot be reached. */
private class Lookup(val word: String) {
    var loading by mutableStateOf(true)
    var meanings by mutableStateOf<List<Meaning>?>(null)
}

/**
 * The pages of a book as recognized text that flows to the width of the screen.
 * Page numbers work as in [PdfPages]. A page without text shows as the original page.
 *
 * With [highlighter] on, a sideways drag over text makes a highlight and a drag up or down moves the text.
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
    notes: List<NoteEntity>,
    chromeVisible: Boolean,
    highlighter: Boolean,
    jumps: Flow<Int>,
    loadText: suspend (page: Int) -> String?,
    loadPage: PageLoader,
    onPageChanged: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onAddHighlight: (page: Int, position: Int, text: String, color: HighlightColor) -> Unit,
    onUpdateHighlight: (HighlightEntity) -> Unit,
    onDeleteHighlight: (HighlightEntity) -> Unit,
    onNote: (NoteDraft) -> Unit,
    onHighlighterColor: (HighlightColor) -> Unit,
    onExitHighlighter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownPages by rememberUpdatedState(pages)
    val current by rememberUpdatedState(currentPage)
    val pageChanged by rememberUpdatedState(onPageChanged)
    val marking by rememberUpdatedState(highlighter)
    val markColor by rememberUpdatedState(settings.highlighter)
    val pagerState = rememberPagerState(initialPage = positionOf(pages, currentPage)) { shownPages.size }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current

    var selection by remember { mutableStateOf<TextSelection?>(null) }
    // True while the finger that selects is down. The menu waits, so it does not jump during the drag.
    var selecting by remember { mutableStateOf(false) }
    var pickedId by remember { mutableStateOf<Long?>(null) }
    val picked = highlights.firstOrNull { it.id == pickedId }
    var lookup by remember { mutableStateOf<Lookup?>(null) }
    var fullMeaning by remember { mutableStateOf(false) }
    // Bounds of the selection or the picked highlight, in the coordinates of the root.
    var anchor by remember { mutableStateOf<IntRect?>(null) }
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val notedIds = remember(notes) { notes.mapNotNull { it.highlightId }.toSet() }

    fun clear() {
        selection = null
        pickedId = null
        lookup = null
        fullMeaning = false
        anchor = null
    }

    LaunchedEffect(pagerState, pages) {
        pagerState.scrollToPage(positionOf(pages, current))
        snapshotFlow { pagerState.currentPage }.collect { position ->
            pages.getOrNull(position)?.let { pageChanged(it) }
        }
    }
    LaunchedEffect(pagerState.currentPage, highlighter) { clear() }
    // The selection of the highlighter stays until its highlight is on the page: no flash between the two.
    LaunchedEffect(highlights) { if (marking) selection = null }
    LaunchedEffect(jumps) { jumps.collect { pagerState.scrollToPage(positionOf(shownPages, it)) } }
    LaunchedEffect(lookup) {
        lookup?.let {
            it.meanings = Dictionary.define(it.word)
            it.loading = false
        }
    }
    BackHandler(enabled = highlighter, onBack = onExitHighlighter)

    val onTap by rememberUpdatedState { x: Float, width: Int ->
        val position = pagerState.currentPage
        when {
            // The card closes first. The selection stays for a second action.
            lookup != null -> lookup = null
            selection != null || pickedId != null -> clear()
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
        if (selection == null) {
            if (!marking) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            anchor = null
        }
        // The bars of the reader would cover the actions for the selection.
        if (chromeVisible) onToggleChrome()
        pickedId = null
        lookup = null
        selecting = true
        selection = selected
    }
    val onSelectEnd by rememberUpdatedState { finished: Boolean ->
        selecting = false
        val selected = selection
        if (marking && selected != null) {
            if (finished) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAddHighlight(selected.page, selected.start, selected.text, markColor)
            } else {
                selection = null
            }
        }
    }

    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot().round() }) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            // A sideways drag belongs to the highlighter. The edges of the screen still turn the page on a tap.
            userScrollEnabled = !highlighter,
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
                notedIds = notedIds,
                selection = selection?.takeIf { it.page == page },
                pickedId = pickedId,
                marking = highlighter,
                loadText = loadText,
                loadPage = loadPage,
                onTap = { x, width -> onTap(x, width) },
                onSelect = { onSelect(it) },
                onSelectEnd = { onSelectEnd(it) },
                onPick = {
                    clear()
                    pickedId = it.id
                },
                onAnchor = { anchor = it },
            )
        }

        val selected = selection
        val menu = MenuTarget(selected.takeIf { !selecting && !highlighter }, picked)
            .takeIf { (it.selected != null || it.picked != null) && lookup == null }
        val place = anchor?.translate(-origin)
        AnchoredPopIn(menu, place) { target ->
            MarkBar(
                selectedColor = target.picked?.let { HighlightColor.of(it.color) },
                note = target.picked?.let { mark -> notes.firstOrNull { it.highlightId == mark.id }?.text },
                onColor = { color ->
                    if (target.picked != null) {
                        onUpdateHighlight(target.picked.copy(color = color.name))
                    } else if (target.selected != null) {
                        onAddHighlight(target.selected.page, target.selected.start, target.selected.text, color)
                        clear()
                    }
                },
                onNote = {
                    val draft = if (target.picked != null) {
                        NoteDraft(target.picked.page, target.picked.position, target.picked.text, target.picked.id)
                    } else {
                        target.selected?.let { NoteDraft(it.page, it.start, it.text) }
                    }
                    clear()
                    draft?.let(onNote)
                },
                // One word only: the dictionary has no sentences.
                onMeaning = target.selected?.let { lookupWord(it.text) }
                    ?.takeIf { word -> word.isNotEmpty() && word.none { it.isWhitespace() } }
                    ?.let { word -> { lookup = Lookup(word) } },
                onCopy = target.selected?.let {
                    {
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Text", it.text))) }
                        clear()
                    }
                },
                onDelete = target.picked?.let {
                    {
                        onDeleteHighlight(it)
                        clear()
                    }
                },
            )
        }
        AnchoredPopIn(lookup.takeIf { !fullMeaning }, place) { found ->
            MeaningCard(found, onMore = { fullMeaning = true })
        }

        AnimatedVisibility(
            visible = highlighter,
            enter = popIn(),
            exit = popOut(),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
        ) {
            HighlighterPill(settings.highlighter, onHighlighterColor, onExitHighlighter)
        }
    }

    lookup?.takeIf { fullMeaning }?.let { found ->
        MeaningSheet(
            lookup = found,
            onDismiss = {
                lookup = null
                fullMeaning = false
            },
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
    /** Highlights that have a note. */
    notedIds: Set<Long>,
    selection: TextSelection?,
    pickedId: Long?,
    marking: Boolean,
    loadText: suspend (page: Int) -> String?,
    loadPage: PageLoader,
    onTap: (x: Float, width: Int) -> Unit,
    onSelect: (TextSelection) -> Unit,
    /** The finger left the screen. False when the gesture was cancelled. */
    onSelectEnd: (finished: Boolean) -> Unit,
    onPick: (HighlightEntity) -> Unit,
    /** Bounds of the selection or the picked highlight, in the coordinates of the root. */
    onAnchor: (IntRect) -> Unit,
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
    val range = remember(selection, pickedId, marks) {
        selection?.let { TextRange(it.start, it.end) }
            ?: marks.firstOrNull { it.first.id == pickedId }?.let { TextRange(it.second.first, it.second.second) }
    }

    var slot by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var slotSize by remember { mutableStateOf(IntSize.Zero) }
    var textBox by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val textOrigin = remember { mutableStateOf(Offset.Zero) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentMarks by rememberUpdatedState(marks)
    val currentMarking by rememberUpdatedState(marking)
    val tap by rememberUpdatedState(onTap)
    val pick by rememberUpdatedState(onPick)
    val select by rememberUpdatedState(onSelect)
    val selectEnd by rememberUpdatedState(onSelectEnd)

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
                // The highlighter draws in its own color: the text looks the same before and after the release.
                val selectionColor = if (marking) {
                    settings.highlighter.color.copy(alpha = MARK_ALPHA)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                }
                val shown = remember(pageText, marks, notedIds, selection, selectionColor) {
                    buildAnnotatedString {
                        append(pageText)
                        marks.forEach { (mark, place) ->
                            val style = SpanStyle(
                                background = HighlightColor.of(mark.color).color.copy(alpha = MARK_ALPHA),
                                // The line tells that the highlight has a note.
                                textDecoration = TextDecoration.Underline.takeIf { mark.id in notedIds },
                            )
                            addStyle(style, place.first, place.second)
                        }
                        if (selection != null && selection.end <= pageText.length) {
                            addStyle(SpanStyle(background = selectionColor), selection.start, selection.end)
                        }
                    }
                }
                // ponytail: the page text is laid out again on each frame of these animations, about 220 ms.
                // Change to a crossfade of the page if a long page stutters.
                val textSize by animateFloatAsState(settings.textSize, Motion.standard(), label = "textSize")
                val spacing by animateFloatAsState(settings.spacing.factor, Motion.standard(), label = "spacing")
                val margin by animateDpAsState(settings.margins.dp.dp, Motion.standard(), label = "margin")
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .safeDrawingPadding()
                        .padding(horizontal = margin, vertical = 24.dp),
                ) {
                    Text(
                        text = shown,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = if (settings.font == ReaderFont.SERIF) FontFamily.Serif else FontFamily.SansSerif,
                        fontSize = textSize.sp,
                        lineHeight = (textSize * spacing).sp,
                        textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start,
                        onTextLayout = { layout = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned {
                                textBox = it
                                textOrigin.value = it.positionInRoot()
                            }
                            .pointerInput(page, pageText) {
                                // Highlighter: a drag that starts sideways marks the words. A drag that starts
                                // up or down is not taken, so it moves the text.
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (!currentMarking) return@awaitEachGesture
                                    val first = layout?.wordAt(down.position) ?: return@awaitEachGesture
                                    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                                        change.consume()
                                    } ?: return@awaitEachGesture
                                    select(selectionOf(page, pageText, first, layout?.wordAt(start.position)))
                                    val finished = drag(start.id) { change ->
                                        layout?.wordAt(change.position)?.let { word ->
                                            select(selectionOf(page, pageText, first, word))
                                        }
                                        change.consume()
                                    }
                                    selectEnd(finished)
                                }
                            }
                            .pointerInput(page, pageText) {
                                // The word under the long press. A drag selects from this word to the word under the finger.
                                var anchor: TextRange? = null
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { position ->
                                        anchor = layout?.wordAt(position)
                                        anchor?.let { select(selectionOf(page, pageText, it, null)) }
                                    },
                                    onDragEnd = { if (anchor != null) selectEnd(true) },
                                    onDragCancel = { if (anchor != null) selectEnd(!currentMarking) },
                                    onDrag = { change, _ ->
                                        val first = anchor
                                        val word = layout?.wordAt(change.position)
                                        if (first != null && word != null) select(selectionOf(page, pageText, first, word))
                                    },
                                )
                            },
                    )
                }
                AnchorReport(range, layout, textOrigin::value, onAnchor)
            }
        }
    }
}

/** The text from the word [first] to the word [last], in both directions. */
private fun selectionOf(page: Int, pageText: String, first: TextRange, last: TextRange?): TextSelection {
    val start = min(first.start, last?.start ?: first.start)
    val end = max(first.end, last?.end ?: first.end)
    return TextSelection(page, start, end, pageText.substring(start, end))
}

/** Tells where [range] is on the screen, again when the text moves. Apart, so a scroll composes only this. */
@Composable
private fun AnchorReport(range: TextRange?, layout: TextLayoutResult?, origin: () -> Offset, onAnchor: (IntRect) -> Unit) {
    if (range == null || layout == null || range.end > layout.layoutInput.text.length) return
    val at = origin()
    LaunchedEffect(range, layout, at) {
        onAnchor(layout.getPathForRange(range.start, range.end).getBounds().translate(at).roundToIntRect())
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

@Composable
private fun ColorDot(color: HighlightColor, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Highlight ${color.name.lowercase()}" },
    ) {
        Box(Modifier.size(26.dp).background(color.color, CircleShape).border(2.dp, ring, CircleShape))
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
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 6.dp,
        modifier = modifier.widthIn(max = 340.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            // Two rows: one row is wider than a small phone.
            Row(verticalAlignment = Alignment.CenterVertically) {
                HighlightColor.entries.forEach { color -> ColorDot(color, color == selectedColor) { onColor(color) } }
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

/** Tells that the highlighter mode is on. Small, so the page stays the thing to look at. */
@Composable
private fun HighlighterPill(color: HighlightColor, onColor: (HighlightColor) -> Unit, onClose: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        shadowElevation = 6.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.animateContentSize(Motion.standard()).padding(horizontal = 4.dp),
        ) {
            if (choosing) {
                HighlightColor.entries.forEach { option ->
                    ColorDot(option, option == color) {
                        onColor(option)
                        choosing = false
                    }
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { choosing = true }
                        .semantics { contentDescription = "Highlighter is on. Color ${color.name.lowercase()}. Change color" },
                ) {
                    Box(Modifier.size(26.dp).background(color.color, CircleShape))
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Turn the highlighter off")
            }
        }
    }
}

/** The meaning of a word next to the word. Short: the full list is in [MeaningSheet]. */
@Composable
private fun MeaningCard(lookup: Lookup, onMore: () -> Unit) {
    val found = lookup.meanings
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            Text(lookup.word, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif)
            val first = found?.firstOrNull()
            when {
                lookup.loading -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(vertical = 12.dp).size(20.dp),
                )
                found == null -> CardMessage("No internet connection. The dictionary is online.")
                first == null -> CardMessage("No meaning found.")
                else -> {
                    if (first.partOfSpeech.isNotEmpty()) {
                        Text(
                            text = first.partOfSpeech.lowercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    first.definitions.take(CARD_DEFINITIONS).forEachIndexed { index, definition ->
                        Text(
                            text = if (first.definitions.size > 1) "${index + 1}. $definition" else definition,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (found != null && first != null && (found.size > 1 || first.definitions.size > CARD_DEFINITIONS)) {
                TextButton(onClick = onMore, modifier = Modifier.align(Alignment.End)) { Text("More") }
            } else {
                Box(Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun CardMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** All meanings of a word. Opens from "More" on the [MeaningCard], with the answer that the card has. */
@Composable
private fun MeaningSheet(lookup: Lookup, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(lookup.word, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
            lookup.meanings.orEmpty().forEach { meaning ->
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
            Text(
                text = "From Wiktionary",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
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
