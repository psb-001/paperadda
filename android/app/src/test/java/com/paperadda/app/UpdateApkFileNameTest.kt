package com.paperadda.app

import com.paperadda.app.data.model.AppRelease
import com.paperadda.app.data.update.apkFileName
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The APK name is written into a DownloadManager destination path, and the
 * version string comes from a GitHub release — i.e. from us, but not from this
 * build. Anything unexpected in it must not become a path.
 */
class UpdateApkFileNameTest {

    private fun release(version: String) = AppRelease(
        id = "1",
        version = version,
        notes = "",
        apkUrl = "https://example.com/paperadda.apk",
        releasePageUrl = "https://example.com/releases/latest",
        minSupportedVersion = null
    )

    @Test
    fun `normal version names are kept`() {
        assertEquals("paperadda-1.6.apk", apkFileName(release("1.6")))
        assertEquals("paperadda-v1.6.apk", apkFileName(release("v1.6")))
        assertEquals("paperadda-1.6-beta-1.apk", apkFileName(release("1.6-beta-1")))
    }

    @Test
    fun `path characters are replaced, never kept`() {
        // The safe set is letters, digits, dot, underscore and hyphen, so a
        // hyphen in a release name is fine but slashes and spaces are not.
        assertEquals("paperadda-1.6-.apk", apkFileName(release("1.6/")))
        assertEquals("paperadda-1.6-.apk", apkFileName(release("1.6\\")))
        assertEquals("paperadda-1.6-.apk", apkFileName(release("1.6 ")))
        assertEquals("paperadda-1.6--beta-1-.apk", apkFileName(release("1.6 (beta 1)")))
    }

    @Test
    fun `a traversal attempt cannot escape the updates folder`() {
        val name = apkFileName(release("../../outside"))
        assertEquals("paperadda-..-..-outside.apk", name)
        // No directory separators survive at all.
        assertEquals(false, name.contains('/'))
    }

    @Test
    fun `an empty version still produces a usable name`() {
        assertEquals("paperadda-.apk", apkFileName(release("")))
    }
}
