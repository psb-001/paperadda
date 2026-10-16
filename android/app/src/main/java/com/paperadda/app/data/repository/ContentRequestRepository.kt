package com.paperadda.app.data.repository

import android.content.Context
import com.paperadda.app.data.remote.HttpSupport
import com.paperadda.app.data.remote.SupabaseConfig
import com.paperadda.app.data.remote.SupabaseException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
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
        var connection: HttpURLConnection? = null
        try {
            val url = URL("${SupabaseConfig.URL}/functions/v1/content-requests")
            connection = HttpSupport.postJson(url, apiKey = null, body = payload.put("action", action))

            val status = connection.responseCode
            val body = HttpSupport.readBody(connection)
            if (status !in 200..299) {
                throw HttpSupport.failureFor(
                    SupabaseException(HttpSupport.classifyStatus(status), HttpSupport.summarizeErrorBody(body))
                )
            }
            JSONObject(body)
        } catch (e: Exception) {
            throw HttpSupport.failureFor(e)
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
