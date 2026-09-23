package com.example.data.model

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sentinel branch code for content shared by every branch.
 * First-year subjects/papers/notes use this instead of ENTC/AIML/CE/IT.
 */
const val BRANCH_COMMON = "COMMON"

fun branchDisplayName(code: String): String =
    if (code.equals(BRANCH_COMMON, ignoreCase = true)) "First Year" else code

data class Branch(
    val code: String, // e.g. "ENTC", "AIML", "CE", "IT"
    val fullName: String, // e.g. "Electronics and Telecommunication Engineering"
    val iconName: String // "memory", "smart_toy", "computer", "language"
)

data class Subject(
    val id: String,
    val name: String, // e.g. "Signals and Systems"
    val branchCode: String, // "ENTC" or "COMMON"
    val academicYear: Int = 1, // 1 = First Year ... 4 = Final Year
    val paperCount: Int, // e.g. 12
    val iconName: String // "graphic_eq", "memory", "cell_tower", etc.
) {
    val supportingText: String
        get() {
            val scope = if (branchCode.equals(BRANCH_COMMON, ignoreCase = true)) {
                "All branches"
            } else {
                branchCode
            }
            return "$scope · $paperCount question papers"
        }
}

/** Academic years shown after picking a branch: 1..4. */
data class AcademicYear(
    val year: Int, // 1..4
    val label: String // "First Year" ... "Final Year"
) {
    companion object {
        val ALL = listOf(
            AcademicYear(1, "First Year"),
            AcademicYear(2, "Second Year"),
            AcademicYear(3, "Third Year"),
            AcademicYear(4, "Final Year")
        )

        /** Years reachable from a branch card — first year is COMMON, so branches start at 2. */
        val BRANCH_ONLY = ALL.filter { it.year != 1 }

        fun labelFor(year: Int): String =
            ALL.find { it.year == year }?.label ?: "Year $year"
    }
}

data class QuestionPaper(
    val id: String,
    val title: String, // e.g. "End Semester Examination 2025"
    val subjectName: String, // "Signals and Systems"
    val branchCode: String, // "ENTC" or "COMMON"
    val year: String, // "2025"
    val examType: String, // "End Semester Examination"
    val fileFormat: String = "PDF",
    val fileSize: String = "2.4 MB",
    val duration: String = "3 Hours",
    val maxMarks: Int = 100,
    val sampleQuestions: List<String> = emptyList(),
    val storagePath: String = "" // Supabase Storage path; empty = generate locally
) {
    val supportingText: String
        get() {
            val scope = if (branchCode.equals(BRANCH_COMMON, ignoreCase = true)) {
                "All branches"
            } else {
                branchCode
            }
            return "$scope · $year · $fileFormat"
        }
}
