package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.ui.pdf.PdfDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The PDF engine swapped to Apache PDFBox so the reader can search and copy
 * text, which the framework PdfRenderer cannot do at all.
 *
 * These run against two real question papers rather than a synthetic file,
 * because the whole point of the change is that real papers have a usable text
 * layer. PDFBox is pure Java, so this needs no device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfEngineTest {

    private fun asset(name: String): File {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "missing test asset $name"
        }
        val out = File.createTempFile("paper", ".pdf")
        out.deleteOnExit()
        stream.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    private fun physics() = asset("sample-physics.pdf")
    private fun bcmE() = asset("sample-paper.pdf")


    @Before
    fun initEngine() {
        // The app does this in MainActivity.onCreate; without it every entry
        // point refuses, which is what the next tests confirm.
        PdfDocument.init(ApplicationProvider.getApplicationContext())
    }

    /** A real word from the paper, so the search test is not self-referential. */
    private fun firstLongWord(text: String): String {
        val words: List<String> = text.split(Regex("\\s+"))
        for (raw in words) {
            val cleaned: String = raw.trim('.', ',', ';', ':', '(', ')', '[', ']')
            if (cleaned.length > 5 && cleaned.all { ch: Char -> ch.isLetter() }) {
                return cleaned
            }
        }
        throw AssertionError("no searchable word found in: ${text.take(200)}")
    }

    @Test
    fun opensARealQuestionPaper() = runBlocking {
        val doc = PdfDocument(physics())
        doc.open()
        val pages = doc.pageCount
        doc.close()
        assertTrue("a question paper must have pages, had $pages", pages > 0)
    }

    @Test
    fun extractsRealTextFromAPage() = runBlocking {
        val doc = PdfDocument(physics())
        doc.open()
        val text = doc.textOfPage(0)
        doc.close()
        assertTrue("a digital paper must yield extractable text", text.isNotBlank())
        // Real words from this actual paper, not just "some characters".
        assertTrue(
            "expected real question text, got: ${text.take(200)}",
            text.contains("Engineering", ignoreCase = true) ||
                text.contains("Marks", ignoreCase = true) ||
                text.contains("Question", ignoreCase = true)
        )
    }

    @Test
    fun findsAWordAcrossThePaper() = runBlocking {
        val doc = PdfDocument(physics())
        doc.open()
        val text = doc.textOfPage(0)
        // Pick a word that is genuinely on page 1 and search for it.
        val word: String = firstLongWord(text)
        val hits: List<Int> = doc.search(word)
        doc.close()
        assertTrue("searching for '$word' must find page 1, found $hits", hits.contains(0))
    }

    @Test
    fun searchIsCaseInsensitive() = runBlocking {
        val doc = PdfDocument(physics())
        doc.open()
        val text = doc.textOfPage(0)
        val word: String = firstLongWord(text)
        val lower: List<Int> = doc.search(word.lowercase())
        val upper: List<Int> = doc.search(word.uppercase())
        doc.close()
        assertEquals("case must not change the result", lower, upper)
        assertTrue(lower.contains(0))
    }

    @Test
    fun reportsNoHitsForAWordThatIsNotThere() = runBlocking {
        val doc = PdfDocument(physics())
        doc.open()
        val hits = doc.search("zzzznotawordzzzz")
        doc.close()
        assertTrue("a miss must return nothing, not page 1", hits.isEmpty())
    }

    @Test
    fun blankQueryReturnsNothingWithoutTouchingTheDocument() = runBlocking {
        val doc = PdfDocument(physics())
        assertEquals(emptyList<Int>(), doc.search("   "))
    }

    @Test
    fun bothPapersHaveAUsableTextLayer() = runBlocking {
        // Guards the search feature: a scanned paper has no text, and the UI
        // has to say so rather than silently finding nothing.
        for (f in listOf(physics(), bcmE())) {
            val doc = PdfDocument(f)
            doc.open()
            val hasText = doc.hasTextLayer()
            doc.close()
            assertTrue("test asset ${f.name} should have a text layer", hasText)
        }
    }

    @Test
    fun aMissingFileFailsWithAClearMessage() = runBlocking {
        val doc = PdfDocument(File("/definitely/not/here.pdf"))
        val error = runCatching { doc.open() }.exceptionOrNull()
        assertNotNull("opening a missing file must fail", error)
        assertTrue(
            "message should say the file is not found, was: ${error?.message}",
            error!!.message?.contains("not found", ignoreCase = true) == true
        )
    }

    @Test
    fun aCorruptFileIsRejectedRatherThanCrashing() = runBlocking {
        val junk = File.createTempFile("junk", ".pdf").apply {
            deleteOnExit()
            writeText("this is definitely not a pdf")
        }
        val doc = PdfDocument(junk)
        val error = runCatching { doc.open() }.exceptionOrNull()
        assertNotNull("a corrupt file must be reported, not crash the app", error)
    }

    @Test
    fun renderingRespectsTheRequestedWidth() = runBlocking {
        val doc = PdfDocument(physics())
        doc.open()
        val bitmap = doc.renderPage(0, 1000)
        val w = bitmap.width
        val h = bitmap.height
        doc.close()
        // A4 is portrait, so it must stay taller than wide.
        assertTrue("expected a portrait page, got ${w}x$h", h > w)
        assertTrue("rendered width ${w} should be near the requested 1000", w in 700..1200)
    }

    @Test
    fun pageOutOfRangeIsRejected() = runBlocking {
        val doc = PdfDocument(physics())
        doc.open()
        val error = runCatching { doc.renderPage(9999, 800) }.exceptionOrNull()
        doc.close()
        assertNotNull("rendering a page that does not exist must fail", error)
    }
}
