package com.example.data.remote

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray

/**
 * Read-only client for the Supabase backend (plain HTTPS + org.json,
 * no extra dependencies). All calls throw on failure — callers catch
 * and fall back to the offline Room cache.
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

    fun fetchSubjects(): List<RemoteSubject> {
        return getArray("subjects", "id.asc").map { o ->
            val j = o as org.json.JSONObject
            RemoteSubject(
                id = j.getString("id"),
                name = j.getString("name"),
                branchCode = j.getString("branch_code"),
                academicYear = j.optInt("academic_year", 1),
                paperCount = j.optInt("paper_count", 0),
                iconName = j.optString("icon_name", "graphic_eq")
            )
        }
    }

    fun fetchPapers(): List<RemotePaper> {
        return getArray("papers", "year.desc").map { o ->
            val j = o as org.json.JSONObject
            val samples = mutableListOf<String>()
            val arr = j.optJSONArray("sample_questions")
            if (arr != null) {
                for (i in 0 until arr.length()) samples.add(arr.optString(i))
            }
            RemotePaper(
                id = j.getString("id"),
                title = j.getString("title"),
                subjectName = j.getString("subject_name"),
                branchCode = j.getString("branch_code"),
                year = j.getString("year"),
                examType = j.optString("exam_type", "End Semester Examination"),
                fileFormat = j.optString("file_format", "PDF"),
                fileSize = j.optString("file_size", ""),
                duration = j.optString("duration", "3 Hours"),
                maxMarks = j.optInt("max_marks", 100),
                sampleQuestions = samples,
                storagePath = j.optString("storage_path", "")
            )
        }
    }

    fun fetchNotes(): List<RemoteNote> {
        return getArray("notes", "updated_at.desc").map { o ->
            val j = o as org.json.JSONObject
            RemoteNote(
                id = if (j.isNull("id")) null else j.optLong("id"),
                title = j.getString("title"),
                content = j.optString("content", ""),
                subjectName = j.optString("subject_name", ""),
                branchCode = j.optString("branch_code", ""),
                academicYear = j.optInt("academic_year", 1),
                fileUrl = j.optString("file_url", ""),
                storagePath = j.optString("storage_path", "")
            )
        }
    }

    /** Download a file from the public `papers` bucket into [dest]. */
    fun downloadPublicFile(storagePath: String, dest: File) {
        val url = URL("${SupabaseConfig.URL}/storage/v1/object/public/papers/$storagePath")
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw java.io.IOException("Download failed: HTTP ${conn.responseCode}")
            }
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
    }

    fun publicFileUrl(storagePath: String): String =
        "${SupabaseConfig.URL}/storage/v1/object/public/papers/$storagePath"

    private fun getArray(table: String, order: String): List<Any> {
        val url = URL("${SupabaseConfig.URL}/rest/v1/$table?select=*&order=$order")
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            setRequestProperty("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw java.io.IOException("Sync failed: HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            return (0 until arr.length()).map { arr.get(it) as Any }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "SupabaseSync"
        fun logSyncError(e: Exception) {
            Log.w(TAG, "Remote sync unavailable, using offline cache: ${e.message}")
        }
    }
}
