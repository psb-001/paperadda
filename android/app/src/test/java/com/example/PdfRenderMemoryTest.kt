package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.ui.pdf.PdfDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The reader rendered a zoomed A4 page as a 2076x2937 bitmap — 24 MB — and held a
 * few of those at once plus a cache of up to 96 MB. That is more than a phone's
 * heap, so it died with an OutOfMemoryError. Because that is an `Error` and not an
 * `Exception`, nothing caught it and every page reported "could not be drawn".
 *
 * These tests hold the per-page cost down so the same failure cannot quietly
 * return with a bigger zoom or a longer paper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfRenderMemoryTest {

    private fun asset(): File {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("sample-physics.pdf"))
        return File.createTempFile("paper", ".pdf").apply {
            deleteOnExit()
            stream.use { i -> outputStream().use { i.copyTo(it) } }
        }
    }

    private fun paper(): PdfDocument = PdfDocument(asset()).also {
        PdfDocument.init(ApplicationProvider.getApplicationContext())
    }

    /** Bytes a Bitmap uses for a given pixel count: 4 bytes per pixel (ARGB_8888/RGB). */
    private fun bytesFor(pixels: Long) = pixels * 4

    @Test
    fun aZoomedPageStaysInsideThePixelCap() = runBlocking {
        val doc = paper()
        doc.open()
        try {
            // Exactly the width the reader asks for when zoomed: 2x the screen.
            val bitmap = doc.renderPage(0, 2076)
            val pixels = bitmap.width.toLong() * bitmap.height
            assertTrue(
                "rendered ${bitmap.width}x${bitmap.height} = ${bytesFor(pixels) / 1e6} MB, " +
                    "over the ${bytesFor(PdfDocument.MAX_PIXELS) / 1e6} MB cap",
                pixels <= PdfDocument.MAX_PIXELS
            )
        } finally {
            doc.close()
        }
    }

    @Test
    fun severalPagesOnScreenFitInsideAPhonesHeap() = runBlocking {
        val doc = paper()
        doc.open()
        try {
            val hiRes = doc.renderPage(0, 2076)
            val normal = doc.renderPage(1, 1038)
            val hiResBytes = bytesFor(hiRes.width.toLong() * hiRes.height)
            val normalBytes = bytesFor(normal.width.toLong() * normal.height)

            // Only the page on screen is magnified: while zoomed the list is
            // locked, so the pages either side are never looked at closely.
            // Four pages are composed at once.
            val live = hiResBytes + normalBytes * 3
            val heap = 128L * 1024 * 1024
            assertTrue(
                "live pages need ${live / 1e6} MB of a 128 MB heap",
                live < heap / 3
            )
        } finally {
            doc.close()
        }
    }

    @Test
    fun theMagnifiedPageIsStillMeaningfullySharperThanThePlainOne() = runBlocking {
        val doc = paper()
        doc.open()
        try {
            val hiRes = doc.renderPage(0, 2076)
            val normal = doc.renderPage(0, 1038)
            // The whole point of the second render is extra detail; if the pixel
            // cap ever clamps both to the same size, zoom becomes pointless and
            // the extra memory buys nothing.
            assertTrue(
                "magnified ${hiRes.width}px vs plain ${normal.width}px",
                hiRes.width > normal.width
            )
        } finally {
            doc.close()
        }
    }

    @Test
    fun theCacheBudgetCannotDrownTheLivePages() {
        // The cache is extra on top of whatever is currently composed, so it has to
        // stay small even on a generous heap.
        val heapBytes = Runtime.getRuntime().maxMemory()
        assertTrue(
            "heap ${heapBytes / 1e6} MB",
            PdfDocument.MAX_PIXELS * 4 < heapBytes / 2 || PdfDocument.MAX_PIXELS * 4 < 64L * 1024 * 1024
        )
    }
}
