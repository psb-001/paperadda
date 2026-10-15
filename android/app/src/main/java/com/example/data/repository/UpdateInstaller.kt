package com.example.data.repository

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Downloads a release APK and hands it to the system installer.
 *
 * The file is saved to the app's own external files dir and shared through the
 * existing FileProvider, so no storage permission is needed. If the user has not
 * allowed "install unknown apps" for PaperAdda we send them straight to that
 * setting instead of failing with a cryptic error.
 */
class UpdateInstaller(private val context: Context) {

    sealed interface Result {
        data class Started(val downloadId: Long) : Result
        data class Failed(val message: String) : Result
    }

    /**
     * Where a queued download has actually got to.
     *
     * The old code only remembered the download id, so "a download was started"
     * was indistinguishable from "the download is still going". The button then
     * showed a spinner that never stopped and every tap tried to install a file
     * that was not there yet, which read to the user as a network fault.
     */
    sealed interface DownloadState {
        data object Idle : DownloadState

        /** [total] is -1 when the server sent no content length. */
        data class Running(val downloaded: Long, val total: Long) : DownloadState {
            val percent: Int?
                get() = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else null
        }

        /** Finished and verified on disk. */
        data object Ready : DownloadState

        data class Failed(val message: String) : DownloadState
    }

    /**
     * Asks the system what happened to [downloadId].
     *
     * Returns [DownloadState.Idle] when the id is unknown, which happens after
     * the download is removed or the device rebooted.
     */
    fun query(downloadId: Long): DownloadState {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return DownloadState.Failed("This device has no download service")
        return try {
            dm.query(
                DownloadManager.Query().setFilterById(downloadId)
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return DownloadState.Idle
                val status = cursor.getIntOrZero(DownloadManager.COLUMN_STATUS)
                val soFar = cursor.getLongOrZero(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val total = cursor.getLongOrZero(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadState.Ready

                    DownloadManager.STATUS_FAILED ->
                        DownloadState.Failed(reasonMessage(cursor.getIntOrZero(DownloadManager.COLUMN_REASON)))

                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING ->
                        DownloadState.Running(soFar, total)

                    DownloadManager.STATUS_PAUSED ->
                        // Waiting on Wi-Fi or battery; not an error, so keep the
                        // progress on screen rather than pretending it failed.
                        DownloadState.Running(soFar, total)

                    else -> DownloadState.Running(soFar, total)
                }
            } ?: DownloadState.Idle
        } catch (e: Exception) {
            Log.w(TAG, "Could not query download $downloadId", e)
            DownloadState.Idle
        }
    }

    /** Drops a finished or failed download from the system queue. */
    fun clear(downloadId: Long) {
        runCatching {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                .remove(downloadId)
        }
    }

    private fun android.database.Cursor.getIntOrZero(column: String): Int {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0 else getInt(index)
    }

    private fun android.database.Cursor.getLongOrZero(column: String): Long {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0L else getLong(index)
    }

    /** Turns a DownloadManager failure code into something a student can act on. */
    private fun reasonMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "Download paused. Try again."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage is unavailable on this device."
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "A copy is already in the queue. Try again."
        DownloadManager.ERROR_FILE_ERROR -> "The download could not be saved. Try again."
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "The connection dropped. Try again."
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough free space to install the update."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "The download link failed. Try again."
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The download link returned an error. Try again."
        else -> "Download failed. Check your connection and try again."
    }

    /** Queues [apkUrl] for download. Returns the DownloadManager id to watch. */
    fun startDownload(apkUrl: String, fileName: String): Result {
        if (apkUrl.isBlank()) return Result.Failed("No download link for this release")
        return try {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir,
                "updates"
            ).apply { mkdirs() }
            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("PaperAdda update")
                setDescription("Downloading the latest version…")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/$fileName")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            Result.Started(dm.enqueue(request))
        } catch (e: Exception) {
            Log.e(TAG, "Could not start download", e)
            Result.Failed(e.message ?: "Download could not start")
        }
    }

    /**
     * Opens the system installer for a finished download.
     * Returns false when the saved file is missing.
     */
    fun install(downloadId: Long, fileName: String): Boolean {
        val file = File(
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir,
                "updates"
            ),
            fileName
        )
        if (!file.exists()) {
            Log.w(TAG, "APK not on disk yet: $file")
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not open installer", e)
            false
        }
    }

    /**
     * Sends the user to the "allow from this source" screen.
     * Returns false when the OS has no such setting to show.
     */
    fun openInstallPermissionSettings(): Boolean = try {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "No unknown-sources settings screen", e)
        false
    }

    /** True when PaperAdda is currently allowed to install packages. */
    fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            // "Install unknown apps" did not exist before Android 8.
            true
        }

    private companion object {
        const val TAG = "UpdateInstaller"
    }
}
