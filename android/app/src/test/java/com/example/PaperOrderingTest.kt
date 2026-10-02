package com.example

import com.example.data.model.QuestionPaper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The backend orders papers by year only, so within a year the order was whatever
 * the database happened to return — a list could read Paper 1..7 then Paper 1 again.
 */
class PaperOrderingTest {

    private fun paper(year: String, title: String) = QuestionPaper(
        id = "$year-$title", title = title, subjectName = "S", branchCode = "COMMON",
        year = year, examType = "End Semester Examination"
    )

    /** Mirrors the repository's comparator. */
    private val order = compareByDescending<QuestionPaper> { it.year }
        .thenBy { num(it.title) }
        .thenBy { it.title.lowercase() }

    private fun num(title: String) = Regex("""·\s*Paper\s+(\d+)""", RegexOption.IGNORE_CASE)
        .find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE

    private fun sorted(titles: List<Pair<String, String>>) =
        titles.map { paper(it.first, it.second) }.sortedWith(order).map { it.title }

    @Test
    fun papersAreNumberedNotAlphabetical() {
        val out = sorted(
            listOf(
                "2025" to "End Semester Examination 2025 · Paper 2",
                "2025" to "End Semester Examination 2025 · Paper 10",
                "2025" to "End Semester Examination 2025 · Paper 1"
            )
        )
        // "Paper 10" must not sort between 1 and 2.
        assertEquals(
            listOf(
                "End Semester Examination 2025 · Paper 1",
                "End Semester Examination 2025 · Paper 2",
                "End Semester Examination 2025 · Paper 10"
            ),
            out
        )
    }

    @Test
    fun newestYearComesFirst() {
        val out = sorted(
            listOf(
                "2024" to "End Semester Examination 2024 · Paper 1",
                "2026" to "End Semester Examination 2026 · Paper 1",
                "2025" to "End Semester Examination 2025 · Paper 7",
                "2025" to "End Semester Examination 2025 · Paper 1"
            )
        )
        assertEquals(
            listOf(
                "End Semester Examination 2026 · Paper 1",
                "End Semester Examination 2025 · Paper 1",
                "End Semester Examination 2025 · Paper 7",
                "End Semester Examination 2024 · Paper 1"
            ),
            out
        )
    }

    @Test
    fun untitledPapersGoLastRatherThanDisappearing() {
        val out = sorted(
            listOf(
                "2025" to "End Semester Examination 2025 · Paper 1",
                "2025" to "End Semester Examination"
            )
        )
        assertEquals("End Semester Examination", out.last())
    }
}
