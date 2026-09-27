package com.shreyas.pdfreader.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shreyas.pdfreader.data.FitMode
import com.shreyas.pdfreader.data.ReaderSettings
import com.shreyas.pdfreader.data.ReadingMode
import com.shreyas.pdfreader.pdf.PageEdits
import com.shreyas.pdfreader.pdf.positionOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

// ponytail: zoom re-renders the whole page, capped by these budgets, so very high zoom is slightly soft.
// Move to tile rendering (PdfRenderer.Page.render with a transform matrix) if sharper zoom is needed.
private const val PAGED_MAX_PIXELS = 8_000_000 // one zoomed page at a time, 32 MB
private const val SCROLL_MAX_PIXELS = 4_000_000 // several zoomed pages at a time, 16 MB each
private const val RENDER_DELAY_MS = 150L

/** Everything a page needs to draw itself. Shared by both reading modes. */
private class PageScope(
    val viewport: IntSize,
    val settings: ReaderSettings,
    val defaultAspect: Float,
    val loadPage: PageLoader,
    /** Zero-based page numbers of the file, in reading order. Deleted pages are not in the list. */
    val pages: List<Int>,
    val edits: PageEdits,
) {
    val aspects = mutableStateMapOf<Int, Float>()

    /** Shape of a page as shown, with its crop. Exact once the page was rendered. */
    fun aspect(page: Int): Float {
        aspects[page]?.let { return it }
        val crop = edits.cropFor(page) ?: return defaultAspect
        return defaultAspect * crop.width / crop.height
    }

    /** Page width on screen at zoom 1. */
    fun baseWidth(page: Int): Float = when (settings.fit) {
        FitMode.WIDTH -> viewport.width.toFloat()
        FitMode.PAGE -> min(viewport.width.toFloat(), viewport.height * aspect(page))
    }
}

/**
 * The pages of a book in the chosen reading mode.
 * [currentPage], [jumps] and [onPageChanged] use page numbers of the file, not positions in [pages].
 * The pages follow [currentPage] when [pages] changes. At other times the pages lead and report
 * through [onPageChanged].
 */
@Composable
fun PdfPages(
    pages: List<Int>,
    edits: PageEdits,
    currentPage: Int,
    defaultAspect: Float,
    settings: ReaderSettings,
    chromeVisible: Boolean,
    jumps: Flow<Int>,
    loadPage: PageLoader,
    onPageChanged: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val zoom = remember { ZoomState() }

    // Sharp bitmaps are rendered after the pinch settles, not on every frame of it.
    var renderScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(zoom) {
        snapshotFlow { zoom.scale }.collectLatest {
            delay(RENDER_DELAY_MS)
            renderScale = it
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                viewport = it
                zoom.viewport = Size(it.width.toFloat(), it.height.toFloat())
                zoom.reset()
            },
    ) {
        if (viewport.width > 0 && viewport.height > 0) {
            val scope = remember(viewport, settings, defaultAspect, loadPage, pages, edits) {
                PageScope(viewport, settings, defaultAspect, loadPage, pages, edits)
            }
            when (settings.mode) {
                ReadingMode.PAGED -> PagedPages(
                    scope, currentPage, zoom, renderScale, chromeVisible, jumps, onPageChanged, onToggleChrome,
                )
                ReadingMode.SCROLL -> ScrollPages(
                    scope, currentPage, zoom, renderScale, jumps, onPageChanged, onToggleChrome,
                )
            }
        }
    }
}

