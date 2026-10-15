package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.local.DownloadedPaperEntity
import com.example.data.local.PaperCatalogDao
import com.example.data.local.PaperDao
import com.example.data.local.PaperEntity
import com.example.data.local.SubjectCatalogDao
import com.example.data.local.SubjectEntity
import com.example.data.model.BRANCH_COMMON
import com.example.data.model.Branch
import com.example.data.model.QuestionPaper
import com.example.data.model.Subject
import com.example.data.remote.SupabaseRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Build the download [File] for a paper, confined to [downloadDir].
 * Catalog text is admin-influenced, so every filename component is
 * allowlisted (letters, digits, dot, underscore, hyphen; spaces become
 * underscores; everything else collapses) and the resolved file must stay
 * inside the download directory (canonical-path confinement).
 * Throws IllegalArgumentException if the resolved path escapes.
 * Top-level + internal so regression tests can call it without Android.
 */
internal fun buildConfinedDownloadFile(
    downloadDir: File,
    branchCode: String,
    subjectName: String,
    year: String
): File {
    fun safe(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_', '.', '-')
        return cleaned.ifBlank { "item" }.take(80)
    }
    val fileName = "${safe(branchCode)}_${safe(subjectName)}_${safe(year)}.pdf"
    val file = File(downloadDir, fileName)
    require(file.canonicalPath == downloadDir.canonicalPath + File.separator + file.name) {
        "Unsafe paper file name rejected"
    }
    return file
}

class PaperRepository(
    private val paperDao: PaperDao,
    private val subjectCatalogDao: SubjectCatalogDao,
    private val paperCatalogDao: PaperCatalogDao,
    private val remote: SupabaseRemoteDataSource,
    private val context: Context
) {
    val branches: List<Branch> = listOf(
        Branch(
            code = "ENTC",
            fullName = "Electronics and Telecommunication Engineering",
            iconName = "memory"
        ),
        Branch(
            code = "AIML",
            fullName = "Artificial Intelligence and Machine Learning",
            iconName = "smart_toy"
        ),
        Branch(
            code = "CE",
            fullName = "Computer Engineering",
            iconName = "computer"
        ),
        Branch(
            code = "IT",
            fullName = "Information Technology",
            iconName = "language"
        )
    )

    // ---- Synced catalog (Room cache, refreshed from Supabase) ----
    // Screens call the getters below on the main thread, so they read a
    // pre-loaded in-memory copy. [loadCache] + [refreshFromRemote] run on
    // background threads from the ViewModel and report via catalogRevision.
    @Volatile
    private var cachedSubjects: List<Subject> = emptyList()

    @Volatile
    private var cachedPapers: List<QuestionPaper> = emptyList()

    private fun activePapers(): List<QuestionPaper> = cachedPapers

    private fun activeSubjects(): List<Subject> =
        withActualPaperCounts(cachedSubjects, activePapers())

    /**
     * Displayed counts must equal tappable papers: derive [Subject.paperCount]
     * from the actual paper rows (mirroring [getPapersForSubject]) instead of
     * trusting the portal's manual `paper_count` field, which drifts when an
     * admin edits it by hand. Idempotent — safe to apply on every read.
     */
    private fun withActualPaperCounts(
        subjects: List<Subject>,
        papers: List<QuestionPaper>
    ): List<Subject> {
        if (subjects.isEmpty()) return subjects
        // Index the papers once instead of re-scanning the whole list for every
        // subject, and hand back the original list when nothing changed so
        // repeated reads stop allocating.
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
            if (actual == s.paperCount) {
                out.add(s)
            } else {
                out.add(s.copy(paperCount = actual))
                changed = true
            }
        }
        return if (changed) out else subjects
    }

    /** Case-insensitive key for the subject/branch pair used by paper counts. */
private fun countKey(subjectName: String, branchCode: String): String =
    subjectName.lowercase() + "\u0000" + branchCode.lowercase()

