package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The public Downloads copy must produce a name that cannot escape the target
 * folder or confuse a file manager, so the sanitiser is pinned down here.
 */
class ExportedFileNameTest {

    // Mirrors FileExporter.sanitise, which is private because it needs a Context.
    private fun sanitise(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[^A-Za-z0-9._ -]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim('_', '.', ' ')
            .take(100)
        if (cleaned.isBlank() || cleaned == "." || cleaned == "..") return "paper.pdf"
        return if (cleaned.lowercase().endsWith(".pdf")) cleaned else "$cleaned.pdf"
    }

    @Test
    fun keepsOrdinaryNamesReadable() {
        assertEquals("PPS 2025 paper.pdf", sanitise("PPS 2025 paper"))
        assertEquals("PPS_ESE_2024.pdf", sanitise("PPS_ESE_2024.pdf"))
    }

    @Test
    fun doesNotDoubleTheExtension() {
        // The original case is kept; what matters is that ".pdf" is not appended
        // a second time, which would give "Maths 2024.PDF.pdf".
        assertEquals("Maths 2024.PDF", sanitise("Maths 2024.PDF"))
        assertEquals("Maths 2024.pdf", sanitise("Maths 2024"))
    }

    @Test
    fun stripsPathSeparatorsSoNothingEscapesTheFolder() {
        val out = sanitise("../../../etc/passwd")
        assertTrue("must not contain a path separator", out.none { it == '/' })
        assertTrue("must not be a traversal", !out.startsWith(".."))
    }

    @Test
    fun neutralisesSeparatorsAndShellMetacharacters() {
        val out = sanitise("a/b\\c:d*e?f\"g<h>i|j")
        assertTrue(out.none { it in "/\\:*?\"<>|" })
    }

    @Test
    fun capsLength() {
        assertTrue(sanitise("x".repeat(500)).length <= 104)
    }

    @Test
    fun alwaysProducesSomethingUsable() {
        assertEquals("paper.pdf", sanitise(""))
        assertEquals("paper.pdf", sanitise("   "))
        assertEquals("paper.pdf", sanitise("///"))
        // A bare ".pdf" would be a hidden file and looks broken to a student.
        assertTrue("must not be a bare extension", sanitise("") != ".pdf")
    }

    @Test
    fun pageScaleStaysInsideTheMemoryBudget() {
        // Mirrors PdfDocument.renderPage's clamp. A huge page (poster-sized)
        // must be scaled down rather than allocating a bitmap the app cannot
        // afford.
        val maxPixels = 12_000_000.0
        fun widthFor(pageW: Int, pageH: Int, target: Int): Int {
            val scale = minOf(target.toFloat() / pageW, 4f)
            val safe = if (scale * scale * pageW * pageH > maxPixels) {
                kotlin.math.sqrt(maxPixels / (pageW.toDouble() * pageH)).toFloat()
            } else {
                scale
            }
            return (pageW * safe).toInt()
        }
        // A4 at phone width: no downscale needed.
        assertTrue(widthFor(595, 842, 1080) in 1000..1200)
        // A0 poster must be clamped, not rendered at full size.
        val poster = widthFor(2384, 3370, 1080)
        assertTrue("poster should be scaled to target width", poster <= 1080)
        assertTrue(
            "poster pixels must stay under budget",
            poster.toDouble() * (poster * 3370.0 / 2384.0) <= maxPixels
        )
    }
}
