package com.example.data.repository

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.local.DownloadedPaperEntity
import com.example.data.local.PaperCatalogDao
import com.example.data.local.PaperDao
import com.example.data.local.PaperEntity
import com.example.data.local.SavedPaperEntity
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
import java.io.FileOutputStream

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

    // Empty until the admin catalog syncs from Supabase — no bundled seed data.
    private val fallbackSubjects: List<Subject> = emptyList()
    private val fallbackPapers: List<QuestionPaper> = emptyList()

    // ---- Synced catalog (Room cache, refreshed from Supabase) ----
    // Screens call the getters below on the main thread, so they read a
    // pre-loaded in-memory copy. [loadCache] + [refreshFromRemote] run on
    // background threads from the ViewModel and report via catalogRevision.
    @Volatile
    private var cachedSubjects: List<Subject>? = null

    @Volatile
    private var cachedPapers: List<QuestionPaper>? = null

    private fun activeSubjects(): List<Subject> = cachedSubjects ?: fallbackSubjects
    private fun activePapers(): List<QuestionPaper> = cachedPapers ?: fallbackPapers

    /** Load the offline cache into memory. Returns true if cache was non-empty. */
    suspend fun loadCache(): Boolean = withContext(Dispatchers.IO) {
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
        // Always mirror the DB into memory (including empty) so a wiped
        // cloud catalog doesn't leave a stale in-memory list behind.
        cachedSubjects = subjects
        cachedPapers = papers
        subjects.isNotEmpty() || papers.isNotEmpty()
    }

    /**
     * Pull the admin-published catalog and mirror it into the offline cache
     * (full replace, so admin deletions propagate too). Returns true on
     * success, false when offline — caller keeps the old cache.
     */
    suspend fun refreshFromRemote(): Boolean = withContext(Dispatchers.IO) {
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
            true
        } catch (e: Exception) {
            SupabaseRemoteDataSource.logSyncError(e)
            false
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

    fun getPapersForSubject(subjectName: String, branchCode: String): List<QuestionPaper> {
        return activePapers().filter {
            it.subjectName.equals(subjectName, ignoreCase = true) &&
                (
                    it.branchCode.equals(branchCode, ignoreCase = true) ||
                        // First-year subjects are COMMON; accept them no matter
                        // which branch the navigation route carried.
                        it.branchCode.equals(BRANCH_COMMON, ignoreCase = true)
                    )
        }
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
    val savedPapers: Flow<List<SavedPaperEntity>> = paperDao.getAllSavedPapers()
    val downloadedPapers: Flow<List<DownloadedPaperEntity>> = paperDao.getAllDownloadedPapers()

    fun isPaperSaved(paperId: String): Flow<Boolean> = paperDao.isPaperSavedFlow(paperId)
    fun isPaperDownloaded(paperId: String): Flow<Boolean> = paperDao.isPaperDownloadedFlow(paperId)

    suspend fun toggleBookmark(paper: QuestionPaper) = withContext(Dispatchers.IO) {
        val exists = paperDao.isPaperSaved(paper.id)
        if (exists) {
            paperDao.deleteSavedPaper(paper.id)
        } else {
            paperDao.insertSavedPaper(
                SavedPaperEntity(
                    paperId = paper.id,
                    title = paper.title,
                    subjectName = paper.subjectName,
                    branchCode = paper.branchCode,
                    year = paper.year,
                    fileFormat = paper.fileFormat
                )
            )
        }
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

        // No admin file yet: generate genuine PDF document
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 dimensions
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = AndroidColor.rgb(11, 87, 208) // Primary Blue
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subPaint = Paint().apply {
            color = AndroidColor.rgb(68, 71, 78) // onSurfaceVariant
            textSize = 12f
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = AndroidColor.rgb(27, 27, 31) // onSurface
            textSize = 11f
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = AndroidColor.rgb(196, 198, 208) // outlineVariant
            strokeWidth = 1f
        }

        var yPos = 50f

        // University Header
        canvas.drawText("TECHNICAL UNIVERSITY EXAMINATION BOARD", 40f, yPos, titlePaint)
        yPos += 24f
        canvas.drawText("${paper.examType} - Academic Session ${paper.year}", 40f, yPos, subPaint)
        yPos += 20f
        canvas.drawLine(40f, yPos, 555f, yPos, linePaint)
        yPos += 25f

        // Paper Metadata
        val branchLabel = if (paper.branchCode.equals(BRANCH_COMMON, ignoreCase = true)) {
            "All branches"
        } else {
            paper.branchCode
        }
        canvas.drawText("Course / Subject: ${paper.subjectName}", 40f, yPos, titlePaint)
        yPos += 18f
        canvas.drawText("Branch: $branchLabel | Time: ${paper.duration} | Max Marks: ${paper.maxMarks}", 40f, yPos, subPaint)
        yPos += 20f
        canvas.drawLine(40f, yPos, 555f, yPos, linePaint)
        yPos += 25f

        // Instructions
        val boldBody = Paint(bodyPaint).apply { isFakeBoldText = true }
        canvas.drawText("INSTRUCTIONS TO CANDIDATES:", 40f, yPos, boldBody)
        yPos += 18f
        canvas.drawText("1. Answer any FIVE full questions choosing at least TWO from each section.", 40f, yPos, bodyPaint)
        yPos += 16f
        canvas.drawText("2. Figures to the right indicate full marks. Assume suitable data if necessary.", 40f, yPos, bodyPaint)
        yPos += 22f
        canvas.drawLine(40f, yPos, 555f, yPos, linePaint)
        yPos += 30f

        // Questions
        canvas.drawText("EXAMINATION QUESTIONS:", 40f, yPos, boldBody)
        yPos += 22f

        val questions = if (paper.sampleQuestions.isNotEmpty()) {
            paper.sampleQuestions
        } else {
            listOf(
                "Q1. (a) Discuss the theoretical foundations and core principles governing ${paper.subjectName}. [10 Marks]",
                "Q1. (b) Derive the mathematical formulation and show step-by-step proofs for standard test inputs. [10 Marks]",
                "Q2. (a) Compare classical implementations with modern advanced architectures in ${paper.branchCode}. [10 Marks]",
                "Q2. (b) Explain design considerations, performance bottlenecks, and mitigation strategies. [10 Marks]",
                "Q3. (a) Solve the practical engineering design problem using appropriate domain parameters. [10 Marks]",
                "Q3. (b) Illustrate with circuit / timing / architecture diagrams and describe functional behavior. [10 Marks]"
            )
        }

        for (q in questions) {
            val words = q.split(" ")
            var line = ""
            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                if (bodyPaint.measureText(testLine) > 500f) {
                    canvas.drawText(line, 40f, yPos, bodyPaint)
                    yPos += 16f
                    line = "    $word"
                } else {
                    line = testLine
                }
            }
            if (line.isNotEmpty()) {
                canvas.drawText(line, 40f, yPos, bodyPaint)
                yPos += 20f
            }
        }

        yPos += 20f
        canvas.drawLine(40f, yPos, 555f, yPos, linePaint)
        yPos += 20f
        canvas.drawText("--- End of Question Paper ---", 220f, yPos, subPaint)

        pdfDocument.finishPage(page)

        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

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

        file
    }

    fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
