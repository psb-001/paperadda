package com.paperadda.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.paperadda.app.data.local.AppDatabase
import com.paperadda.app.data.model.QuestionPaper
import com.paperadda.app.data.repository.DownloadedPaperReconciler
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The guarantee behind the destructive-migration fallback: after the database
 * is gone but the PDFs are still on disk, a sync brings the library back
 * without the user re-downloading anything.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadedPaperReconcilerTest {

    private lateinit var db: AppDatabase
    private lateinit var context: android.content.Context
    private lateinit var downloadDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        downloadDir = File(context.cacheDir, "reconciler-downloads").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        downloadDir.deleteRecursively()
    }

    @Test
    fun `restores a row for every PDF on disk that matches the catalog`() = runBlocking {
        writeFile("ENTC_Data_Structures_2023-24.pdf")
        writeFile("ENTC_Thermodynamics_2022-23.pdf")

        val result = DownloadedPaperReconciler(db.paperDao()).reconcile(downloadDir, catalog())

        assertEquals(2, result.scanned)
        assertEquals(2, result.restored)

        val rows = db.paperDao().getAllDownloadedPapers().first()
        assertEquals(2, rows.size)
        val ids = rows.map { it.paperId }.sorted()
        assertEquals(listOf("p1", "p2"), ids)
        rows.forEach { assertTrue(it.filePath.startsWith(downloadDir.absolutePath)) }
    }

    @Test
    fun `unknown PDFs still get a readable row with a namespaced id`() = runBlocking {
        writeFile("ENTC_Legacy_Paper_2019-20.pdf")

        val result = DownloadedPaperReconciler(db.paperDao()).reconcile(downloadDir, emptyList())

        assertEquals(1, result.restored)
        val row = db.paperDao().getAllDownloadedPapers().first().single()
        assertEquals("recovered:ENTC_Legacy_Paper_2019-20.pdf", row.paperId)
        assertEquals("Legacy Paper", row.subjectName)
        assertEquals("2019-20", row.year)
    }

    @Test
    fun `a second reconcile keeps the original rows and timestamps`() = runBlocking {
        writeFile("ENTC_Data_Structures_2023-24.pdf")
        val reconciler = DownloadedPaperReconciler(db.paperDao())

        reconciler.reconcile(downloadDir, catalog())
        val originalTimestamp = db.paperDao().getAllDownloadedPapers().first().single().downloadedAt

        val second = reconciler.reconcile(downloadDir, catalog())

        assertEquals(1, second.scanned)
        assertEquals("an existing row must not be re-inserted", 0, second.restored)
        val rows = db.paperDao().getAllDownloadedPapers().first()
        assertEquals(1, rows.size)
        assertEquals(originalTimestamp, rows.single().downloadedAt)
    }

    @Test
    fun `files that are not PDFs are ignored`() = runBlocking {
        File(downloadDir, "notes.txt").writeText("not a paper")
        File(downloadDir, "thumb.png").writeBytes(byteArrayOf(1, 2, 3))

        val result = DownloadedPaperReconciler(db.paperDao()).reconcile(downloadDir, emptyList())

        assertEquals(0, result.scanned)
        assertEquals(0, result.restored)
        assertTrue(db.paperDao().getAllDownloadedPapers().first().isEmpty())
    }

    private fun writeFile(name: String) {
        File(downloadDir, name).writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46)) // "%PDF"
    }

    private suspend fun catalog(): List<QuestionPaper> = listOf(
        paper("p1", "Data Structures", "ENTC", "2023-24"),
        paper("p2", "Thermodynamics", "ENTC", "2022-23")
    )

    private fun paper(
        id: String,
        subject: String,
        branch: String,
        year: String
    ) = QuestionPaper(
        id = id,
        title = "$subject · Paper 1",
        subjectName = subject,
        branchCode = branch,
        year = year,
        examType = "End Semester Examination",
        fileFormat = "PDF",
        fileSize = "1.2 MB",
        duration = "3 Hours",
        maxMarks = 100,
        sampleQuestions = emptyList(),
        storagePath = "$branch/$subject/$year.pdf"
    )
}
