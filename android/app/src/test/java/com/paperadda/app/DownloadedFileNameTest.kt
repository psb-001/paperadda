package com.paperadda.app

import com.paperadda.app.data.model.QuestionPaper
import com.paperadda.app.data.repository.downloadNameToken
import com.paperadda.app.data.repository.matchCatalogPaper
import com.paperadda.app.data.repository.parseDownloadedPdfFileName
import com.paperadda.app.data.repository.recoveredDownloadFor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The download-file name is the only record of what a PDF on disk contains, so
 * the index-recovery parser has to round-trip exactly with the sanitiser that
 * wrote the name. Pure JVM — no Android, no database.
 */
class DownloadedFileNameTest {

    @Test
    fun `parses the three parts of a normal name`() {
        val parsed = parseDownloadedPdfFileName("ENTC_Data_Structures_2023-24.pdf")
        assertEquals("ENTC", parsed?.branchCode)
        assertEquals("Data_Structures", parsed?.subjectToken)
        assertEquals("2023-24", parsed?.yearToken)
    }

    @Test
    fun `joins the middle back into one subject token`() {
        // buildConfinedDownloadFile collapses runs of invalid characters to a
        // single underscore, so the original spacing is unrecoverable.
        val parsed = parseDownloadedPdfFileName("COMMON_Engineering_Chemistry_2022-23.pdf")
        assertEquals("COMMON", parsed?.branchCode)
        assertEquals("Engineering_Chemistry", parsed?.subjectToken)
        assertEquals("2022-23", parsed?.yearToken)
    }

    @Test
    fun `rejects names that are not our downloads`() {
        assertNull(parseDownloadedPdfFileName("random.pdf"))
        assertNull(parseDownloadedPdfFileName("no_parts_here.txt"))
        assertNull(parseDownloadedPdfFileName("_.pdf"))
    }

    @Test
    fun `token matches what the sanitizer produces`() {
        // Contiguous runs of invalid characters collapse to ONE underscore.
        assertEquals("Data_Structures", downloadNameToken("Data Structures"))
        assertEquals("C_Language", downloadNameToken("C++ Language"))
        assertEquals("Paper_1", downloadNameToken("  Paper 1  "))
        assertEquals("DBMS", downloadNameToken("DBMS"))
        // Leading/trailing separators are trimmed, so the name can always be
        // split back into exactly three parts.
        assertEquals("A_B", downloadNameToken("...A // B..."))
    }

    @Test
    fun `matches the catalog paper by subject and year`() {
        val papers = listOf(
            paper("p2", "Data Structures", "ENTC", "2022-23"),
            paper("p1", "Data Structures", "ENTC", "2023-24")
        )
        val parsed = parseDownloadedPdfFileName("ENTC_Data_Structures_2023-24.pdf")!!
        assertEquals("p1", matchCatalogPaper(papers, parsed)?.id)
    }

    @Test
    fun `falls back to the newest year when the file year is unknown to the catalog`() {
        val papers = listOf(
            paper("p2", "Data Structures", "ENTC", "2021-22"),
            paper("p1", "Data Structures", "ENTC", "2023-24")
        )
        val parsed = parseDownloadedPdfFileName("ENTC_Data_Structures_2019-20.pdf")!!
        assertEquals("p1", matchCatalogPaper(papers, parsed)?.id)
    }

    @Test
    fun `no catalog match returns null so a synthetic id is used`() {
        val papers = listOf(paper("p1", "Thermodynamics", "ENTC", "2023-24"))
        val parsed = parseDownloadedPdfFileName("ENTC_Unknown_Subject_2023-24.pdf")!!
        assertNull(matchCatalogPaper(papers, parsed))
    }

    @Test
    fun `recovered row uses the real catalog paper when matched`() {
        val parsed = parseDownloadedPdfFileName("ENTC_Data_Structures_2023-24.pdf")!!
        val paper = paper("p1", "Data Structures", "ENTC", "2023-24", title = "Data Structures · Paper 2")
        val file = File("/downloads/ENTC_Data_Structures_2023-24.pdf")

        val recovered = recoveredDownloadFor(file, parsed, paper)

        assertEquals("p1", recovered.paperId)
        assertEquals("Data Structures · Paper 2", recovered.title)
        assertEquals("ENTC", recovered.branchCode)
        assertEquals("2023-24", recovered.year)
    }

    @Test
    fun `recovered row without a catalog match stays readable and namespaced`() {
        val parsed = parseDownloadedPdfFileName("ENTC_Old_Paper_2019-20.pdf")!!
        val file = File("/downloads/ENTC_Old_Paper_2019-20.pdf")

        val recovered = recoveredDownloadFor(file, parsed, paper = null)

        assertEquals("recovered:ENTC_Old_Paper_2019-20.pdf", recovered.paperId)
        assertEquals("Old Paper", recovered.subjectName)
        assertEquals("2019-20", recovered.year)
    }

    private fun paper(
        id: String,
        subject: String,
        branch: String,
        year: String,
        title: String = "$subject $year"
    ) = QuestionPaper(
        id = id,
        title = title,
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
}
