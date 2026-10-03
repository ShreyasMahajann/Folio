package com.shreyas.pdfreader.lite

import kotlin.math.max
import kotlin.math.min

/** Where a tap on the edge of the screen moves the reader inside one page. */
object Paging {
    /** The marker for "this page has no more to show, turn the page". */
    const val TURN = -1

    // One step keeps a tenth of the screen, so the reader finds the last line again.
    private fun step(viewHeight: Int) = viewHeight * 9 / 10

    /** The new scroll position after a step down, or [TURN] at the end of the page. */
    fun forward(scrollY: Int, viewHeight: Int, pageHeight: Int): Int {
        val last = pageHeight - viewHeight
        return if (scrollY >= last) TURN else min(scrollY + step(viewHeight), last)
    }

    /** The new scroll position after a step up, or [TURN] at the start of the page. */
    fun back(scrollY: Int, viewHeight: Int): Int =
        if (scrollY <= 0) TURN else max(scrollY - step(viewHeight), 0)
}
