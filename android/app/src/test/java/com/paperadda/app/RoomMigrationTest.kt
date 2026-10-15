package com.paperadda.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.paperadda.app.data.local.AppDatabase
import com.paperadda.app.data.local.Migrations
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
 * Proves the 7 → 8 migration is real and lossless.
 *
 * The database is created as version 7 with the exact DDL Room expects for
 * that version, seeded with rows, and then opened through Room with only the
 * app's migrations available. Room re-validates the schema after running the
 * migration, so a wrong index name or a dropped column fails this test rather
 * than a user's phone.
 */
@RunWith(RobolectricTestRunner::class)
class RoomMigrationTest {

    private val dbName = "room-migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `7 to 8 keeps every row and creates the new indices`() = runBlocking {
        createVersion7Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL.toTypedArray())
            .build()

        val downloaded = db.paperDao().getAllDownloadedPapers().first()
        assertEquals(1, downloaded.size)
        assertEquals("paper-1", downloaded.first().paperId)
        assertEquals("ENTC_Data-Structures_2023-24.pdf", downloaded.first().title)
        assertEquals(1_700_000_000_000L, downloaded.first().downloadedAt)

        val saved = db.paperDao().getAllSavedPapers().first()
        assertEquals(1, saved.size)
        assertEquals("paper-9", saved.first().paperId)

        assertEquals(1, db.noteDao().count())
        assertEquals(1, db.subjectCatalogDao().getAll().size)
        assertEquals(1, db.paperCatalogDao().getAll().size)

        val indices = indexNames(db.openHelper.writableDatabase, "downloaded_papers")
        assertTrue(
            "downloaded_papers index missing after migration: $indices",
            indices.contains("index_downloaded_papers_downloadedAt")
        )
        val savedIndices = indexNames(db.openHelper.writableDatabase, "saved_papers")
        assertTrue(
            "saved_papers index missing after migration: $savedIndices",
            savedIndices.contains("index_saved_papers_savedAt")
        )

        db.close()
    }

    /**
     * A brand-new database must still open and work — the migration path is
     * only half the contract; onCreate has to keep behaving.
     */
    @Test
    fun `fresh database opens at the current version`() = runBlocking {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL.toTypedArray())
            .build()

        assertEquals(0, db.noteDao().count())
        val helper = db.openHelper.writableDatabase
        val dump = sqliteMasterDump(helper)
        val indices = indexNames(helper, "downloaded_papers")
        assertTrue(
            "fresh database should already have the index: $indices\n$dump",
            indices.contains("index_downloaded_papers_downloadedAt")
        )
        db.close()
    }

    /** Builds the database exactly as version 7 of the app left it. */
    private fun createVersion7Database() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_papers` (`paperId` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `subjectName` TEXT NOT NULL, `branchCode` TEXT NOT NULL, " +
                    "`year` TEXT NOT NULL, `fileFormat` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`paperId`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `downloaded_papers` (`paperId` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `subjectName` TEXT NOT NULL, `branchCode` TEXT NOT NULL, " +
                    "`year` TEXT NOT NULL, `filePath` TEXT NOT NULL, `fileSize` TEXT NOT NULL, " +
                    "`downloadedAt` INTEGER NOT NULL, PRIMARY KEY(`paperId`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `content` TEXT NOT NULL, `subjectName` TEXT NOT NULL, " +
                    "`branchCode` TEXT NOT NULL, `academicYear` INTEGER NOT NULL, `fileUrl` TEXT NOT NULL, " +
                    "`storagePath` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `subjects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`branchCode` TEXT NOT NULL, `academicYear` INTEGER NOT NULL, `paperCount` INTEGER NOT NULL, " +
                    "`iconName` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `papers` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`subjectName` TEXT NOT NULL, `branchCode` TEXT NOT NULL, `year` TEXT NOT NULL, " +
                    "`examType` TEXT NOT NULL, `fileFormat` TEXT NOT NULL, `fileSize` TEXT NOT NULL, " +
                    "`duration` TEXT NOT NULL, `maxMarks` INTEGER NOT NULL, `sampleQuestions` TEXT NOT NULL, " +
                    "`storagePath` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )

            db.execSQL(
                "INSERT OR REPLACE INTO saved_papers VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    "paper-9", "Saved Paper", "Data Structures", "ENTC", "2023-24", "PDF",
                    1_700_000_100_000L
                )
            )
            db.execSQL(
                "INSERT OR REPLACE INTO downloaded_papers VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    "paper-1", "ENTC_Data-Structures_2023-24.pdf", "Data Structures", "ENTC",
                    "2023-24", "/files/Download/ENTC_Data-Structures_2023-24.pdf", "1.2 MB",
                    1_700_000_000_000L
                )
            )
            db.execSQL(
                "INSERT OR REPLACE INTO notes (id, title, content, subjectName, branchCode, " +
                    "academicYear, fileUrl, storagePath, updatedAt) VALUES " +
                    "(1, 'Unit notes', '', 'Data Structures', 'ENTC', 2, '', 'notes/1.pdf', 1700000200000)"
            )
            db.execSQL(
                "INSERT OR REPLACE INTO subjects VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>("s-1", "Data Structures", "ENTC", 2, 3, "graphic_eq")
            )
            db.execSQL(
                "INSERT OR REPLACE INTO papers VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    "paper-1", "Data Structures · Paper 1", "Data Structures", "ENTC", "2023-24",
                    "End Semester Examination", "PDF", "1.2 MB", "3 Hours", 100, "", "entc/ds/1.pdf"
                )
            )

            // A stale identity hash is what makes Room decide a migration is
            // pending — the same signal a real device sends.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf<Any>("stale-hash-from-version-7")
            )
            db.version = 7
        }
    }

    private fun indexNames(db: SupportSQLiteDatabase, table: String): List<String> {
        // Filtered in Kotlin rather than with a `?` bind argument: the
        // SupportSQLiteDatabase.bind path is not exercised reliably here.
        val result = mutableListOf<String>()
        db.query("SELECT tbl_name, name FROM sqlite_master WHERE type = 'index'").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(0) == table) result += cursor.getString(1)
            }
        }
        return result
    }

    /** Everything in sqlite_master, for failure messages. */
    private fun sqliteMasterDump(db: SupportSQLiteDatabase): String =
        buildString {
            append("sqlite_master:\n")
            db.query("SELECT type, name, tbl_name FROM sqlite_master").use { cursor ->
                while (cursor.moveToNext()) {
                    append("  [").append(cursor.getString(0)).append("] ")
                        .append(cursor.getString(1)).append(" on ").append(cursor.getString(2))
                        .append('\n')
                }
            }
        }
}
