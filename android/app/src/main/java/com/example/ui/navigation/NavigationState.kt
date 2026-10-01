package com.example.ui.navigation

sealed class AppScreen {
    object Home : AppScreen()
    data class BranchYears(val branchCode: String) : AppScreen()
    data class YearMenu(
        val branchCode: String,
        val academicYear: Int
    ) : AppScreen()
    data class PaperLibrary(
        val branchCode: String,
        val academicYear: Int
    ) : AppScreen()
    data class YearNotes(
        val branchCode: String,
        val academicYear: Int
    ) : AppScreen()
    data class QuestionPapers(
        val subjectName: String,
        val branchCode: String
    ) : AppScreen()
    data class PaperDetails(val paperId: String) : AppScreen()
    data class PdfReader(val filePath: String, val title: String) : AppScreen()
    object Requests : AppScreen()
    object Feedback : AppScreen()
}

enum class NavigationDirection {
    FORWARD,
    BACK,
    TAB_SWITCH
}
