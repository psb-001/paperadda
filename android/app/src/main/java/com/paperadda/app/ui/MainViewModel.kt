package com.paperadda.app.ui

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
import com.paperadda.app.data.local.AppDatabase
import com.paperadda.app.data.local.DownloadedPaperEntity
import com.paperadda.app.data.local.NoteEntity
import com.paperadda.app.data.model.AppRelease
import com.paperadda.app.data.model.Branch
import com.paperadda.app.data.model.QuestionPaper
import com.paperadda.app.data.model.Subject
import com.paperadda.app.data.remote.SupabaseConfig
import com.paperadda.app.data.remote.SupabaseRemoteDataSource
import com.paperadda.app.data.repository.ContentRequest
import com.paperadda.app.data.repository.ContentRequestRepository
import com.paperadda.app.data.repository.FeedbackRepository
import com.paperadda.app.data.repository.NoteRepository
import com.paperadda.app.data.repository.PaperRepository
import com.paperadda.app.data.repository.UpdateInstaller
import com.paperadda.app.data.repository.FileExporter
import com.paperadda.app.data.repository.UpdateRepository
import com.paperadda.app.data.repository.isPdfContent
import com.paperadda.app.data.update.UpdateManager
import com.paperadda.app.ui.components.NavDestination
import com.paperadda.app.ui.navigation.AppScreen
import com.paperadda.app.ui.navigation.NavigationDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Upper bounds on user-supplied text. The server validates too; these stop a
 * phone from uploading something absurd before the request even leaves.
 */
private const val REQUEST_TITLE_MAX = 120
private const val REQUEST_DETAILS_MAX = 1000
private const val FEEDBACK_MESSAGE_MAX = 2000
private const val EMAIL_MAX = 254

/**
 * A pragmatic address check: one @, a dot-bearing domain, no spaces. Deliberately
 * loose — the goal is catching typos, not rejecting a valid new TLD.
 * Pure JVM so it is unit-tested without Android.
 */
internal fun isPlausibleEmail(raw: String): Boolean {
    val email = raw.trim()
    if (email.length > EMAIL_MAX) return false
    if (email.any { it.isWhitespace() }) return false
    val parts = email.split("@")
    if (parts.size != 2) return false
    val (local, domain) = parts
    if (local.isEmpty() || domain.isEmpty()) return false
    if (!domain.contains('.') || domain.startsWith('.') || domain.endsWith('.')) return false
    return domain.none { it.isWhitespace() }
}

/**
 * Gate for the legacy note `fileUrl` branch of [MainViewModel.openNotePdf].
 * An uninformed tap must never dispatch an admin-influenced string to an
 * external viewer, so only https links on our own Supabase project host are
 * allowed (the portal only ever stores Supabase storage URLs there).
 * Everything else — other schemes (javascript:, data:, intent:, file:, …),
 * other hosts, userinfo/port tricks, non-URLs — is rejected.
 * Pure JVM (java.net.URI) so regression tests can call it without Android.
 * Top-level + internal for the same reason.
 */
