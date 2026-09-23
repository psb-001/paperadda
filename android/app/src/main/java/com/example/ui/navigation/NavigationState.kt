package com.example.ui.navigation

sealed class AppScreen {
    object Home : AppScreen()
    data class BranchYears(val branchCode: String = "ENTC") : AppScreen()
    data class YearMenu(
        val branchCode: String = "ENTC",
        val academicYear: Int = 1
    ) : AppScreen()
    data class PaperLibrary(
        val branchCode: String = "ENTC",
        val academicYear: Int = 1
    ) : AppScreen()
    data class YearNotes(
        val branchCode: String = "ENTC",
        val academicYear: Int = 1
    ) : AppScreen()
    data class QuestionPapers(
        val subjectName: String = "Signals and Systems",
        val branchCode: String = "ENTC"
    ) : AppScreen()
    data class PaperDetails(val paperId: String = "entc_ss_2025") : AppScreen()
    object Saved : AppScreen()
}

enum class NavigationDirection {
    FORWARD,
    BACK,
    TAB_SWITCH
}
