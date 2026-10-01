package com.example.ui.pdf

import android.graphics.Bitmap
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.rendering.RenderDestination
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Renders, reads and searches PDFs using Apache PDFBox.
 *
 * The framework's `android.graphics.pdf.PdfRenderer` can only draw a page. It
 * cannot extract a single character of text, so there is no search and no
 * copy, and it refuses a password-protected file outright. PDFBox does all
 * three and also draws scanned or oddly-sized pages more predictably.
 *
 * Neither [PDDocument] nor [PDFRenderer] is thread-safe and only one page may
 * be open at a time, so every entry point is serialised through [mutex].
 */
class PdfDocument(private val file: File) {

    private val mutex = Mutex()
    private var document: PDDocument? = null
    private var renderer: PDFRenderer? = null
    private var pageSizes: List<Pair<Int, Int>> = emptyList()

    val pageCount: Int get() = document?.numberOfPages ?: 0

    /** Thrown when the PDF needs a password we do not have. */
    class PasswordProtectedException : IOException("This PDF is password protected")

    private fun requireInit() {
        if (document != null) return
        if (!file.exists()) throw IOException("File not found")
        // Must run before any PDFBox call, and needs a Context for its bundled
        // fonts and resources. Set once from the reader.
        val ctx = appContext ?: throw IOException("PDF engine was not initialised")
        PDFBoxResourceLoader.init(ctx)
        try {
            val doc = PDDocument.load(file)
            document = doc
            renderer = PDFRenderer(doc).apply {
                // Skips images that are larger than the page and cannot be seen
                // anyway, which is most of the cost on a scanned paper.
                isSubsamplingAllowed = true
            }
            // Page sizes are needed for the pixel budget, and reading each one
            // separately is not free on a 40-page scan.
            pageSizes = (0 until doc.numberOfPages).map { i ->
                val box = doc.getPage(i).mediaBox
                box.width.toInt().coerceAtLeast(1) to box.height.toInt().coerceAtLeast(1)
            }
        } catch (e: InvalidPasswordException) {
            closeQuietly()
            throw PasswordProtectedException()
        } catch (e: IOException) {
            closeQuietly()
            throw IOException("This file could not be opened as a PDF", e)
        } catch (e: Exception) {
            closeQuietly()
            throw IOException("This file could not be opened as a PDF", e)
        }
    }

    suspend fun open() = withContext(Dispatchers.IO) {
        mutex.withLock { requireInit() }
    }

    /**
     * Height / width of every page, in order.
     *
     * The reader lays the document out as one continuous strip, so it needs the
     * full height up front to know where the document ends and to clamp panning
     * once the user zooms in.
     */
    suspend fun pageAspects(): List<Float> = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireInit()
            pageSizes.map { (w, h) -> h.toFloat() / w.toFloat() }
        }
    }

    /**
     * Renders one page to fit [targetWidthPx]. The returned bitmap belongs to
     * the caller, which should hand it to [PdfBitmapCache] rather than
     * recycling it.
     */
    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                requireInit()
                if (index !in 0 until pageCount) throw IOException("Page out of range")
                val r = renderer ?: throw IOException("Document is closed")
                val (naturalWidth, naturalHeight) = pageSizes.getOrElse(index) { 612 to 792 }

                val wanted = targetWidthPx.coerceAtLeast(MIN_WIDTH_PX)
                // PDFBox scales from the page's own point size, so the same
                // budget as before applies.
                val scale = min(wanted.toFloat() / naturalWidth, MAX_SCALE)
                val safeScale =
                    if (scale * scale * naturalWidth * naturalHeight > MAX_PIXELS) {
                        sqrt(MAX_PIXELS.toDouble() / (naturalWidth.toDouble() * naturalHeight)).toFloat()
                    } else {
                        scale
                    }
                try {
                    val bitmap = r.renderImage(
                        index,
                        safeScale,
                        // RGB rather than ARGB: a PDF page is opaque, and ARGB
                        // would cost a third more memory for an alpha channel
                        // that is always 255.
                        ImageType.RGB,
                        RenderDestination.VIEW
                    )
                    Log.d(TAG, "rendered page $index at ${bitmap.width}x${bitmap.height}")
                    bitmap
                } catch (e: Exception) {
                    Log.e(TAG, "renderPage($index) failed", e)
                    throw IOException("Page ${index + 1} could not be drawn", e)
                }
            }
        }

    /**
     * The plain text of a page, used for search and copying.
     *
     * A scanned paper has no text layer, so this is legitimately empty for
     * those; callers must handle that rather than showing nothing silently.
     */
    suspend fun textOfPage(index: Int): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireInit()
            val doc = document ?: throw IOException("Document is closed")
            if (index !in 0 until doc.numberOfPages) return@withLock ""
            runCatching {
                PDFTextStripper().apply {
                    startPage = index + 1
                    endPage = index + 1
                }.getText(doc)
            }.getOrElse {
                Log.w(TAG, "No text layer on page $index", it)
                ""
            }
        }
    }

    /** Pages (zero-based) where [query] appears, case-insensitively. */
    suspend fun search(query: String): List<Int> = withContext(Dispatchers.IO) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return@withContext emptyList()
        mutex.withLock {
            requireInit()
            val doc = document ?: return@withContext emptyList()
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            (0 until doc.numberOfPages).filter { page ->
                runCatching {
                    stripper.startPage = page + 1
                    stripper.endPage = page + 1
                    stripper.getText(doc).lowercase().contains(needle)
                }.getOrDefault(false)
            }
        }
    }

    /** True when the document has any extractable text at all. */
    suspend fun hasTextLayer(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireInit()
            val doc = document ?: return@withLock false
            runCatching {
                val stripper = PDFTextStripper()
                // One page is enough to tell a digital paper from a scan.
                stripper.startPage = 1
                stripper.endPage = 1
                stripper.getText(doc).isNotBlank()
            }.getOrDefault(false)
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock { closeQuietly() }
    }

    private fun closeQuietly() {
        // PDFRenderer has no close(); closing the document underneath it is
        // enough to release the page cache and file handle.
        try {
            document?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Document close failed", e)
        }
        renderer = null
        document = null
        pageSizes = emptyList()
    }

    companion object {
        private const val TAG = "PdfDocument"
        const val MIN_WIDTH_PX = 320
        const val MAX_SCALE = 4f
        const val MAX_PIXELS = 12_000_000L

        /**
         * PDFBox needs a Context to load its bundled fonts and resources before
         * any page can be drawn. Set once, from the UI thread, at startup.
         */
        @Volatile
        private var appContext: android.content.Context? = null

        fun init(context: android.content.Context) {
            if (appContext == null) appContext = context.applicationContext
        }
    }
}
