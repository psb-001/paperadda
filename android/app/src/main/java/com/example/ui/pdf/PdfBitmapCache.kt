package com.example.ui.pdf

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache

/**
 * Keeps recently rendered pages in memory so scrolling back to a page is
 * instant.
 *
 * The reader used to render a page every time it entered composition and
 * recycle the bitmap on the way out. That meant scrolling back up showed a
 * spinner again, and — worse — recycling a bitmap the compositor was still
 * drawing caused hard crashes ("Canvas: trying to use a recycled bitmap").
 *
 * Nothing here recycles. Evicted bitmaps are left to the garbage collector;
 * explicitly recycling a bitmap that may still be referenced by a pending
 * draw is exactly the bug this replaces. Memory is bounded by [maxKb] instead,
 * sized from the heap so it cannot be the reason the app is killed.
 */
object PdfBitmapCache {

    private const val TAG = "PdfBitmapCache"

    private val maxKb: Int = run {
        val heapKb = Runtime.getRuntime().maxMemory() / 1024
        // An eighth of the heap, but never less than 24 MB (two hi-res pages)
        // and never more than 96 MB (we are one activity in a student app).
        (heapKb / 8).coerceIn(24L * 1024, 96L * 1024).toInt()
    }

    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            // Deliberately not recycling: see the class comment.
            if (evicted) Log.d(TAG, "evicted $key (${oldValue.byteCount / 1024} KB)")
        }
    }

    private fun key(pageIndex: Int, widthPx: Int) = "$pageIndex@$widthPx"

    /** An already-rendered page at this exact width, or null. */
    fun get(pageIndex: Int, widthPx: Int): Bitmap? = try {
        cache.get(key(pageIndex, widthPx))
    } catch (e: Exception) {
        null
    }

    fun put(pageIndex: Int, widthPx: Int, bitmap: Bitmap) {
        try {
            cache.put(key(pageIndex, widthPx), bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Could not cache page $pageIndex", e)
        }
    }

    fun clear() {
        try {
            cache.evictAll()
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear the page cache", e)
        }
    }
}
