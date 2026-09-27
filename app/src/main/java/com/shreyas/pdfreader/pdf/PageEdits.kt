package com.shreyas.pdfreader.pdf

/** Page number that stands for "all pages" in a book-wide crop. */
const val ALL_PAGES = -1

/** Smallest crop box, as a fraction of the page side. */
const val MIN_CROP_SIZE = 0.1f

/** The visible part of a page. Values are fractions 0..1 of the page width and height. */
data class PageCrop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val isFull get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f

    companion object {
        val FULL = PageCrop(0f, 0f, 1f, 1f)
    }
}

enum class CropCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Moves one corner by a fraction of the page. The box stays on the page and above the minimum size. */
fun PageCrop.dragCorner(corner: CropCorner, dx: Float, dy: Float): PageCrop {
    val movesLeft = corner == CropCorner.TOP_LEFT || corner == CropCorner.BOTTOM_LEFT
    val movesTop = corner == CropCorner.TOP_LEFT || corner == CropCorner.TOP_RIGHT
    return PageCrop(
        left = if (movesLeft) (left + dx).coerceIn(0f, right - MIN_CROP_SIZE) else left,
        top = if (movesTop) (top + dy).coerceIn(0f, bottom - MIN_CROP_SIZE) else top,
        right = if (movesLeft) right else (right + dx).coerceIn(left + MIN_CROP_SIZE, 1f),
        bottom = if (movesTop) bottom else (bottom + dy).coerceIn(top + MIN_CROP_SIZE, 1f),
    )
}

/** Moves the whole box by a fraction of the page. The size stays, the box stays on the page. */
fun PageCrop.moveBy(dx: Float, dy: Float): PageCrop {
    val x = dx.coerceIn(-left, 1f - right)
    val y = dy.coerceIn(-top, 1f - bottom)
    return PageCrop(left + x, top + y, right + x, bottom + y)
}

/**
 * What the reader changed about a book. The PDF file itself is never changed.
 * Page numbers are the zero-based page numbers of the file.
 */
data class PageEdits(
    val hidden: Set<Int> = emptySet(),
    /** Key [ALL_PAGES] holds the book-wide crop. */
    val crops: Map<Int, PageCrop> = emptyMap(),
) {
    val bookCrop: PageCrop? get() = crops[ALL_PAGES]

    /** The crop of [page]. A crop of the page itself wins over the book-wide crop. */
    fun cropFor(page: Int): PageCrop? = (crops[page] ?: bookCrop)?.takeUnless { it.isFull }

    /** File page numbers the reader shows, in order. Never empty for a file with pages. */
    fun visiblePages(pageCount: Int): List<Int> {
        val all = 0 until pageCount
        return all.filter { it !in hidden }.ifEmpty { all.toList() }
    }
}

/**
 * Position of [filePage] in the list of visible [pages].
 * A hidden page maps to the next visible page, or to the last page when none follows.
 */
fun positionOf(pages: List<Int>, filePage: Int): Int {
    val index = pages.indexOfFirst { it >= filePage }
    return if (index >= 0) index else (pages.size - 1).coerceAtLeast(0)
}
