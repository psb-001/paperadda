package com.paperadda.app.data.repository

import android.content.Context
import com.paperadda.app.data.remote.HttpSupport
import com.paperadda.app.data.remote.SupabaseConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class FeedbackRepository(private val context: Context) {

    private val preferences = context.getSharedPreferences("paperadda_feedback_identity", Context.MODE_PRIVATE)

    private val feedbackKey: String
        get() = preferences.getString(KEY, null) ?: synchronized(this) {
            preferences.getString(KEY, null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString(KEY, it).apply()
            }
        }

    suspend fun submitFeedback(category: String, message: String, email: String) {
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        val payload = JSONObject()
            .put("action", "submit")
            .put("feedbackKey", feedbackKey)
            .put("category", category)
            .put("message", message)
            .put("email", email)
            .put("appVersion", appVersion)
            .put("platform", "android")
        call(payload)
    }

    private suspend fun call(payload: JSONObject) = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = HttpSupport.postJson(
                URL("${SupabaseConfig.URL}/functions/v1/feedback"),
                apiKey = null,
                body = payload
            )

            val status = connection.responseCode
            if (status !in 200..299) {
                val body = HttpSupport.readBody(connection)
                val message = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                throw IOException(message.ifBlank { "Feedback failed ($status)" })
            }
        } catch (error: UnknownHostException) {
            throw IOException("No internet connection. Check your network and try again.", error)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val KEY = "feedback_key"
    }
}
