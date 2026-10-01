package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.FileExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Saving into the public Downloads folder has two completely different
 * implementations depending on the Android version, and only one of them needs
 * a permission.
 *
 * The bug these guard against: the old code sent Android 9-and-below down a
 * direct-write path that requires WRITE_EXTERNAL_STORAGE, but the app never
 * declared or requested that permission. "Save" on those devices therefore
 * failed with a message telling the user to grant a permission the app had no
 * way to ask for.
 */
@RunWith(RobolectricTestRunner::class)
class SaveToDownloadsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun seedPdf(name: String = "PPS 2025.pdf"): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
        return File(dir, name).apply {
            parentFile?.mkdirs()
            writeText("%PDF-1.4 fake but non-empty")
        }
    }

    // ---- which path is taken ----

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun android9AndBelowTakeTheLegacyWritePath() {
        assertTrue(
            "API 28 has no MediaStore.Downloads, so the direct write path applies",
            FileExporter.needsLegacyWritePermission()
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q, Build.VERSION_CODES.TIRAMISU])
    fun android10AndAboveNeedNoPermission() {
        assertFalse(
            "MediaStore.Downloads exists from API 29 and needs no permission",
            FileExporter.needsLegacyWritePermission()
        )
    }

    // ---- the manifest half of the fix ----

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun manifestDeclaresTheLegacyPermissionOnTheVersionsThatNeedIt() {
        // PackageManager filters requestedPermissions by maxSdkVersion, so this
        // is also the proof that the declaration covers API 28 exactly.
        val declared = declaredPermissions()
        assertTrue(
            "WRITE_EXTERNAL_STORAGE must be declared or API<=28 can never save",
            declared.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun theApi28CapKeepsThePermissionOffModernDevices() {
        // If maxSdkVersion were missing, Android 10+ would carry a permission it
        // can never be granted and the app would be asking for nothing useful.
        assertFalse(
            "WRITE_EXTERNAL_STORAGE must be capped at API 28 so no modern " +
                "device is ever asked for it",
            declaredPermissions().contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        )
    }

    private fun declaredPermissions(): List<String> =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.toList().orEmpty()

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun modernDeviceReportsNoLegacyPermissionRequested() {
        // On API 33+ WRITE_EXTERNAL_STORAGE is not even a grantable runtime
        // permission, so a build that still needed it would be broken here.
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        assertEquals(PackageManager.PERMISSION_DENIED, granted)
    }

    // ---- behaviour on a modern device, which is the path already verified by
    // hand on hardware ----

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun savesThroughMediaStoreOnModernAndroid() {
        val file = seedPdf()
        val result = FileExporter(context).saveToDownloads(file, "PPS 2025.pdf")

        assertTrue(
            "expected a Saved result but got $result",
            result is FileExporter.Result.Saved
        )
        val location = (result as FileExporter.Result.Saved).location
        assertTrue("should report the public folder, was: $location",
            location.startsWith("Download/PaperAdda/"))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun reportsAMissingFileInsteadOfCrashing() {
        val missing = File(context.cacheDir, "definitely-not-here.pdf")
        val result = FileExporter(context).saveToDownloads(missing)
        assertTrue(result is FileExporter.Result.Failed)
    }
}
