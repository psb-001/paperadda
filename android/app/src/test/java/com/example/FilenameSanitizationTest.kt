package com.example

import com.example.data.repository.buildConfinedDownloadFile
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the download filename confinement fix.
 * Pure JVM (no Robolectric): calls the real [buildConfinedDownloadFile]
 * used by PaperRepository.downloadPaper. Hostile catalog text must never
 * escape the download directory; legitimate names keep historical filenames.
 */
class FilenameSanitizationTest {

    private val downloadDir: File = Files.createTempDirectory("paperadda-downloads").toFile()

    @Test
    fun `traversal payloads stay inside download dir`() {
        val dirCanon = downloadDir.canonicalPath
        listOf(
            Triple("../../evil", "Signals and Systems", "2025"),
            Triple("ENTC", "a/b", "2025"),
            Triple("ENTC", "..\\windows", "2025"),
            Triple("", "", ""),
            Triple("...", "...", "..."),
            Triple("ENTC", "x".repeat(200), "2025"),
            Triple("ENTC", "/abs/path", "2025"),
        ).forEach { (branch, subject, year) ->
            val file = buildConfinedDownloadFile(downloadDir, branch, subject, year)
            assertTrue(
                "escaped download dir: ${file.canonicalPath}",
                file.canonicalPath.startsWith(dirCanon + File.separator)
            )
        }
    }

    @Test
    fun `legitimate names keep historical filenames`() {
        assertEquals(
            "ENTC_Signals_and_Systems_2025.pdf",
            buildConfinedDownloadFile(downloadDir, "ENTC", "Signals and Systems", "2025").name
        )
        assertEquals(
            "CE_Engineering_Mathematics_I_2025.pdf",
            buildConfinedDownloadFile(downloadDir, "CE", "Engineering Mathematics I", "2025").name
        )
    }

    @After
    fun tearDown() {
        downloadDir.deleteRecursively()
    }
}
