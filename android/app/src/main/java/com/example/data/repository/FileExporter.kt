package com.example.data.repository

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Gets a downloaded PDF out of PaperAdda's private folder and into somewhere the
 * student can actually reach.
 *
 * The app stores downloads under `Android/data/com.paperadda.app/files/Download`,
 * which Android hides from file managers and deletes on uninstall — so
 * "download" alone left people with a file they could not open or keep. These
 * helpers copy it into the public Downloads/PaperAdda folder (visible in Files
 * and every file manager) and offer a share sheet.
 */
class FileExporter(private val context: Context) {

    companion object {
        private const val TAG = "FileExporter"
        const val FOLDER_NAME = "PaperAdda"

        const val LEGACY_WRITE_PERMISSION = "android.permission.WRITE_EXTERNAL_STORAGE"

        /**
         * True on the Android versions that still need WRITE_EXTERNAL_STORAGE to
         * save into public Downloads. Android 10 added MediaStore.Downloads,
         * which needs no permission, so the prompt never appears on a modern
         * phone.
         */
        fun needsLegacyWritePermission(): Boolean =
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    }

    sealed interface Result {
        data class Saved(val location: String) : Result
        data class Failed(val message: String) : Result
    }

    /**
     * Copies [file] into the public Downloads folder.
     *
     * On Android 10+ this goes through MediaStore and needs no permission. On
     * Android 9 and below there is no equivalent, so the copy is written
     * directly to the public directory, which does need the legacy write
     * permission — the caller asks for it first, via [needsLegacyWritePermission].
     */
    fun saveToDownloads(file: File, displayName: String? = null): Result {
        if (!file.exists()) return Result.Failed("That file is no longer on the device")
        val name = sanitise(displayName ?: file.name)

        return try {
            if (needsLegacyWritePermission()) {
                saveToPublicDirectory(file, name)
            } else {
                saveViaMediaStore(file, name)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Save rejected", e)
            Result.Failed("Android needs storage permission to save to Downloads")
        } catch (e: Exception) {
            Log.e(TAG, "Save failed", e)
            Result.Failed(e.message ?: "Could not save the file")
        }
    }

    // Every MediaStore.Downloads reference below is API 29+; the caller only
    // reaches this method on Q and above.
    @SuppressLint("InlinedApi")
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(file: File, name: String): Result {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: return Result.Failed("Android would not create the file")

        val written = try {
            val out = resolver.openOutputStream(uri)
            if (out == null) {
                false
            } else {
                out.use { stream ->
                    file.inputStream().use { input -> input.copyTo(stream) }
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore write failed", e)
            false
        }

        if (!written) {
            // Never leave a half-written pending entry behind.
            runCatching { resolver.delete(uri, null, null) }
            return Result.Failed("Could not write the file")
        }

        return try {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Result.Saved("Download/$FOLDER_NAME/$name")
        } catch (e: Exception) {
            Log.e(TAG, "Could not finalise the saved file", e)
            Result.Failed("The file was written but could not be published")
        }
    }

    /**
     * Android 9 and below: there is no MediaStore.Downloads, so the copy is
     * written straight into the public Downloads folder. The caller has already
     * obtained WRITE_EXTERNAL_STORAGE by this point.
     */
    @Suppress("DEPRECATION")
    private fun saveToPublicDirectory(file: File, name: String): Result {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER_NAME
        )
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.Failed("Could not create the Downloads folder")
        }
        val target = File(dir, name)
        FileOutputStream(target).use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        }
        return Result.Saved(target.absolutePath)
    }

    /**
     * A content:// URI other apps can read, for the share sheet.
     *
     * Returns null rather than falling back to a `file://` URI. Sharing a
     * `file://` URI is refused outright from Android 7 onwards
     * (FileUriExposedException), so a fallback here would turn a recoverable
     * "cannot share" into a crash.
     */
    private fun shareUri(file: File): Uri? = runCatching {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }.onFailure { Log.w(TAG, "Could not build a shareable URI", it) }.getOrNull()

    /** The share sheet for [file], or null when no shareable URI can be made. */
    fun shareIntent(file: File): Intent? {
        val uri = shareUri(file) ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Strips separators and caps length so catalog text cannot escape the folder. */
    private fun sanitise(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[^A-Za-z0-9._ -]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim('_', '.', ' ')
            .take(100)
        if (cleaned.isBlank() || cleaned == "." || cleaned == "..") return "paper.pdf"
        return if (cleaned.lowercase().endsWith(".pdf")) cleaned else "$cleaned.pdf"
    }
}
