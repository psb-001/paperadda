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
        var connection: HttpsURLConnection? = null
        try {
            connection = URL("${SupabaseConfig.URL}/functions/v1/feedback").openConnection() as HttpsURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 20000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
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
