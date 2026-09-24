package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadedPaperEntity
import com.example.data.local.NoteEntity
import com.example.data.local.SavedPaperEntity
import com.example.data.model.Branch
import com.example.data.model.QuestionPaper
import com.example.data.model.Subject
import com.example.data.remote.SupabaseRemoteDataSource
import com.example.data.repository.NoteRepository
import com.example.data.repository.PaperRepository
import com.example.ui.components.NavDestination
import com.example.ui.navigation.AppScreen
import com.example.ui.navigation.NavigationDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val remote = SupabaseRemoteDataSource()
    private val repository = PaperRepository(
        database.paperDao(),
        database.subjectCatalogDao(),
        database.paperCatalogDao(),
        remote,
        application
    )
    private val noteRepository = NoteRepository(database.noteDao())

    val branches: List<Branch> = repository.branches

    val savedPapers: StateFlow<List<SavedPaperEntity>> = repository.savedPapers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedPapers: StateFlow<List<DownloadedPaperEntity>> = repository.downloadedPapers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = noteRepository.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bumped every time the synced catalog changes so catalog screens
    // (which read synchronous getters) recompose with fresh data.
    private val _catalogRevision = MutableStateFlow(0)
    val catalogRevision: StateFlow<Int> = _catalogRevision.asStateFlow()

    init {
        // Offline cache first (instant UI), then remote refresh. No local seed.
        viewModelScope.launch {
            repository.loadCache()
            _catalogRevision.value++
            if (repository.refreshFromRemote()) _catalogRevision.value++
            noteRepository.refreshFromRemote(remote)
        }
    }

    // Navigation Stack
    val backStack = mutableStateListOf<AppScreen>(AppScreen.Home)
    var navDirection by mutableStateOf(NavigationDirection.FORWARD)
        private set

    val currentScreen: AppScreen
        get() = backStack.lastOrNull() ?: AppScreen.Home

    val currentNavDestination: NavDestination
        get() = when (currentScreen) {
            is AppScreen.Saved -> NavDestination.SAVED
            // Everything under Home (branches, years, papers, notes) keeps
            // the Home tab highlighted — there are no separate Papers/Notes tabs.
            else -> NavDestination.HOME
        }

    // Search state
    var isSearchOpen by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<QuestionPaper>>(emptyList())

    // Drawer / Info sheet state
    var isInfoSheetOpen by mutableStateOf(false)

    // Active downloading state
    var downloadingPaperId by mutableStateOf<String?>(null)
    var downloadMessage by mutableStateOf<String?>(null)

    // Active note-PDF open (mirrors paper download state)
    var openingNoteId by mutableStateOf<Long?>(null)

    fun navigateTo(screen: AppScreen, direction: NavigationDirection = NavigationDirection.FORWARD) {
        navDirection = direction
        backStack.add(screen)
    }

    fun navigateBack(): Boolean {
        if (backStack.size > 1) {
            navDirection = NavigationDirection.BACK
            backStack.removeAt(backStack.lastIndex)
            return true
        }
        // At a tab root that is not Home (e.g. Saved or Notes opened from the
        // bottom bar, which reset the stack): go back to Home instead of
        // exiting the app — like every standard Android app. Only Home exits.
        if (currentScreen !is AppScreen.Home) {
            navDirection = NavigationDirection.TAB_SWITCH
            backStack.clear()
            backStack.add(AppScreen.Home)
            return true
        }
        return false
    }

    fun selectBottomNav(destination: NavDestination) {
        navDirection = NavigationDirection.TAB_SWITCH
        backStack.clear()
        backStack.add(
            when (destination) {
                NavDestination.HOME -> AppScreen.Home
                NavDestination.SAVED -> AppScreen.Saved
            }
        )
    }

    fun getBranch(branchCode: String): Branch? {
        return repository.getBranch(branchCode)
    }

    fun getSubjectsForBranch(branchCode: String): List<Subject> {
        return repository.getSubjectsForBranch(branchCode)
    }

    fun getSubjectsForBranchAndYear(branchCode: String, academicYear: Int): List<Subject> {
        return repository.getSubjectsForBranchAndYear(branchCode, academicYear)
    }

    fun getPapersForSubject(subjectName: String, branchCode: String): List<QuestionPaper> {
        return repository.getPapersForSubject(subjectName, branchCode)
    }

    fun getPaperById(id: String): QuestionPaper? {
        return repository.getPaperById(id)
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        searchResults = repository.searchAll(query)
    }

    fun isPaperSaved(paperId: String): Boolean {
        return savedPapers.value.any { it.paperId == paperId }
    }

    fun toggleBookmark(paper: QuestionPaper) {
        viewModelScope.launch {
            repository.toggleBookmark(paper)
        }
    }

    fun downloadQuestionPaper(paper: QuestionPaper) {
        if (downloadingPaperId != null) return
        downloadingPaperId = paper.id
        viewModelScope.launch {
            try {
                val file = repository.downloadPaper(paper)
                downloadMessage = "Downloaded: ${file.name}"
                Toast.makeText(
                    getApplication(),
                    "Downloaded: ${paper.subjectName} (${paper.year}) PDF",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                downloadMessage = "Download failed: ${e.localizedMessage}"
                Toast.makeText(
                    getApplication(),
                    "Failed to download: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                downloadingPaperId = null
            }
        }
    }

    fun openDownloadedPdf(file: File) {
        try {
            val uri: Uri = repository.getFileUri(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                getApplication(),
                "No PDF reader found. File saved at: ${file.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Open a published note's PDF (Supabase Storage via [NoteEntity.storagePath]).
     * Falls back to the legacy public [NoteEntity.fileUrl] if set; otherwise
     * toasts (seed/legacy notes without an uploaded file).
     */
    fun openNotePdf(note: NoteEntity) {
        if (openingNoteId != null) return
        if (note.storagePath.isBlank() && note.fileUrl.isBlank()) {
            Toast.makeText(
                getApplication(),
                "No PDF attached to this note yet",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (note.fileUrl.isBlank()) {
            openingNoteId = note.id
            viewModelScope.launch {
                try {
                    val app = getApplication<Application>()
                    val downloadDir = app.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                        ?: app.filesDir
                    val safeName = note.title
                        .trim()
                        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                        .trim('_', '.', '-')
                        .ifBlank { "note" }
                        .take(80) + ".pdf"
                    val file = File(downloadDir, safeName)
                    // Catalog text is admin-influenced — keep the file inside downloadDir.
                    require(file.canonicalPath == downloadDir.canonicalPath + File.separator + file.name) {
                        "Unsafe note file name rejected"
                    }
                    remote.downloadPublicFile(note.storagePath, file)
                    openDownloadedPdf(file)
                } catch (e: Exception) {
                    Toast.makeText(
                        getApplication(),
                        "Failed to open note: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    openingNoteId = null
                }
            }
            return
        }
        // Legacy absolute URL — hand off to the system viewer / browser.
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(note.fileUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                getApplication(),
                "No app available to open this link",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