@Composable
private fun PagedPages(
    scope: PageScope,
    currentPage: Int,
    zoom: ZoomState,
    renderScale: Float,
    chromeVisible: Boolean,
    jumps: Flow<Int>,
    onPageChanged: (Int) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val pages by rememberUpdatedState(scope.pages)
    val current by rememberUpdatedState(currentPage)
    val pagerState = rememberPagerState(initialPage = positionOf(scope.pages, currentPage)) { pages.size }
    val coroutineScope = rememberCoroutineScope()
    val chromeShown by rememberUpdatedState(chromeVisible)
    val toggleChrome by rememberUpdatedState(onToggleChrome)
    val pageChanged by rememberUpdatedState(onPageChanged)

    LaunchedEffect(zoom) {
        zoom.panVertically = true
        zoom.onListScroll = {}
    }
    // Restarts when pages are deleted or restored: the same position then holds another page.
    LaunchedEffect(pagerState, scope.pages) {
        pagerState.scrollToPage(positionOf(scope.pages, current))
        snapshotFlow { pagerState.currentPage }.collect { position ->
            scope.pages.getOrNull(position)?.let { pageChanged(it) }
        }
    }
    LaunchedEffect(pagerState.currentPage) { zoom.reset() }
    LaunchedEffect(jumps) { jumps.collect { pagerState.scrollToPage(positionOf(pages, it)) } }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        key = { scope.pages.getOrElse(it) { -1 } },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(zoom) { detectReaderZoom(zoom, paged = true) }
            .pointerInput(zoom) {
                detectTapGestures(
                    onDoubleTap = { position -> coroutineScope.launch { zoom.toggle(position) } },
                    onTap = { position ->
                        val current = pagerState.currentPage
                        when {
                            chromeShown || zoom.isZoomed -> toggleChrome()
                            position.x < size.width * 0.25f && current > 0 ->
                                coroutineScope.launch { pagerState.animateScrollToPage(current - 1) }
                            position.x > size.width * 0.75f && current < pages.size - 1 ->
                                coroutineScope.launch { pagerState.animateScrollToPage(current + 1) }
                            else -> toggleChrome()
                        }
                    },
                )
            },
    ) { position ->
        val page = scope.pages.getOrNull(position) ?: return@HorizontalPager
        val isCurrent = position == pagerState.currentPage
        val width = (scope.baseWidth(page) * if (isCurrent) renderScale else 1f).roundToInt()
        val pageModifier = Modifier.aspectRatio(scope.aspect(page))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    if (position == pagerState.currentPage) {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    }
                },
        ) {
            val content = @Composable { modifier: Modifier ->
                PdfPage(
                    page = page,
                    widthPx = width,
                    maxPixels = PAGED_MAX_PIXELS,
                    theme = scope.settings.theme,
                    loadPage = scope.loadPage,
                    onAspectKnown = { scope.aspects[page] = it },
                    version = scope.edits.cropFor(page),
                    modifier = modifier,
                )
            }
            when (scope.settings.fit) {
                FitMode.PAGE -> content(pageModifier)
                // A page taller than the screen scrolls inside its own slot.
                FitMode.WIDTH -> Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) {
                    content(pageModifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ScrollPages(
    scope: PageScope,
    currentPage: Int,
    zoom: ZoomState,
    renderScale: Float,
    jumps: Flow<Int>,
    onPageChanged: (Int) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val pages by rememberUpdatedState(scope.pages)
    val current by rememberUpdatedState(currentPage)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = positionOf(scope.pages, currentPage))
    val coroutineScope = rememberCoroutineScope()
    val toggleChrome by rememberUpdatedState(onToggleChrome)
    val pageChanged by rememberUpdatedState(onPageChanged)
    val density = LocalDensity.current

    LaunchedEffect(zoom, listState) {
        zoom.reset()
        zoom.panVertically = false
        zoom.onListScroll = { listState.dispatchRawDelta(it) }
    }
    LaunchedEffect(listState, scope.pages) {
        // Scroll only when needed. A scroll puts the page at the top and loses the place inside it.
        if (scope.pages.getOrNull(listState.pageAtCentre()) != current) {
            listState.scrollToItem(positionOf(scope.pages, current))
        }
        snapshotFlow { listState.pageAtCentre() }.collect { position ->
            scope.pages.getOrNull(position)?.let { pageChanged(it) }
        }
    }
    LaunchedEffect(jumps) { jumps.collect { listState.scrollToItem(positionOf(pages, it)) } }

    // The list is scaled around its centre, so its top and bottom edges fall off screen when zoomed.
    // This padding lets the first and last page scroll back into view.
    val edgePadding = with(density) { (scope.viewport.height * (1 - 1 / zoom.scale) / 2).toDp() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = edgePadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(zoom) { detectReaderZoom(zoom, paged = false) }
            .pointerInput(zoom) {
                detectTapGestures(
                    onDoubleTap = { position -> coroutineScope.launch { zoom.toggle(position) } },
                    onTap = { toggleChrome() },
                )
            }
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
            },
    ) {
        items(count = scope.pages.size, key = { scope.pages[it] }) { position ->
            val page = scope.pages[position]
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PdfPage(
                    page = page,
                    widthPx = (scope.baseWidth(page) * renderScale).roundToInt(),
                    maxPixels = SCROLL_MAX_PIXELS,
                    theme = scope.settings.theme,
                    loadPage = scope.loadPage,
                    onAspectKnown = { scope.aspects[page] = it },
                    version = scope.edits.cropFor(page),
                    // Whole-page fit narrows the page until its height fits the screen.
                    modifier = Modifier
                        .width(with(density) { scope.baseWidth(page).toDp() })
                        .aspectRatio(scope.aspect(page)),
                )
            }
        }
    }
}

/** The page under the middle of the screen. The last page counts once the list cannot scroll further. */
private fun LazyListState.pageAtCentre(): Int {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return firstVisibleItemIndex
    if (!canScrollForward) return visible.last().index
    val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return (visible.lastOrNull { it.offset <= centre } ?: visible.first()).index
}
