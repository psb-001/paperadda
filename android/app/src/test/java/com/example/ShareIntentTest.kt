package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.FileExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Modifier

/**
 * Sharing a PDF has to hand another app a `content://` URI.
 *
 * The exporter used to fall back to `Uri.fromFile` when FileProvider refused
 * the file, which is the one thing Android has forbidden since 7.0: sending a
 * `file://` URI to another app throws FileUriExposedException. The fallback
 * therefore turned a recoverable "cannot share this" into a crash, so these
 * tests pin down that it is gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShareIntentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun exporter() = FileExporter(context)

    /**
     * FileProvider memoises parsed path strategies in a static map keyed by
     * authority, rooted at whichever directory the first caller had. Robolectric
     * gives every test a fresh temp data dir but never clears that map, so
     * without this the second test in the class resolves paths against the
     * first test's directories and spuriously fails. On a device there is one
     * Context and one cache, so this is purely a test-isolation concern.
     *
     * Done reflectively and defensively so an androidx rename degrades to "no
     * clearing" rather than a hard test failure.
     */
    @Before
    fun clearFileProviderCache() {
        runCatching {
            FileProvider::class.java.declaredFields
                .filter { Map::class.java.isAssignableFrom(it.type) && Modifier.isStatic(it.modifiers) }
                .forEach { field ->
                    field.isAccessible = true
                    (field.get(null) as? MutableMap<*, *>)?.clear()
                }
        }
    }

    @Test
    fun sharesAFileInsideTheDeclaredProviderPath() {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
        val file = File(dir, "PPS 2025.pdf").apply { writeText("not a real pdf") }

        val intent = exporter().shareIntent(file)

        assertNotNull("a downloaded PDF must be shareable", intent)
        assertEquals("application/pdf", intent!!.type)
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull("share intent needs a stream URI", uri)
        assertEquals(
            "must be a content:// URI, never file://",
            "content", uri!!.scheme
        )
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun returnsNullRatherThanAFileUriWhenTheFileCannotBeShared() {
        // Deliberately outside every path declared in res/xml/file_paths.xml.
        val outside = File(context.cacheDir.parentFile, "outside-the-provider.pdf")
            .apply { writeText("not a real pdf") }

        val intent = exporter().shareIntent(outside)

        assertNull(
            "must return null instead of fabricating a file:// URI, " +
                "which throws FileUriExposedException on Android 7+",
            intent
        )
    }

    @Test
    fun sharesFilesFromTheInternalFilesDirToo() {
        val file = File(context.filesDir, "note.pdf").apply { writeText("x") }

        val intent = exporter().shareIntent(file)

        assertNotNull("files-path is declared, so these must be shareable", intent)
        assertEquals("application/pdf", intent!!.type)
    }

    @Test
    fun grantReadPermissionIsSetSoTheReceivingAppCanOpenIt() {
        val file = File(context.filesDir, "granted.pdf").apply { writeText("x") }

        val intent = exporter().shareIntent(file)!!

        assertTrue(
            "without FLAG_GRANT_READ_URI_PERMISSION the target app gets a " +
                "permission denial when it tries to open the PDF",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        )
    }
}
