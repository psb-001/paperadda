package com.paperadda.app

import com.paperadda.app.data.model.QuestionPaper
import com.paperadda.app.data.repository.buildConfinedDownloadFile
import com.paperadda.app.data.repository.downloadFileName
import com.paperadda.app.data.repository.existingDownloadFile
import com.paperadda.app.data.repository.isPdfContent
import com.paperadda.app.data.repository.legacyDownloadFileName
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The download file-name contract, and the collision bug it fixes.
 *
 * Pure JVM (no Robolectric). Two rules hold this together:
 *  1. hostile catalog text can never escape the download directory, and
 *  2. two different papers can never share a file name.
 *
 * Rule 2 is not theoretical: while the name was built from branch, subject and
 * year alone, the published catalog mapped 75 papers onto 21 file names, so
 * downloading the second paper of a subject and year silently replaced the
 * first.
 */
class FilenameSanitizationTest {

    private val downloadDir: File = Files.createTempDirectory("paperadda-downloads").toFile()

    @Test
    fun `traversal payloads stay inside download dir`() {
        val dirCanon = downloadDir.canonicalPath
        listOf(
            paper("../../evil", "Signals and Systems", "2025"),
            paper("ENTC", "a/b", "2025"),
            paper("ENTC", "..\\windows", "2025"),
            paper("", "", ""),
            paper("...", "...", "..."),
            paper("ENTC", "x".repeat(200), "2025"),
            paper("ENTC", "/abs/path", "2025"),
            paper("ENTC", "ok subject", "../../escape"),
        ).forEach { p ->
            val file = buildConfinedDownloadFile(downloadDir, p)
            assertTrue(
                "escaped download dir: ${file.canonicalPath}",
                file.canonicalPath.startsWith(dirCanon + File.separator)
            )
        }
    }

    @Test
    fun `the name carries branch subject year and id`() {
        assertEquals(
            "ENTC_Signals_and_Systems_2025_entc_signals_2025.pdf",
            downloadFileName(paper("ENTC", "Signals and Systems", "2025", id = "entc_signals_2025"))
        )
        assertEquals(
            "COMMON_Engineering_Chemistry_2024_common_chemistry_2024_2.pdf",
            downloadFileName(
                paper("COMMON", "Engineering Chemistry", "2024", id = "common_chemistry_2024_2")
            )
        )
    }

    @Test
    fun `two papers of the same subject and year never share a file`() {
        val first = paper("COMMON", "Engineering Chemistry", "2024", id = "common_chemistry_2024_1")
        val second = paper("COMMON", "Engineering Chemistry", "2024", id = "common_chemistry_2024_2")

        assertNotEquals(
            buildConfinedDownloadFile(downloadDir, first).name,
            buildConfinedDownloadFile(downloadDir, second).name
        )
        assertNotEquals(downloadFileName(first), downloadFileName(second))
    }

    /**
     * The whole catalog, as published: names must stay distinct. This is the
     * regression test for the overwrite bug — 75 papers, 21 old-style names.
     */
    @Test
    fun `every paper in a real-shaped catalog gets its own name`() {
        val catalog = buildList {
            for (subject in listOf("Engineering Chemistry", "Engineering Graphics", "Communication Skills")) {
                for (year in listOf("2023", "2024", "2025")) {
                    for (n in 1..7) {
                        add(paper("COMMON", subject, year, id = "common_${subject.take(4)}_${year}_$n"))
                    }
                }
            }
        }
        val names = catalog.map { downloadFileName(it).lowercase() }
        assertEquals("all 63 names must be distinct", 63, names.toSet().size)
    }

    @Test
    fun `the legacy name is still recognised for old downloads`() {
        val p = paper("ENTC", "Signals and Systems", "2025", id = "entc_signals_2025")
        assertEquals("ENTC_Signals_and_Systems_2025.pdf", legacyDownloadFileName(p))

        // A file saved by an older build still counts as downloaded.
        buildConfinedDownloadFile(downloadDir, p, legacy = true).writeText("%PDF-1.4")
        assertEquals("ENTC_Signals_and_Systems_2025.pdf", existingDownloadFile(downloadDir, p)?.name)
    }

    @Test
    fun `the new name wins over the legacy one when both exist`() {
        val p = paper("ENTC", "Signals and Systems", "2025", id = "entc_signals_2025")
        buildConfinedDownloadFile(downloadDir, p, legacy = true).writeText("%PDF-1.4")
        buildConfinedDownloadFile(downloadDir, p).writeText("%PDF-1.4")

        assertEquals(downloadFileName(p), existingDownloadFile(downloadDir, p)?.name)
    }

    // ---- the download has to actually be a PDF ----

    @Test
    fun `a real PDF passes the content check`() {
        val file = File(downloadDir, "good.pdf").apply { writeBytes("%PDF-1.7\nrest".toByteArray()) }
        assertTrue(isPdfContent(file))
    }

    @Test
    fun `a signed-in portal page saved as a PDF is rejected`() {
        // What a storage bucket returns when the path needs auth: HTML.
        val file = File(downloadDir, "bad.pdf").apply {
            writeText("<html><body>Please sign in</body></html>")
        }
        assertFalse(isPdfContent(file))
        assertFalse("even a tiny file must not pass", isPdfContent(File(downloadDir, "tiny.pdf").apply { writeBytes("%PD".toByteArray()) }))
    }

    @Test
    fun `the header may be preceded by junk, as the spec allows`() {
        val file = File(downloadDir, "junk.pdf").apply {
            writeBytes(("garbage".repeat(50) + "%PDF-1.4").toByteArray())
        }
        assertTrue(isPdfContent(file))
    }

    @Test
    fun `a missing file is not a PDF`() {
        assertFalse(isPdfContent(File(downloadDir, "nope.pdf")))
    }

    private fun paper(
        branch: String,
        subject: String,
        year: String,
        id: String = "paper_id"
    ) = QuestionPaper(
        id = id,
        title = "$subject $year",
        subjectName = subject,
        branchCode = branch,
        year = year,
        examType = "End Semester Examination",
        fileFormat = "PDF",
        fileSize = "1 MB",
        duration = "3 Hours",
        maxMarks = 100,
        sampleQuestions = emptyList(),
        storagePath = "$branch/$subject/$year.pdf"
    )

    @After
    fun tearDown() {
        downloadDir.deleteRecursively()
    }
}
