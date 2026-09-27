package com.shreyas.pdfreader.pdf

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Rendered pages, bounded by total bytes.
 *
 * Evicted bitmaps are not recycled. Compose can still draw a bitmap after eviction,
 * and drawing a recycled bitmap crashes. The garbage collector frees them.
 */
class PageBitmapCache(maxBytes: Int) {

    private val cache = object : LruCache<Long, Bitmap>(maxBytes) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(page: Int, widthPx: Int): Bitmap? = cache.get(key(page, widthPx))

    fun put(page: Int, widthPx: Int, bitmap: Bitmap) {
        cache.put(key(page, widthPx), bitmap)
    }

    fun clear() = cache.evictAll()

    private fun key(page: Int, widthPx: Int): Long = (page.toLong() shl 32) or widthPx.toLong()
}
