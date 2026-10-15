package com.paperadda.app.data.remote

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only client for the Supabase backend (plain HTTPS + org.json,
 * no extra dependencies). All calls throw [SupabaseException] on failure —
 * callers catch and fall back to the offline Room cache.
 *
 * Reads are paged with `Range` headers. PostgREST caps a response at the
 * project's `max_rows` (1000 unless changed), and an unpaged `select=*`
 * silently returns only the first page — the app would show a complete-looking
 * catalog that is quietly missing every paper after the cap. Paging also keeps
 * peak memory bounded on a phone.
 */
class SupabaseRemoteDataSource {

    data class RemoteSubject(
        val id: String,
        val name: String,
        val branchCode: String,
        val academicYear: Int,
        val paperCount: Int,
        val iconName: String
    )

    data class RemotePaper(
        val id: String,
        val title: String,
        val subjectName: String,
        val branchCode: String,
        val year: String,
        val examType: String,
        val fileFormat: String,
        val fileSize: String,
        val duration: String,
        val maxMarks: Int,
        val sampleQuestions: List<String>,
        val storagePath: String
    )

    data class RemoteNote(
        val id: Long?,
        val title: String,
        val content: String,
        val subjectName: String,
        val branchCode: String,
        val academicYear: Int,
        val fileUrl: String,
        val storagePath: String
    )

    fun fetchSubjects(): List<RemoteSubject> = fetchTable(
        table = "subjects",
        order = stableOrderClause("id.asc")
    ) { json ->
        RemoteSubject(
            id = json.requiredString("id"),
            name = json.requiredString("name"),
            branchCode = json.requiredString("branch_code"),
            academicYear = json.optInt("academic_year", 1),
            paperCount = json.optInt("paper_count", 0),
            iconName = json.optString("icon_name", "graphic_eq")
        )
    }

    fun fetchPapers(): List<RemotePaper> = fetchTable(
        table = "papers",
        order = stableOrderClause("year.desc")
    ) { json ->
        val samples = mutableListOf<String>()
        val arr = json.optJSONArray("sample_questions")
        if (arr != null) {
            for (i in 0 until arr.length()) samples.add(arr.optString(i))
        }
        RemotePaper(
            id = json.requiredString("id"),
            title = json.requiredString("title"),
            subjectName = json.requiredString("subject_name"),
            branchCode = json.requiredString("branch_code"),
            year = json.requiredString("year"),
            examType = json.optString("exam_type", "End Semester Examination"),
            fileFormat = json.optString("file_format", "PDF"),
            fileSize = json.optString("file_size", ""),
            duration = json.optString("duration", "3 Hours"),
            maxMarks = json.optInt("max_marks", 100),
            sampleQuestions = samples,
            storagePath = json.optString("storage_path", "")
        )
    }

    fun fetchNotes(): List<RemoteNote> = fetchTable(
        table = "notes",
        order = stableOrderClause("updated_at.desc")
    ) { json ->
        RemoteNote(
            id = if (json.isNull("id")) null else json.optLong("id"),
            title = json.requiredString("title"),
            content = json.optString("content", ""),
            subjectName = json.optString("subject_name", ""),
            branchCode = json.optString("branch_code", ""),
            academicYear = json.optInt("academic_year", 1),
            fileUrl = json.optString("file_url", ""),
            storagePath = json.optString("storage_path", "")
        )
    }

    /**
     * Reads a whole table, page by page, applying [map] to every row.
     * Transient failures (offline, 5xx, 429) are retried with backoff.
     */
    private fun <T> fetchTable(
        table: String,
        order: String,
        map: (JSONObject) -> T
    ): List<T> = HttpSupport.withRetry {
        val out = ArrayList<T>(HttpSupport.PAGE_SIZE)
        var offset = 0
        while (offset / HttpSupport.PAGE_SIZE < HttpSupport.MAX_PAGES) {
            val page = fetchPage(table, order, offset, map)
            out.addAll(page)
            if (HttpSupport.isLastPage(page.size)) break
            offset += HttpSupport.PAGE_SIZE
        }
        out
    }

    private fun <T> fetchPage(
        table: String,
        order: String,
        offset: Int,
        map: (JSONObject) -> T
    ): List<T> {
        val url = URL(
            "${SupabaseConfig.URL}/rest/v1/$table?select=*&order=$order"
        )
        val connection = HttpSupport.get(
            url = url,
            apiKey = SupabaseConfig.ANON_KEY,
            range = HttpSupport.rangeHeader(offset)
        )
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw failure(status, HttpSupport.readBody(connection))
            }
            val array = JSONArray(HttpSupport.readBody(connection))
            return (0 until array.length()).map { map(array.getJSONObject(it)) }
        } catch (e: Exception) {
            throw HttpSupport.failureFor(e)
        } finally {
            connection.disconnect()
        }
    }

    /** Download a file from the public `papers` bucket into [dest]. */
    fun downloadPublicFile(storagePath: String, dest: File) = HttpSupport.withRetry {
        val url = URL("${SupabaseConfig.URL}/storage/v1/object/public/papers/$storagePath")
        val connection = HttpSupport.get(
            url = url,
            apiKey = SupabaseConfig.ANON_KEY,
            // Storage authorises the bucket by anon key alone; sending a bearer
            // token here would only confuse it.
            useAuthHeader = false
        )
        connection.connectTimeout = HttpSupport.DOWNLOAD_CONNECT_TIMEOUT_MS
        connection.readTimeout = HttpSupport.DOWNLOAD_READ_TIMEOUT_MS
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw failure(status, HttpSupport.readBody(connection))
            }
            FileOutputStream(dest).use { output ->
                HttpSupport.copyTo(connection, output)
            }
        } catch (e: Exception) {
            throw HttpSupport.failureFor(e)
        } finally {
            connection.disconnect()
        }
    }

    fun publicFileUrl(storagePath: String): String =
        "${SupabaseConfig.URL}/storage/v1/object/public/papers/$storagePath"

    private fun failure(status: Int, body: String): SupabaseException {
        val kind = HttpSupport.classifyStatus(status)
        val detail = HttpSupport.summarizeErrorBody(body)
        val message = when {
            detail.isEmpty() && kind == SyncFailure.OFFLINE ->
                "Couldn't reach PaperAdda's library. Check your connection."
            detail.isEmpty() -> "PaperAdda's library is unavailable (HTTP $status)."
            else -> "PaperAdda's library is unavailable (HTTP $status): $detail"
        }
        return SupabaseException(kind, message)
    }

    companion object {
        private const val TAG = "SupabaseSync"

        /** A missing id/name is a data problem, not something to guess around. */
        private fun JSONObject.requiredString(key: String): String {
            val value = optString(key)
            if (value.isBlank()) {
                throw SupabaseException(
                    SyncFailure.MALFORMED,
                    "The library returned an incomplete record (missing \"$key\")."
                )
            }
            return value
        }

        fun logSyncError(e: Exception) {
            val kind = (e as? SupabaseException)?.failure?.name ?: "other"
            Log.w(TAG, "Remote sync unavailable ($kind), using offline cache: ${e.message}")
        }
    }
}

/**
 * Order clause for a paged read.
 *
 * The sort key must be unique. `ORDER BY` on a column with ties is not stable
 * across separate requests, so paging by one repeats some rows and skips
 * others — measured against the live backend: 75 papers paged by `year.desc`
 * at 30 rows a page came back as 70 distinct ids, with 5 papers missing.
 * `id` is unique per row, so it is appended as the tiebreaker.
 * Top-level + internal so the rule is unit-tested without a network.
 */
internal fun stableOrderClause(primary: String): String = "$primary,id.asc"
