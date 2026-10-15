package com.example

import com.example.data.model.BRANCH_COMMON
import com.example.data.model.QuestionPaper
import com.example.data.model.Subject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Paper counts are what students see on every subject row, so the fast
 * single-pass implementation must agree exactly with the straightforward
 * definition, including the shared First Year (COMMON) case.
 */
class PaperCountDerivationTest {

    private fun subject(
        name: String,
        branch: String,
        count: Int = 0
    ) = Subject(
        id = "$branch-$name",
        name = name,
        branchCode = branch,
        academicYear = 1,
        paperCount = count,
        iconName = "menu_book"
    )

    private fun paper(subjectName: String, branch: String) = QuestionPaper(
        id = "$branch-$subjectName-${subjectName.length}",
        title = "End Semester 2025",
        subjectName = subjectName,
        branchCode = branch,
        year = "2025",
        examType = "End Semester Examination"
    )

    /** The original, obviously-correct definition. */
    private fun reference(
        subjects: List<Subject>,
        papers: List<QuestionPaper>
    ): List<Subject> =
        subjects.map { s ->
            val actual = papers.count { p ->
                p.subjectName.equals(s.name, ignoreCase = true) &&
                    (
                        p.branchCode.equals(s.branchCode, ignoreCase = true) ||
                            p.branchCode.equals(BRANCH_COMMON, ignoreCase = true)
                        )
            }
            if (actual == s.paperCount) s else s.copy(paperCount = actual)
        }

    private fun countKey(subjectName: String, branchCode: String): String =
        subjectName.lowercase() + "\u0000" + branchCode.lowercase()

    /** Mirrors the optimised implementation under test. */
    private fun fast(
        subjects: List<Subject>,
        papers: List<QuestionPaper>
    ): List<Subject> {
        if (subjects.isEmpty()) return subjects
        val counts = HashMap<String, Int>(papers.size * 2)
        for (p in papers) {
            val k = countKey(p.subjectName, p.branchCode)
            counts[k] = (counts[k] ?: 0) + 1
        }
        var changed = false
        val out = ArrayList<Subject>(subjects.size)
        for (s in subjects) {
            val common = counts[countKey(s.name, BRANCH_COMMON)] ?: 0
            val own = if (s.branchCode.equals(BRANCH_COMMON, ignoreCase = true)) {
                0
            } else {
                counts[countKey(s.name, s.branchCode)] ?: 0
            }
            val actual = own + common
            if (actual == s.paperCount) out.add(s)
            else {
                out.add(s.copy(paperCount = actual)); changed = true
            }
        }
        return if (changed) out else subjects
    }

    @Test
    fun matchesTheReferenceDefinition() {
        val subjects = listOf(
            subject("Signals and Systems", "ENTC", 99),
            subject("Signals and Systems", BRANCH_COMMON),
            subject("Digital Electronics", "ENTC"),
            subject("Maths", "CE", 4),
            subject("Physics", "AIML")
        )
        val papers = listOf(
            paper("Signals and Systems", "ENTC"),
            paper("Signals and Systems", "ENTC"),
            paper("Signals and Systems", BRANCH_COMMON),
            paper("Digital Electronics", BRANCH_COMMON),
            paper("Maths", "CE")
        )
        assertEquals(reference(subjects, papers), fast(subjects, papers))
    }

    @Test
    fun sharedFirstYearCountsDoNotDoubleForCommonSubjects() {
        val subjects = listOf(subject("Engineering Physics", BRANCH_COMMON))
        val papers = listOf(
            paper("Engineering Physics", BRANCH_COMMON),
            paper("Engineering Physics", BRANCH_COMMON),
            paper("Engineering Physics", BRANCH_COMMON)
        )
        val result = fast(subjects, papers)
        assertEquals(3, result[0].paperCount)
        assertEquals(reference(subjects, papers), result)
    }

    @Test
    fun matchesCaseInsensitively() {
        val subjects = listOf(subject("Signals and Systems", "ENTC"))
        val papers = listOf(
            paper("signals and systems", "ENTC"),
            paper("SIGNALS AND SYSTEMS", "ENTC")
        )
        assertEquals(2, fast(subjects, papers)[0].paperCount)
    }

    @Test
    fun returnsTheSameInstanceWhenNoCountChanges() {
        val subjects = listOf(subject("Maths", "CE", 1))
        val papers = listOf(paper("Maths", "CE"))
        // Identity matters: a fresh list on every read was forcing Compose to
        // treat an unchanged catalogue as new.
        assertEquals(subjects, fast(subjects, papers))
    }

    @Test
    fun emptyInputsAreHandled() {
        assertEquals(emptyList<Subject>(), fast(emptyList(), listOf(paper("x", "ENTC"))))
        val subjects = listOf(subject("Nothing", "ENTC"))
        assertEquals(0, fast(subjects, emptyList())[0].paperCount)
    }
}
