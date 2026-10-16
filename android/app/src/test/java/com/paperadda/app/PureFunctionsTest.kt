package com.paperadda.app

import com.paperadda.app.data.model.AppRelease
import com.paperadda.app.data.model.BRANCH_COMMON
import com.paperadda.app.data.model.branchDisplayName
import com.paperadda.app.data.model.compareVersions
import com.paperadda.app.data.model.normalizeVersion
import com.paperadda.app.data.model.QuestionPaper
import com.paperadda.app.data.update.apkFileName
import com.paperadda.app.data.repository.downloadFileName
import com.paperadda.app.data.repository.downloadNameToken
import com.paperadda.app.data.repository.isPdfContent
import com.paperadda.app.data.repository.legacyDownloadFileName
import com.paperadda.app.ui.isPlausibleEmail
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure functions that sit on the boundary between the app and the backend —
 * version comparison, note/download naming, email validation, PDF detection.
 * Tested without Android or a network.
 */
class PureFunctionsTest {

    // ---- version comparison ----

    @Test
    fun `numeric parts compare as numbers`() {
        assertTrue(compareVersions("1.10", "1.9") > 0)
        assertTrue(compareVersions("1.9", "1.10") < 0)
        assertEquals(0, compareVersions("1.2", "1.2.0"))
        assertEquals(0, compareVersions("1.2", "1.2"))
    }

    @Test
    fun `leading v is ignored`() {
        assertTrue(compareVersions("v1.2", "1.2") == 0)
        assertTrue(compareVersions("V2.0", "2.0") == 0)
        assertTrue(compareVersions("1.11", "v1.9") > 0)
    }

    @Test
    fun `missing parts count as zero`() {
        assertEquals(0, compareVersions("1", "1.0"))
        assertTrue(compareVersions("1.0.1", "1") > 0)
    }

    @Test
    fun `non-numeric suffixes parse to zero`() {
        assertEquals(0, compareVersions("1.2-beta", "1.2"))
        assertTrue(compareVersions("1.2", "1.2-rc1") == 0)
    }

    @Test
    fun `normalizeVersion strips only the leading v`() {
        assertEquals("1.2", normalizeVersion("v1.2"))
        assertEquals("1.2", normalizeVersion("V1.2"))
        assertEquals("1.2", normalizeVersion("1.2"))
        assertEquals("1.2-beta", normalizeVersion("v1.2-beta"))
    }

    // ---- branch display name ----

    @Test
    fun `common branch displays as first year`() {
        assertEquals("First Year", branchDisplayName("COMMON"))
        assertEquals("ENTC", branchDisplayName("ENTC"))
        assertEquals("AIML", branchDisplayName("AIML"))
    }

    // ---- question paper display title strips repeated subject prefix ----

    @Test
    fun `repeated subject prefix is stripped`() {
        val paper = questionPaper(
            branchCode = "PPS",
            subjectName = "PPS (Programming for Problem Solving)",
            year = "2025",
            id = "p1",
            title = "PPS (Programming for Problem Solving) - End Semester 2025"
        )
        assertEquals("End Semester 2025", paper.displayTitle)
    }

    @Test
    fun `non-matching prefix leaves title unchanged`() {
        val paper = questionPaper(
            branchCode = "Data Structures",
            subjectName = "Data Structures",
            year = "2024",
            id = "p2",
            title = "End Semester Examination 2024"
        )
        assertEquals("End Semester Examination 2024", paper.displayTitle)
    }

    @Test
    fun `case insensitive prefix match`() {
        val paper = questionPaper(
            branchCode = "Data Structures",
            subjectName = "Data Structures",
            year = "2024",
            id = "p3",
            title = "data structures end semester"
        )
        assertEquals("end semester", paper.displayTitle)
    }

    // ---- download name token ----

    @Test
    fun `token collapses invalid characters to one underscore`() {
        assertEquals("Data_Structures", downloadNameToken("Data Structures"))
        assertEquals("C_Language", downloadNameToken("C++ Language"))
        assertEquals("Paper_1", downloadNameToken("  Paper 1  "))
        assertEquals("A_B", downloadNameToken("...A // B..."))
        assertEquals("DBMS", downloadNameToken("DBMS"))
    }

    // ---- download file name includes the paper id ----