/** Load the offline cache into memory. */
    suspend fun loadCache() = withContext(Dispatchers.IO) {
        val subjects = subjectCatalogDao.getAll().map {
            Subject(it.id, it.name, it.branchCode, it.academicYear, it.paperCount, it.iconName)
        }
        val papers = paperCatalogDao.getAll().map {
            QuestionPaper(
                id = it.id, title = it.title, subjectName = it.subjectName,
                branchCode = it.branchCode, year = it.year, examType = it.examType,
                fileFormat = it.fileFormat, fileSize = it.fileSize, duration = it.duration,
                maxMarks = it.maxMarks, sampleQuestions = it.sampleQuestions,
                storagePath = it.storagePath
            )
        }
        cachedSubjects = subjects
        cachedPapers = papers
    }

    /** Result of a catalog sync — callers surface [Failed] in the UI. */
    sealed interface CatalogSyncResult {
        data object Success : CatalogSyncResult
        data class Failed(val message: String) : CatalogSyncResult
    }

    /**
     * Pull the admin-published catalog and mirror it into the offline cache
     * (full replace, so admin deletions propagate too). Returns [Success] on
     * success, [Failed] with a human-readable reason when offline — caller
     * keeps the old cache and must show the failure (no more silent aside
     * from the log line below).
     */
    suspend fun refreshFromRemote(): CatalogSyncResult = withContext(Dispatchers.IO) {
        try {
            val subjects = remote.fetchSubjects()
            val papers = remote.fetchPapers()
            subjectCatalogDao.replaceAll(subjects.map {
                SubjectEntity(it.id, it.name, it.branchCode, it.academicYear, it.paperCount, it.iconName)
            })
            paperCatalogDao.replaceAll(papers.map {
                PaperEntity(
                    id = it.id, title = it.title, subjectName = it.subjectName,
                    branchCode = it.branchCode, year = it.year, examType = it.examType,
                    fileFormat = it.fileFormat, fileSize = it.fileSize, duration = it.duration,
                    maxMarks = it.maxMarks, sampleQuestions = it.sampleQuestions,
                    storagePath = it.storagePath
                )
            })
            loadCache()
            CatalogSyncResult.Success
        } catch (e: Exception) {
            SupabaseRemoteDataSource.logSyncError(e)
            CatalogSyncResult.Failed(e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: e.javaClass.simpleName)
        }
    }

    fun getBranch(branchCode: String): Branch? {
        return branches.find { it.code.equals(branchCode, ignoreCase = true) }
    }

    fun getSubjectsForBranch(branchCode: String): List<Subject> {
        // First year is COMMON; branch screens only list years 2–4, so a
        // plain branch lookup never needs to expand into COMMON rows.
        return activeSubjects().filter {
            it.branchCode.equals(branchCode, ignoreCase = true) &&
                !it.branchCode.equals(BRANCH_COMMON, ignoreCase = true)
        }
    }

    fun getSubjectsForBranchAndYear(branchCode: String, academicYear: Int): List<Subject> {
        return activeSubjects().filter { s ->
            s.academicYear == academicYear && (
                // Year 1 content is shared: match COMMON regardless of the
                // branch the caller happened to carry (safety net for search,
                // saved papers, deep links, and the dedicated First Year card).
                academicYear == 1 ||
                    s.branchCode.equals(branchCode, ignoreCase = true)
                )
        }
    }

    /**
     * Newest year first, then by the "Paper N" number in the title.
     *
     * The backend only orders by `year.desc`, so papers inside a year came back in
     * an arbitrary order and a list could read 1, 2, 3, 4, 5, 6, 7, 1. Sorting on
     * the parsed number rather than the title keeps the order correct once a
     * subject has ten or more papers in one year, where "Paper 10" would otherwise
     * sort between 1 and 2.
     */
    private val paperDisplayOrder: Comparator<QuestionPaper> =
        compareByDescending<QuestionPaper> { it.year }
            .thenBy { paperNumberIn(it.title) }
            .thenBy { it.title.lowercase() }

    private fun paperNumberIn(title: String): Int =
        Regex("""·\s*Paper\s+(\d+)""", RegexOption.IGNORE_CASE)
            .find(title)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Int.MAX_VALUE

    fun getPapersForSubject(subjectName: String, branchCode: String): List<QuestionPaper> {
        val matched = activePapers().filter {
            it.subjectName.equals(subjectName, ignoreCase = true) &&
                (
                    it.branchCode.equals(branchCode, ignoreCase = true) ||
                        // First-year subjects are COMMON; accept them no matter
                        // which branch the navigation route carried.
                        it.branchCode.equals(BRANCH_COMMON, ignoreCase = true)
                    )
        }
        return matched.sortedWith(paperDisplayOrder)
    }

    fun getPaperById(id: String): QuestionPaper? {
        return activePapers().find { it.id == id }
    }

    fun searchAll(query: String): List<QuestionPaper> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        return activePapers().filter {
            it.title.lowercase().contains(q) ||
            it.subjectName.lowercase().contains(q) ||
            it.branchCode.lowercase().contains(q) ||
            it.year.contains(q)
        }
    }

    // Room operations
    val downloadedPapers: Flow<List<DownloadedPaperEntity>> = paperDao.getAllDownloadedPapers()

    fun isPaperDownloaded(paperId: String): Flow<Boolean> = paperDao.isPaperDownloadedFlow(paperId)

    /**
     * The on-disk copy of [paper] if it has already been downloaded, else null.
     * Lets the app open a paper in its own reader without hitting the network.
     */
    fun cachedFileFor(paper: QuestionPaper): File? = try {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val f = buildConfinedDownloadFile(dir, paper.branchCode, paper.subjectName, paper.year)
        if (f.exists() && f.length() > 0) f else null
    } catch (e: Exception) {
        null
    }

    suspend fun downloadPaper(paper: QuestionPaper): File = withContext(Dispatchers.IO) {
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        // Security: catalog text is admin-influenced (see buildConfinedDownloadFile).
        val file = buildConfinedDownloadFile(
            downloadDir, paper.branchCode, paper.subjectName, paper.year
        )

        // Admin-uploaded PDF on Supabase Storage: download the real file.
        if (paper.storagePath.isNotBlank()) {
            remote.downloadPublicFile(paper.storagePath, file)
            paperDao.insertDownloadedPaper(
                DownloadedPaperEntity(
                    paperId = paper.id,
                    title = paper.title,
                    subjectName = paper.subjectName,
                    branchCode = paper.branchCode,
                    year = paper.year,
                    filePath = file.absolutePath,
                    fileSize = paper.fileSize
                )
            )
            return@withContext file
        }

        throw IOException("No PDF has been uploaded for this paper yet")
    }

    fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
