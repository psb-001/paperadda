package com.example

import com.example.data.model.QuestionPaper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Paper titles are shown under a heading that already names the subject, so a
 * title that repeats it must be trimmed. Also guards the "no invented data"
 * rule for marks and duration.
 */
class QuestionPaperDisplayTest {

    private fun paper(
        title: String,
        subject: String = "PPS (Programming for Problem Solving)",
        maxMarks: Int = 70,
        duration: String = "3 Hours"
    ) = QuestionPaper(
        id = "x",
        title = title,
        subjectName = subject,
        branchCode = "COMMON",
        year = "2025",
        examType = "End Semester Examination",
        maxMarks = maxMarks,
        duration = duration
    )

    @Test
    fun stripsSubjectNameAndSeparator() {
        assertEquals(
            "End Semester Examination 2025",
            paper("PPS (Programming for Problem Solving) — End Semester Examination 2025").displayTitle
        )
    }

    @Test
    fun stripsSubjectNameWithOtherSeparators() {
        for (sep in listOf(" - ", " – ", ": ", " | ", ", ", "—")) {
            assertEquals(
                "separator \"$sep\" should be stripped",
                "End Semester Examination 2025",
                paper("PPS (Programming for Problem Solving)$sep" + "End Semester Examination 2025")
                    .displayTitle
            )
        }
    }

    @Test
    fun keepsTitleWhenSubjectIsNotAPrefix() {
        assertEquals(
            "End Semester Examination 2025",
            paper("End Semester Examination 2025").displayTitle
        )
    }

    @Test
    fun fallsBackToRawTitleWhenStrippingWouldEmptyIt() {
        // Title is nothing but the subject name — showing nothing would be worse.
        assertEquals(
            "PPS (Programming for Problem Solving)",
            paper("PPS (Programming for Problem Solving)").displayTitle
        )
    }

    @Test
    fun doesNotStripSubjectNameAppearingMidTitle() {
        assertEquals(
            "Paper covering PPS (Programming for Problem Solving) basics",
            paper("Paper covering PPS (Programming for Problem Solving) basics").displayTitle
        )
    }

    @Test
    fun unsetMarksAndDurationHaveNoFabricatedDefault() {
        val p = paper("End Semester Examination 2026", maxMarks = 0, duration = "")
        assertEquals(0, p.maxMarks)
        assertEquals("", p.duration)
    }
}
