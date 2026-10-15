package com.paperadda.app.data.repository

import android.content.Context
import android.os.Environment
import com.paperadda.app.data.local.DownloadedPaperEntity
import com.paperadda.app.data.local.PaperDao
import com.paperadda.app.data.model.QuestionPaper
import com.paperadda.app.data.repository.downloadNameToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A download row rebuilt from a PDF that is still on disk but has no row in
 * `downloaded_papers` (after a database recreation, or a failed insert).
 */
internal data class RecoveredDownload(
    val paperId: String,
    val title: String,
    val subjectName: String,
    val branchCode: String,
    val year: String,
    val filePath: String,
    val fileSize: String
)

/** The three pieces of a download filename: `BRANCH_SUBJECT_YEAR.pdf`. */
internal data class ParsedDownloadFileName(
    val branchCode: String,
    val subjectToken: String,
    val yearToken: String
)

/**
 * Parses `BRANCH_SUBJECT_YEAR.pdf` — the name [buildConfinedDownloadFile]
 * produces. Returns null for anything that is not one of our own files.
 *
 * The middle is kept as one underscore-joined token because the sanitiser
 * collapses runs of invalid characters to a single `_`, so the number of
 * original spaces in a subject name cannot be recovered from the name alone.
 * Callers match that token back to catalog text with [matchCatalogPaper].
 */
internal fun parseDownloadedPdfFileName(fileName: String): ParsedDownloadFileName? {
    val base = fileName.trim()
    if (!base.endsWith(".pdf", ignoreCase = true)) return null
    val stem = base.dropLast(4)
    if (stem.isBlank()) return null
    val parts = stem.split("_")
    if (parts.size < 3) return null
    return ParsedDownloadFileName(
        branchCode = parts.first(),
        subjectToken = parts.subList(1, parts.size - 1).joinToString("_"),
        yearToken = parts.last()
    )
}

/**
 * Finds the catalog paper a recovered file belongs to: same sanitised subject
 * name, then the same year when one matches, otherwise the newest year. Pure
 * so the matching rules can be tested without a database.
 */
internal fun matchCatalogPaper(
    papers: List<QuestionPaper>,
    parsed: ParsedDownloadFileName
): QuestionPaper? {
    val subjectMatches = papers.filter {
        downloadNameToken(it.subjectName).equals(parsed.subjectToken, ignoreCase = true)
    }
    if (subjectMatches.isEmpty()) return null
    subjectMatches.firstOrNull {
        it.year.trim().equals(parsed.yearToken, ignoreCase = true)
    }?.let { return it }
    return subjectMatches.maxByOrNull { it.year }
}

/** Turns a file plus its catalog match into the row that should exist. */
internal fun recoveredDownloadFor(
    file: File,
    parsed: ParsedDownloadFileName,
    paper: QuestionPaper?
): RecoveredDownload {
    val subject = paper?.subjectName ?: parsed.subjectToken.replace('_', ' ')
    val year = paper?.year ?: parsed.yearToken
    return RecoveredDownload(
        // Prefer the real catalog id: the row then lines up with the paper in
        // the catalog and every Flow-based UI state is correct again.
        paperId = paper?.id ?: ("recovered:" + file.name),
        title = paper?.title ?: "$subject · $year",
        subjectName = subject,
        branchCode = paper?.branchCode ?: parsed.branchCode,
        year = year,
        filePath = file.absolutePath,
        fileSize = paper?.fileSize ?: ""
    )
}

/**
 * Re-inserts a row for every downloaded PDF that is still on disk but is
 * missing from `downloaded_papers`.
 *
 * This is what makes [com.paperadda.app.data.local.AppDatabase]'s destructive
 * fallback survivable: the PDFs live in the app's external files directory and
 * survive an uninstall-adjacent database wipe, so the index can always be
 * rebuilt from them. Inserts use `OnConflictStrategy.IGNORE`, so running this
 * on every sync is safe and never rewrites an existing row's timestamp.
 */
class DownloadedPaperReconciler(
    private val paperDao: PaperDao
) {
    suspend fun reconcile(downloadDir: File, papers: List<QuestionPaper>): ReconcileResult =
        withContext(Dispatchers.IO) {
            val files = downloadDir.listFiles { candidate ->
                candidate.isFile && candidate.name.endsWith(".pdf", ignoreCase = true)
            }?.sortedBy { it.name } ?: emptyList()

            var restored = 0
            for (file in files) {
                val parsed = parseDownloadedPdfFileName(file.name) ?: continue
                val paper = matchCatalogPaper(papers, parsed)
                val recovered = recoveredDownloadFor(file, parsed, paper)
                val inserted = paperDao.insertDownloadedPaperIfAbsent(
                    DownloadedPaperEntity(
                        paperId = recovered.paperId,
                        title = recovered.title,
                        subjectName = recovered.subjectName,
                        branchCode = recovered.branchCode,
                        year = recovered.year,
                        filePath = recovered.filePath,
                        fileSize = recovered.fileSize
                    )
                )
                if (inserted != -1L) restored++
            }
            ReconcileResult(scanned = files.size, restored = restored)
        }

    /** The download directory the app writes papers into. */
    fun downloadDir(context: Context): File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
}

data class ReconcileResult(val scanned: Int, val restored: Int)
