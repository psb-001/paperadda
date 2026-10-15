package com.paperadda.app.data.repository

import android.content.Context
import android.os.Environment
import com.paperadda.app.data.local.DownloadedPaperEntity
import com.paperadda.app.data.local.PaperDao
import com.paperadda.app.data.model.QuestionPaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Re-inserts a row for every downloaded PDF that is still on disk but is
 * missing from `downloaded_papers`.
 *
 * This is what makes [com.example.data.local.AppDatabase]'s destructive
 * fallback survivable: the PDFs live in the app's external files directory and
 * survive a database wipe, so the index can always be rebuilt from them.
 * Inserts use `OnConflictStrategy.IGNORE`, so running this on every sync is
 * safe and never rewrites an existing row's timestamp.
 *
 * Files are matched to the catalog by rebuilding each paper's file name with
 * the same [downloadFileName] the writer uses, rather than by splitting the
 * name apart: paper slugs contain underscores themselves, so parsing a name
 * back into pieces is guesswork, but comparing it to every known name is not.
 */
class DownloadedPaperReconciler(
    private val paperDao: PaperDao
) {
    suspend fun reconcile(downloadDir: File, papers: List<QuestionPaper>): ReconcileResult =
        withContext(Dispatchers.IO) {
            val files = downloadDir.listFiles { candidate ->
                candidate.isFile && candidate.name.endsWith(".pdf", ignoreCase = true)
            }?.sortedBy { it.name } ?: emptyList()

            // Built once per sync: both the current and the legacy name of every
            // paper, so a file saved by an older build still finds its paper.
            val known: Map<String, QuestionPaper> = buildMap {
                for (paper in papers) {
                    put(downloadFileName(paper).lowercase(), paper)
                    put(legacyDownloadFileName(paper).lowercase(), paper)
                }
            }

            var restored = 0
            for (file in files) {
                val paper = known[file.name.lowercase()]
                val entity = paper?.let {
                    DownloadedPaperEntity(
                        paperId = it.id,
                        title = it.title,
                        subjectName = it.subjectName,
                        branchCode = it.branchCode,
                        year = it.year,
                        filePath = file.absolutePath,
                        fileSize = it.fileSize
                    )
                } ?: unknownFileEntity(file) ?: continue

                if (paperDao.insertDownloadedPaperIfAbsent(entity) != -1L) restored++
            }
            ReconcileResult(scanned = files.size, restored = restored)
        }

    /** The download directory the app writes papers into. */
    fun downloadDir(context: Context): File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
}

/**
 * A row for a PDF that no published paper claims. Still worth showing: the
 * student can open it, and it is no longer invisible in the app's own list.
 */
private fun unknownFileEntity(file: File): DownloadedPaperEntity? {
    val stem = file.name.removeSuffix(".pdf").removeSuffix(".PDF")
    val readable = stem.replace('_', ' ').trim().ifBlank { return null }
    return DownloadedPaperEntity(
        paperId = "recovered:${file.name}",
        title = readable,
        subjectName = readable,
        branchCode = "",
        year = "",
        filePath = file.absolutePath,
        fileSize = ""
    )
}

data class ReconcileResult(val scanned: Int, val restored: Int)
