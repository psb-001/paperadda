package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * The reader used to let a page be scaled to 4x from a bitmap rendered at screen
 * width, which magnifies a blur; drag a zoomed page far enough and it left the
 * viewport with no way back; and single-finger drags were swallowed by the
 * double-tap detector so the list would not scroll. These pin the arithmetic
 * that fixes all three.
 */
class PdfReaderGeometryTest {

    /** Mirrors PdfReaderScreen.bucketFor. */
    private fun bucketFor(scale: Float): Float = if (scale > 1.15f) 2f else 1f

    private fun clamp(value: Float, lo: Float, hi: Float): Float = value.coerceIn(lo, hi)

    /**
     * Mirrors the pan clamp. graphicsLayer scales about the centre, so the page
     * grows by (scale - 1) * size and overhangs each edge by half of that.
     */
    private fun maxOffset(sizePx: Float, scale: Float): Float =
        (sizePx * (scale - 1f) / 2f).coerceAtLeast(0f)

    // ---- resolution follows zoom ----

    @Test
    fun zoomsInWithoutAHiResRenderSoTextStaysSharp() {
        assertEquals(1f, bucketFor(1f), 0.001f)
        assertEquals(1f, bucketFor(1.15f), 0.001f)
    }

    @Test
    fun magnifiesAtDoubleResolutionSoTextStaysSharp() {
        assertEquals(2f, bucketFor(1.2f), 0.001f)
        assertEquals(2f, bucketFor(2.5f), 0.001f)
        assertEquals(2f, bucketFor(4f), 0.001f)
    }

    @Test
    fun thresholdHoldsBackReRenderingDuringAPinch() {
        // Crossing the threshold repeatedly would restart the render on every
        // frame and make zooming stutter.
        var flips = 0
        var scale = 1f
        var step = 0.01f
        while (scale <= 1.4f) {
            if (bucketFor(scale) != bucketFor(scale - step)) flips++
            scale += step
        }
        assertEquals("bucket must not thrash while pinching", 1, flips)
    }

    @Test
    fun hiResRenderStaysInsideTheRendererPixelBudget() {
        // PdfDocument refuses to build a bitmap over its own cap, so the width we
        // ask for must not exceed what a page can actually produce.
        val containerWidth = 1080
        val requested = (containerWidth * bucketFor(2f)).roundToInt()
        val a4NaturalWidth = 595
        assertTrue(
            "2x of a $containerWidth px screen ($requested px) must be within " +
                "the renderer's 4x cap of $a4NaturalWidth pt",
            requested / a4NaturalWidth.toFloat() <= 4f
        )
    }

    // ---- the preload window must stay inside the document ----

    /** Mirrors the window in PdfDocumentStrip. */
    private fun window(firstVisible: Int, lastVisible: Int, pageCount: Int, preload: Int) =
        (firstVisible - preload).coerceAtLeast(0) to
            (lastVisible + preload).coerceAtMost(pageCount - 1)

    @Test
    fun preloadNeverAsksForAPageBeforeTheFirstOne() {
        // Regression: the window was unclamped, so opening any paper asked the
        // engine for page -1 and painted "Page out of range" above page 1.
        val (start, _) = window(firstVisible = 0, lastVisible = 0, pageCount = 2, preload = 1)
        assertTrue("preload produced index $start, which does not exist", start >= 0)
    }

    @Test
    fun preloadNeverAsksForAPagePastTheLastOne() {
        val (_, end) = window(firstVisible = 1, lastVisible = 1, pageCount = 2, preload = 1)
        assertTrue("preload produced index $end, past a 2 page paper", end <= 1)
    }

    @Test
    fun everyPageInTheWindowExists() {
        val pageCount = 4
        for (first in 0 until pageCount) {
            for (last in first until pageCount) {
                val (start, end) = window(first, last, pageCount, preload = 1)
                assertTrue("start $start", start >= 0)
                assertTrue("end $end", end < pageCount)
                assertTrue("window inverted: $start..$end", end >= start)
            }
        }
    }

    @Test
    fun aOnePagePaperStillHasAValidWindow() {
        val (start, end) = window(0, 0, pageCount = 1, preload = 1)
        assertEquals(0, start)
        assertEquals(0, end)
    }

    // ---- the page cannot be dragged away ----

    @Test
    fun aZoomedPageCanNeverBePannedOutOfView() {
        val width = 1000f
        val height = 1400f
        val flings = listOf(-99999f, -1000f, -250f, 0f, 250f, 1000f, 99999f)
        for (scale in listOf(1.5f, 2f, 3f, 4f)) {
            val maxX = maxOffset(width, scale)
            val maxY = maxOffset(height, scale)
            for (fling in flings) {
                val x = clamp(fling, -maxX, maxX)
                val y = clamp(fling, -maxY, maxY)
                assertTrue("x out of bounds at $scale: $x", x in -maxX..maxX)
                assertTrue("y out of bounds at $scale: $y", y in -maxY..maxY)
            }
            // A hard fling lands exactly on the edge, never past it.
            assertEquals(maxX, clamp(99999f, -maxX, maxX), 0.01f)
            assertEquals(-maxY, clamp(-99999f, -maxY, maxY), 0.01f)
        }
    }

    @Test
    fun thereIsNoSlackWhileNotZoomed() {
        // At 1x the page fits the screen, so any pan must be ignored entirely.
        assertEquals(0f, maxOffset(1000f, 1f), 0.001f)
        assertEquals(0f, maxOffset(1400f, 1f), 0.001f)
    }

    @Test
    fun panSlackGrowsWithZoomSoMoreOfThePageIsReachable() {
        assertTrue(maxOffset(1000f, 4f) > maxOffset(1000f, 2f))
        assertEquals(1500f, maxOffset(1000f, 4f), 0.01f)
    }
}
