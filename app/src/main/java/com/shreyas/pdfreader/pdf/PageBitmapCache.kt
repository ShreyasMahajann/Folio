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

    private data class Key(val page: Int, val widthPx: Int, val crop: PageCrop?)

    private val cache = object : LruCache<Key, Bitmap>(maxBytes) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(page: Int, widthPx: Int, crop: PageCrop?): Bitmap? = cache.get(Key(page, widthPx, crop))

    fun put(page: Int, widthPx: Int, crop: PageCrop?, bitmap: Bitmap) {
        cache.put(Key(page, widthPx, crop), bitmap)
    }

    fun clear() = cache.evictAll()
}