internal fun isAllowedLegacyNoteUrl(rawUrl: String): Boolean {
    val allowedHost = try {
        java.net.URI(SupabaseConfig.URL).host?.lowercase()
    } catch (_: Exception) {
        null
    }
    if (allowedHost.isNullOrBlank()) return false
    return try {
        val uri = java.net.URI(rawUrl.trim())
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase() == allowedHost
    } catch (_: Exception) {
        false
    }
}

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
    private val contentRequestRepository = ContentRequestRepository(application)
    private val feedbackRepository = FeedbackRepository(application)
    private val updateRepository = UpdateRepository(application)
    private val updateInstaller = UpdateInstaller(application)
    private val fileExporter = FileExporter(application)

    // ---- App update (no store listing, so the app asks the backend) ----
    // The flow itself lives in UpdateManager (testable without a screen); these
    // are Compose-visible mirrors of its StateFlows, collected in init.
    private val updateManager = UpdateManager(
        application,
        viewModelScope,
        updateRepository,
        updateInstaller
    )

    var availableUpdate by mutableStateOf<AppRelease?>(null)
        private set
    var showUpdateDialog by mutableStateOf(false)
        private set
    var isCheckingForUpdate by mutableStateOf(false)
        private set
    var updateMessage by mutableStateOf<String?>(null)
        private set

    /**
     * Real progress for the update download. The previous code only kept the
     * download id, so the dialog's button stayed on "Install update" with a
     * spinner for ever and each tap failed with "isn't finished yet".
     */
    var updateDownloadState by mutableStateOf<UpdateInstaller.DownloadState>(
        UpdateInstaller.DownloadState.Idle
    )
        private set
    var isUpdateRequired by mutableStateOf(false)
        private set

    val installedVersionName: String get() = updateManager.installedVersionName
    val installedVersionCode: Int get() = updateManager.installedVersionCode

    /** Called once on launch; throttled so it does not hit the network every start. */
    fun checkForUpdateInBackground() = updateManager.checkInBackground()

    /** Manual "Check for updates" — always asks, and always answers. */
    fun checkForUpdateManually() = updateManager.checkManually()

    /** Re-open the prompt from the Home banner after the user tapped "Later". */
    fun openUpdateDialog() = updateManager.openDialog()

    fun dismissUpdateDialog() = updateManager.dismissDialog()

    /** Start the APK download, or route to the permission screen if blocked. */
    fun downloadUpdate() = updateManager.download()

    /** Abandons the current download and starts again. */
    fun retryUpdateDownload() = updateManager.retryDownload()

    /** Hand the finished download to the system installer. */
    fun installDownloadedUpdate() = updateManager.installDownloadedUpdate()

    fun openUpdateInBrowser() = updateManager.openInBrowser()

    fun dismissUpdateMessage() = updateManager.dismissMessage()

    val branches: List<Branch> = repository.branches

    val contentRequests = MutableStateFlow<List<ContentRequest>>(emptyList())
    var isLoadingRequests by mutableStateOf(false)
        private set
    var isSubmittingRequest by mutableStateOf(false)
        private set
    var deletingRequestId by mutableStateOf<String?>(null)
        private set
    var requestFeedback by mutableStateOf<String?>(null)
        private set
    var requestFeedbackIsError by mutableStateOf(false)
        private set
    var isSubmittingFeedback by mutableStateOf(false)
        private set
    var feedbackMessage by mutableStateOf<String?>(null)
        private set
    var feedbackMessageIsError by mutableStateOf(false)
        private set

    val downloadedPapers: StateFlow<List<DownloadedPaperEntity>> = repository.downloadedPapers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = noteRepository.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bumped every time the synced catalog changes so catalog screens
    // (which read synchronous getters) recompose with fresh data.
    private val _catalogRevision = MutableStateFlow(0)
    val catalogRevision: StateFlow<Int> = _catalogRevision.asStateFlow()

    // Catalog sync state (Fix 2: failures are visible, not silent).
    // True while a network refresh is in flight (drives pull-to-refresh
    // indicators); non-null message means the last refresh failed and the
    // UI is showing offline/cached data.
    var isSyncing by mutableStateOf(false)
        private set
    var syncErrorMessage by mutableStateOf<String?>(null)
        private set

    init {
        collectUpdateState()
        // Offline cache first (instant UI), then remote refresh.
        // Until the backend lands, the published-notes list is seeded locally.
        viewModelScope.launch {
            runCatalogSync()
        }
        updateManager.checkInBackground()
    }

    /** Keeps the mirrored update state in step with [UpdateManager]. */
    private fun collectUpdateState() {
        viewModelScope.launch {
            updateManager.availableUpdate.collect { availableUpdate = it }
        }
        viewModelScope.launch {
            updateManager.showDialog.collect { showUpdateDialog = it }
        }
        viewModelScope.launch {
            updateManager.isChecking.collect { isCheckingForUpdate = it }
        }
        viewModelScope.launch {
            updateManager.message.collect { updateMessage = it }
        }
        viewModelScope.launch {
            updateManager.downloadState.collect { updateDownloadState = it }
        }
        viewModelScope.launch {
            updateManager.isRequired.collect { isUpdateRequired = it }
        }
    }

    /**
     * Pull the admin catalog + notes from Supabase. Safe to call from a
     * pull-to-refresh gesture or a Snackbar "Retry" action — concurrent calls
     * are ignored while one is already running.
     */
    fun retrySync() {
        if (isSyncing) return
        viewModelScope.launch {
            runCatalogSync()
        }
    }

    fun dismissSyncError() {
        syncErrorMessage = null
    }

    fun loadContentRequests() {
        if (isLoadingRequests) return
        isLoadingRequests = true
        viewModelScope.launch {
            try {
                contentRequests.value = contentRequestRepository.listRequests()
                if (requestFeedbackIsError) {
                    requestFeedback = null
                    requestFeedbackIsError = false
                }
            } catch (e: Exception) {
                requestFeedback = e.localizedMessage ?: "Could not load requests"
                requestFeedbackIsError = true
            } finally {
                isLoadingRequests = false
            }
        }
    }

    fun submitContentRequest(
        kind: String,
        branchCode: String,
        academicYear: Int,
        title: String,
        details: String,
        onSuccess: () -> Unit
    ) {
        if (isSubmittingRequest) return
        val cleanTitle = title.trim()
        val cleanDetails = details.trim()
        when {
            cleanTitle.length < 2 -> {
                requestFeedback = "Enter a subject or title"
                requestFeedbackIsError = true
                return
            }
            // Caps are not the server's job alone: a phone with a stalled
            // connection should not upload 100 KB of typing by accident.
            cleanTitle.length > REQUEST_TITLE_MAX -> {
                requestFeedback = "Keep the title under $REQUEST_TITLE_MAX characters"
                requestFeedbackIsError = true
                return
            }
            cleanDetails.length > REQUEST_DETAILS_MAX -> {
                requestFeedback = "Keep the details under $REQUEST_DETAILS_MAX characters"
                requestFeedbackIsError = true
                return
            }
        }
        isSubmittingRequest = true
        viewModelScope.launch {
            try {
                val submitted = contentRequestRepository.submitRequest(
                    kind = kind,
                    branchCode = branchCode,
                    academicYear = academicYear,
                    title = cleanTitle,
                    details = cleanDetails
                )
                contentRequests.value = listOf(submitted) + contentRequests.value
                requestFeedback = "Request sent to the PaperAdda team"
                requestFeedbackIsError = false
                onSuccess()
            } catch (e: Exception) {
                requestFeedback = e.localizedMessage ?: "Could not send request"
                requestFeedbackIsError = true
            } finally {
                isSubmittingRequest = false
            }
        }
    }

    fun deleteContentRequest(request: ContentRequest) {
        if (deletingRequestId != null) return
        deletingRequestId = request.id
        viewModelScope.launch {
            try {
                contentRequestRepository.deleteRequest(request.id)
                contentRequests.value = contentRequests.value.filterNot { it.id == request.id }
                requestFeedback = "Request deleted"
                requestFeedbackIsError = false
            } catch (e: Exception) {
                requestFeedback = e.localizedMessage ?: "Could not delete request"
                requestFeedbackIsError = true
            } finally {
                deletingRequestId = null
            }
        }
    }

    fun submitFeedback(
        category: String,
        message: String,
        email: String,
        onSuccess: () -> Unit
    ) {
        if (isSubmittingFeedback) return
        val cleanMessage = message.trim()
        val cleanEmail = email.trim()
        when {
            cleanMessage.length < 10 -> {
                feedbackMessage = "Please write at least 10 characters"
                feedbackMessageIsError = true
                return
            }
            cleanMessage.length > FEEDBACK_MESSAGE_MAX -> {
                feedbackMessage = "Keep your feedback under $FEEDBACK_MESSAGE_MAX characters"
                feedbackMessageIsError = true
                return
            }
            cleanEmail.isNotEmpty() && !isPlausibleEmail(cleanEmail) -> {
                feedbackMessage = "That email address doesn't look right"
                feedbackMessageIsError = true
                return
            }
        }
        isSubmittingFeedback = true
        feedbackMessage = null
        viewModelScope.launch {
            try {
                feedbackRepository.submitFeedback(category, cleanMessage, cleanEmail)
                feedbackMessage = "Thanks — your feedback was sent"
                feedbackMessageIsError = false
                onSuccess()
            } catch (e: Exception) {
                feedbackMessage = e.localizedMessage ?: "Could not send feedback"
                feedbackMessageIsError = true
            } finally {
                isSubmittingFeedback = false
            }
        }
    }

    fun clearFeedbackMessage() {
        feedbackMessage = null
    }

    fun clearRequestFeedback() {
        requestFeedback = null
    }

    private suspend fun runCatalogSync() {
        isSyncing = true
        try {
            repository.loadCache()
            _catalogRevision.value++
            when (val result = repository.refreshFromRemote()) {
                is PaperRepository.CatalogSyncResult.Success -> {
                    _catalogRevision.value++
                    syncErrorMessage = null
                }
                is PaperRepository.CatalogSyncResult.Failed -> {
                    syncErrorMessage =
                        "Couldn't sync the latest papers (${result.message}). Showing offline data."
                }
            }
            if (!noteRepository.refreshFromRemote(remote) && syncErrorMessage == null) {
                syncErrorMessage = "Couldn't sync the latest notes. Showing offline data."
            }
            // After any cache load, so PDFs left behind by a database
            // recreation get their rows back instead of looking undownloaded.
            val recovered = repository.reconcileDownloadedFiles()
            if (recovered.restored > 0) {
                _catalogRevision.value++
            }
        } finally {
            isSyncing = false
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
            is AppScreen.Requests -> NavDestination.REQUESTS
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
        // At a tab root that is not Home (e.g. Requests opened from the
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
                NavDestination.REQUESTS -> AppScreen.Requests
                NavDestination.FEEDBACK -> AppScreen.Feedback
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

    fun downloadQuestionPaper(paper: QuestionPaper) {
        if (downloadingPaperId != null) return
        downloadingPaperId = paper.id
        viewModelScope.launch {
            try {
                val file = repository.downloadPaper(paper)
                downloadMessage = "Downloaded: ${file.name}"
                // Read it in-app straight away — no third-party viewer needed.
                openPdfReader(file, paper.subjectName)
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

    /** The on-disk copy of [paper] if it was already downloaded, else null. */
    fun downloadedFileFor(paper: QuestionPaper): File? = repository.cachedFileFor(paper)

    /** Opens the built-in reader for a file already on disk. */
    fun openPdfReader(file: File, title: String) {
        viewModelScope.launch {
            try {
                backStack.add(AppScreen.PdfReader(file.absolutePath, title))
                navDirection = NavigationDirection.FORWARD
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Could not open the PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Downloads the paper if needed and opens it in the built-in reader.
     * Used by the "Open" action on a paper that has no local copy yet.
     */
    fun openPaperInReader(paper: QuestionPaper) {
        if (downloadingPaperId != null) return
        val cached = repository.cachedFileFor(paper)
        if (cached != null && cached.exists()) {
            openPdfReader(cached, paper.subjectName)
            return
        }
        downloadingPaperId = paper.id
        viewModelScope.launch {
            try {
                val file = repository.downloadPaper(paper)
                openPdfReader(file, paper.subjectName)
            } catch (e: Exception) {
                downloadMessage = "Could not open: ${e.localizedMessage}"
                Toast.makeText(
                    getApplication(),
                    "Failed to open: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                downloadingPaperId = null
            }
        }
    }

    /** Copies a downloaded PDF into the public Downloads folder. */
    suspend fun saveToPublicDownloads(file: File): String =
        when (val result = fileExporter.saveToDownloads(file)) {
            is FileExporter.Result.Saved -> "Saved to ${result.location}"
            is FileExporter.Result.Failed -> result.message
        }

    /** Opens the system share sheet for a downloaded PDF. */
    suspend fun sharePdf(file: File): String = try {
        val send = fileExporter.shareIntent(file)
            ?: return "This file cannot be shared from its current location"
        val chooser = Intent.createChooser(send, "Share PDF")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(chooser)
        "Sharing ${file.name}"
    } catch (e: Exception) {
        "No app available to share with"
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
                    // The note id is part of the name: two notes can share a
                    // title, and a shared name would overwrite the earlier PDF.
                    val safeName = "${note.title
                        .trim()
                        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                        .trim('_', '.', '-')
                        .ifBlank { "note" }
                        .take(60)}_${note.id}.pdf"
                    val file = File(downloadDir, safeName)
                    // Catalog text is admin-influenced — keep the file inside downloadDir.
                    require(file.canonicalPath == downloadDir.canonicalPath + File.separator + file.name) {
                        "Unsafe note file name rejected"
                    }
                    remote.downloadPublicFile(note.storagePath, file)
                    if (!isPdfContent(file)) {
                        file.delete()
                        Toast.makeText(
                            getApplication(),
                            "That note is not a valid PDF. Please report it.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    openPdfReader(file, note.subjectName.ifBlank { note.title })
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
        // Legacy absolute URL — hand off to the system viewer / browser,
        // but only for https links on our own Supabase host (see
        // isAllowedLegacyNoteUrl). Anything else is blocked with a toast
        // instead of being dispatched to an external app.
        if (!isAllowedLegacyNoteUrl(note.fileUrl)) {
            Toast.makeText(
                getApplication(),
                "This link looks unsafe and was blocked",
                Toast.LENGTH_LONG
            ).show()
            return
        }
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