    @Test
    fun `file name carries branch subject year and id`() {
        assertEquals(
            "ENTC_Signals_and_Systems_2025_entc_signals_2025.pdf",
            downloadFileName(
                questionPaper(branchCode = "ENTC", subjectName = "Signals and Systems", year = "2025", id = "entc_signals_2025")
            )
        )
        assertEquals(
            "COMMON_Engineering_Chemistry_2024_common_chemistry_2024_2.pdf",
            downloadFileName(
                questionPaper(branchCode = "COMMON", subjectName = "Engineering Chemistry", year = "2024", id = "common_chemistry_2024_2")
            )
        )
    }

    @Test
    fun `legacy name drops the id`() {
        assertEquals(
            "ENTC_Signals_and_Systems_2025.pdf",
            legacyDownloadFileName(
                questionPaper(branchCode = "ENTC", subjectName = "Signals and Systems", year = "2025", id = "entc_signals_2025")
            )
        )
    }

    // ---- PDF content detection ----

    @Test
    fun `real pdf passes`() {
        val f = File(createTempDir(), "good.pdf").apply { writeBytes("%PDF-1.7".toByteArray()) }
        assertTrue(isPdfContent(f))
    }

    @Test
    fun `html saved as pdf is rejected`() {
        val f = File(createTempDir(), "bad.pdf").apply { writeText("<html>sign in</html>") }
        assertFalse(isPdfContent(f))
    }

    @Test
    fun `header may be preceded by junk`() {
        val f = File(createTempDir(), "junk.pdf").apply {
            writeBytes(("garbage".repeat(50) + "%PDF-1.4").toByteArray())
        }
        assertTrue(isPdfContent(f))
    }

    // ---- email validation ----

    @Test
    fun `normal addresses pass`() {
        assertTrue(isPlausibleEmail("student@college.edu"))
        assertTrue(isPlausibleEmail("a.b-c_d@sub.domain.co.in"))
    }

    @Test
    fun `broken addresses are rejected`() {
        assertFalse(isPlausibleEmail("student"))
        assertFalse(isPlausibleEmail("student@"))
        assertFalse(isPlausibleEmail("@college.edu"))
        assertFalse(isPlausibleEmail("student@@college.edu"))
        assertFalse(isPlausibleEmail("student@college"))
        assertFalse(isPlausibleEmail("student name@college.edu"))
        assertFalse(isPlausibleEmail("student@college."))
        assertFalse(isPlausibleEmail(""))
    }

    @Test
    fun `over long address is rejected`() {
        assertFalse(isPlausibleEmail("a".repeat(250) + "@college.edu"))
    }

    // ---- APK file name sanitizer ----

    @Test
    fun `normal version names are kept`() {
        assertEquals("paperadda-1.6.apk", apkFileName(release("1.6")))
        assertEquals("paperadda-v1.6.apk", apkFileName(release("v1.6")))
        assertEquals("paperadda-1.6-beta-1.apk", apkFileName(release("1.6-beta-1")))
    }

    @Test
    fun `path characters are replaced never kept`() {
        assertEquals("paperadda-1.6-.apk", apkFileName(release("1.6/")))
        assertEquals("paperadda-1.6-.apk", apkFileName(release("1.6\\")))
        assertEquals("paperadda-1.6-.apk", apkFileName(release("1.6 ")))
        assertEquals("paperadda-1.6--beta-1-.apk", apkFileName(release("1.6 (beta 1)")))
    }

    @Test
    fun `traversal cannot escape updates folder`() {
        val name = apkFileName(release("../../outside"))
        assertFalse("no directory separators survive", name.contains('/'))
        assertFalse("no backslash separators survive", name.contains('\\'))
    }

    @Test
    fun `empty version still produces usable name`() {
        assertEquals("paperadda-.apk", apkFileName(release("")))
    }

    private fun createTempDir(): File =
        java.nio.file.Files.createTempDirectory("paperadda-test").toFile()

    private fun questionPaper(
        branchCode: String,
        subjectName: String,
        year: String,
        id: String = "paper_id",
        title: String? = null
    ) = QuestionPaper(
        id = id,
        title = title ?: "$subjectName $year",
        subjectName = subjectName,
        branchCode = branchCode,
        year = year,
        examType = "End Semester Examination",
        fileFormat = "PDF",
        fileSize = "1 MB",
        duration = "3 Hours",
        maxMarks = 100,
        sampleQuestions = emptyList(),
        storagePath = "$branchCode/$subjectName/$year.pdf"
    )

    private fun release(version: String) = AppRelease(
        id = "1",
        version = version,
        notes = "",
        apkUrl = "https://example.com/paperadda.apk",
        releasePageUrl = "https://example.com/releases/latest",
        minSupportedVersion = null
    )
}
