package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.AppRelease
import com.example.data.model.compareVersions
import com.example.data.model.normalizeVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds out whether a newer PaperAdda build exists.
 *
 * PaperAdda is not distributed through a store, so the OS never tells a user
 * that a new version is out. Instead the app asks GitHub for the repository's
 * latest release and compares it with the version it has installed.
 *
 * Checks are throttled to [CHECK_INTERVAL_MS] so a normal user makes a couple
 * of unauthenticated GitHub API calls a day, well inside the 60/hour-per-IP
 * limit. Any failure (offline, rate limited, no releases yet) is treated as
 * "nothing to report" — an update banner must never break a launch.
 */
class UpdateRepository(
    private val context: Context,
    private val repoOwner: String = DEFAULT_OWNER,
    private val repoName: String = DEFAULT_REPO
) {
    private val prefs = context.getSharedPreferences("paperadda-updates", Context.MODE_PRIVATE)

    fun installedVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
            ?.takeIf { it.isNotBlank() } ?: "?"
    } catch (e: Exception) {
        "?"
    }

    fun installedVersionCode(): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read installed build", e)
        0
    }

    /**
     * Returns the release the user should move to, or null when they are already
     * on the newest build, the check is not due yet, or the lookup failed.
     */
    suspend fun checkForUpdate(force: Boolean = false): AppRelease? = withContext(Dispatchers.IO) {
        val installed = installedVersionName()
        val release = try {
            fetchLatestRelease()
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            return@withContext null
        } ?: return@withContext null

        if (!release.isNewerThan(installed)) return@withContext null
        // A release that blocks old builds ignores the throttle, so a critical
        // fix still reaches someone who has not opened the app in a week.
        if (!force && !isDue() && !release.blocks(installed)) return@withContext null

        markCheckedNow()
        release
    }

    /** True when enough time has passed since the last background check. */
    fun isDue(): Boolean =
        System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0L) >= CHECK_INTERVAL_MS

    fun markCheckedNow() {
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    private fun fetchLatestRelease(): AppRelease? {
        val url = URL("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            requestMethod = "GET"
            // GitHub rejects requests without a User-Agent.
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PaperAdda-Android")
        }
        try {
            val code = conn.responseCode
            if (code == 404) {
                Log.i(TAG, "No GitHub release published yet")
                return null
            }
            if (code == 403 || code == 429) {
                Log.w(TAG, "GitHub API rate limited or forbidden (HTTP $code)")
                return null
            }
            if (code !in 200..299) {
                Log.w(TAG, "GitHub returned HTTP $code")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parseRelease(body)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "UpdateRepository"
        private const val PREFS = "paperadda-updates"
        private const val KEY_LAST_CHECK = "last_check_ms"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

        const val DEFAULT_OWNER = "psb-001"
        const val DEFAULT_REPO = "paperadda"

        /** Marker that turns a GitHub release into a required update. */
        private val MIN_SUPPORTED = Regex("<!--\\s*min_supported:\\s*([0-9][0-9A-Za-z.\\-_]*)\\s*-->")

        /**
         * Reads a GitHub release payload.
         *
         * Returns null when the release cannot be acted on: a draft, a
         * prerelease, or — most importantly — a release with no APK asset, so a
         * student is never sent to a download that is not an APK.
         */
        fun parseRelease(json: String): AppRelease? {
            val root = JSONObject(json)
            if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null

            val apkUrl = findApkAsset(root) ?: run {
                Log.w(TAG, "Latest release has no .apk asset — ignoring")
                return null
            }

            val tag = root.optString("tag_name").ifBlank { root.optString("name") }
            if (tag.isBlank()) return null

            val body = root.optString("body").orEmpty()
            val notes = body
                .replace(MIN_SUPPORTED, "")
                .trim()
                .ifBlank { "A new version of PaperAdda is available." }

            return AppRelease(
                id = root.optString("id", tag),
                version = normalizeVersion(root.optString("name").ifBlank { tag }),
                notes = notes,
                apkUrl = apkUrl,
                releasePageUrl = root.optString("html_url"),
                minSupportedVersion = MIN_SUPPORTED.find(body)?.groupValues?.get(1)
                    ?.let { normalizeVersion(it) }
            )
        }

        private fun findApkAsset(root: JSONObject): String? {
            val assets = root.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    val url = asset.optString("browser_download_url")
                    if (url.isNotBlank()) return url
                }
            }
            return null
        }
    }
}

/** True when [candidate] is strictly newer than [current]; both may carry a "v". */
internal fun isNewerVersion(candidate: String, current: String): Boolean =
    compareVersions(candidate, current) > 0
