package com.shreyas.pdfreader.util

/** Share of the document read, 0..100, when the reader is on zero-based [page]. */
fun progressPercent(page: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 0
    val pagesRead = (page + 1).coerceIn(0, pageCount)
    return (pagesRead * 100L / pageCount).toInt()
}
