package com.example.data.repository

import android.content.Context
import com.example.data.remote.SupabaseConfig
import java.io.IOException
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ContentRequest(
    val id: String,
    val kind: String,
    val branchCode: String,
    val academicYear: Int,
    val title: String,
    val details: String,
    val status: String,
    val adminNote: String,
    val createdAt: String,
    val updatedAt: String
)

class ContentRequestRepository(context: Context) {

    private val preferences = context.getSharedPreferences("paperadda_request_identity", Context.MODE_PRIVATE)

    private val requesterKey: String
        get() = preferences.getString(KEY, null) ?: synchronized(this) {
            preferences.getString(KEY, null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString(KEY, it).apply()
            }
        }

    suspend fun listRequests(): List<ContentRequest> = call(
        action = "list",
        payload = JSONObject().put("requesterKey", requesterKey)
    ).optJSONArray("requests")?.let { requests ->
        buildList {
            for (index in 0 until requests.length()) add(requests.getJSONObject(index).toContentRequest())
        }
    } ?: emptyList()

    suspend fun submitRequest(
        kind: String,
        branchCode: String,
        academicYear: Int,
        title: String,
        details: String
    ): ContentRequest = call(
        action = "submit",
        payload = JSONObject()
            .put("requesterKey", requesterKey)
            .put("kind", kind)
            .put("branchCode", branchCode)
            .put("academicYear", academicYear)
            .put("title", title)
            .put("details", details)
    ).getJSONObject("request").toContentRequest()

    suspend fun deleteRequest(id: String) {
        call(
            action = "delete",
            payload = JSONObject()
                .put("requesterKey", requesterKey)
                .put("id", id)
        )
    }

    private suspend fun call(action: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            connection = URL("${SupabaseConfig.URL}/functions/v1/content-requests").openConnection() as HttpsURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 20000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            payload.put("action", action)
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                throw IOException(message.ifBlank { "Request failed ($status)" })
            }
            JSONObject(body)
        } catch (error: UnknownHostException) {
            throw IOException("No internet connection. Check your network and try again.", error)
        } finally {
            connection?.disconnect()
        }
    }

    private fun JSONObject.toContentRequest() = ContentRequest(
        id = getString("id"),
        kind = getString("kind"),
        branchCode = getString("branchCode"),
        academicYear = getInt("academicYear"),
        title = getString("title"),
        details = optString("details"),
        status = getString("status"),
        adminNote = optString("adminNote"),
        createdAt = getString("createdAt"),
        updatedAt = getString("updatedAt")
    )

    companion object {
        private const val KEY = "requester_key"
    }
}
