package com.example

import com.example.data.model.compareVersions
import com.example.data.model.normalizeVersion
import com.example.data.repository.UpdateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The update banner is driven entirely by a GitHub release payload, so the
 * parsing and the version comparison are pinned down here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitHubReleaseUpdateTest {

    private fun release(
        tag: String = "v1.2",
        name: String = "",
        body: String = "• Fixed a crash",
        assets: String = """[{"name":"paperadda-1.2.apk","browser_download_url":"https://github.com/psb-001/paperadda/releases/download/v1.2/paperadda-1.2.apk"}]""",
        draft: Boolean = false,
        prerelease: Boolean = false
    ) = """
        {
          "id": 42,
          "tag_name": "$tag",
          "name": "$name",
          "body": ${org.json.JSONObject.quote(body)},
          "html_url": "https://github.com/psb-001/paperadda/releases/tag/$tag",
          "draft": $draft,
          "prerelease": $prerelease,
          "assets": $assets
        }
    """.trimIndent()

    // ---- version comparison ----

    @Test
    fun comparesNumericPartsNotStrings() {
        assertTrue(compareVersions("1.10", "1.9") > 0)
        assertTrue(compareVersions("1.2", "1.10") < 0)
        assertEquals(0, compareVersions("1.2", "v1.2.0"))
        assertTrue(compareVersions("2.0", "1.99.99") > 0)
    }

    @Test
    fun handlesVPrefixAndJunk() {
        assertEquals(0, compareVersions("v1.1", "1.1"))
        assertEquals(0, compareVersions("1.1-rc1", "1.1.0"))
        assertTrue(compareVersions("1.2.0", "1.1.9") > 0)
        assertEquals("1.2", normalizeVersion("v1.2"))
    }

    // ---- parsing ----

    @Test
    fun parsesReleaseWithApkAsset() {
        val parsed = UpdateRepository.parseRelease(release(name = "1.2"))
        assertEquals("1.2", parsed?.version)
        assertEquals("• Fixed a crash", parsed?.notes)
        assertTrue(parsed?.apkUrl?.endsWith("paperadda-1.2.apk") == true)
        assertNull("no min_supported marker", parsed?.minSupportedVersion)
    }

    @Test
    fun fallsBackToTagWhenNameIsBlank() {
        assertEquals("1.3", UpdateRepository.parseRelease(release(tag = "v1.3", name = ""))?.version)
    }

    @Test
    fun picksTheApkAssetAmongOtherAssets() {
        val assets = """[
          {"name":"checksums.txt","browser_download_url":"https://x/sha256"},
          {"name":"paperadda.apk","browser_download_url":"https://x/paperadda.apk"}
        ]"""
        assertEquals(
            "https://x/paperadda.apk",
            UpdateRepository.parseRelease(release(assets = assets))?.apkUrl
        )
    }

    @Test
    fun ignoresReleaseWithoutApkSoNobodyDownloadsHtml() {
        val assets = """[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"}]"""
        assertNull(UpdateRepository.parseRelease(release(assets = assets)))
        assertNull(UpdateRepository.parseRelease(release(assets = "[]")))
    }

    @Test
    fun ignoresDraftsAndPrereleases() {
        assertNull(UpdateRepository.parseRelease(release(draft = true)))
        assertNull(UpdateRepository.parseRelease(release(prerelease = true)))
    }

    @Test
    fun readsRequiredMarkerAndHidesItFromTheUser() {
        val body = "• Fixes the Requests tab\n<!-- min_supported: 1.1 -->\n• New icons"
        val parsed = UpdateRepository.parseRelease(release(tag = "v1.2", name = "1.2", body = body))
        assertEquals("1.1", parsed?.minSupportedVersion)
        assertFalse("marker must not be shown in release notes", parsed!!.notes.contains("min_supported"))
        assertTrue(parsed.notes.contains("Requests tab"))
        assertTrue(parsed.notes.contains("New icons"))
    }

    @Test
    fun requiredReleaseBlocksOlderBuildsOnly() {
        val parsed = UpdateRepository.parseRelease(
            release(tag = "v1.2", name = "1.2", body = "critical <!-- min_supported: 1.1 -->")
        )!!
        assertTrue("1.0 is below the floor", parsed.blocks("1.0"))
        assertFalse("1.1 meets the floor", parsed.blocks("1.1"))
        assertFalse("1.2 is fine", parsed.blocks("1.2"))
    }

    @Test
    fun releaseWithoutMarkerNeverBlocks() {
        val parsed = UpdateRepository.parseRelease(release(tag = "v1.2", name = "1.2"))!!
        assertFalse(parsed.blocks("0.9"))
        assertTrue(parsed.isNewerThan("1.1"))
        assertFalse(parsed.isNewerThan("1.2"))
        assertFalse(parsed.isNewerThan("1.3"))
    }

    @Test
    fun emptyBodyGetsAFriendlyMessage() {
        val parsed = UpdateRepository.parseRelease(release(body = "   "))!!
        assertTrue(parsed.notes.isNotBlank())
    }

    // ---- the payload GitHub actually returns ----

    /**
     * Every other fixture here is trimmed down to the handful of fields the
     * parser reads, which means none of them would notice a field the parser
     * chokes on. This one mirrors a real `releases/latest` response field for
     * field, including the auto-generated `tarball_url`/`zipball_url` and the
     * newer `immutable` and `digest` fields, so the parser is held to the shape
     * it meets in production rather than the shape it was written against.
     *
     * Deliberately version-agnostic: it stays valid after v1.2 is superseded.
     */
    private val fullReleaseResponse = """
        {
          "url": "https://api.github.com/repos/psb-001/paperadda/releases/1",
          "html_url": "https://github.com/psb-001/paperadda/releases/tag/v9.4",
          "assets_url": "https://api.github.com/repos/psb-001/paperadda/releases/1/assets",
          "upload_url": "https://uploads.github.com/repos/psb-001/paperadda/releases/1/assets{?name,label}",
          "id": 1,
          "node_id": "MDc6UmVsZWFzZTE=",
          "tag_name": "v9.4",
          "target_commitish": "main",
          "name": "9.4",
          "body": "Line one\n\n**Bold** and a `code span`\n",
          "created_at": "2026-09-29T18:36:12Z",
          "published_at": "2026-09-29T18:36:13Z",
          "updated_at": "2026-09-29T18:40:00Z",
          "draft": false,
          "prerelease": false,
          "immutable": false,
          "tarball_url": "https://api.github.com/repos/psb-001/paperadda/tarball/v9.4",
          "zipball_url": "https://api.github.com/repos/psb-001/paperadda/zipball/v9.4",
          "author": {"login": "psb-001", "id": 1, "node_id": "MDQ6VXNlcjE=",
                     "avatar_url": "https://avatars.githubusercontent.com/u/1", "type": "User"},
          "assets": [
            {
              "url": "https://api.github.com/repos/psb-001/paperadda/releases/assets/2",
              "id": 2,
              "node_id": "MDEyOlJlbGVhc2VBc3NldDI=",
              "name": "paperadda-9.4.apk",
              "label": null,
              "uploader": {"login": "psb-001", "id": 1, "type": "User"},
              "content_type": "application/vnd.android.package-archive",
              "state": "uploaded",
              "size": 2461059,
              "digest": "sha256:2fb3d5a9c6ec4c0fb44c9fcac2282d3cd20d6e47fe1ba6d0fe3c216e6bf8d391",
              "download_count": 0,
              "created_at": "2026-09-29T18:36:12Z",
              "updated_at": "2026-09-29T18:36:12Z",
              "browser_download_url": "https://github.com/psb-001/paperadda/releases/download/v9.4/paperadda-9.4.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesTheFullUntrimmedGitHubResponse() {
        val parsed = UpdateRepository.parseRelease(fullReleaseResponse)
        assertNotNull("real GitHub field set failed to parse", parsed)
        assertEquals("9.4", parsed!!.version)
        assertEquals(
            "https://github.com/psb-001/paperadda/releases/download/v9.4/paperadda-9.4.apk",
            parsed.apkUrl
        )
        assertEquals(
            "https://github.com/psb-001/paperadda/releases/tag/v9.4",
            parsed.releasePageUrl
        )
        assertTrue(parsed.isNewerThan("9.3"))
    }

    @Test
    fun neverMistakesTheSourceArchivesForTheApk() {
        // GitHub always sends tarball_url/zipball_url alongside assets; picking
        // the wrong one would hand a user a tarball where they expect an app.
        val parsed = UpdateRepository.parseRelease(fullReleaseResponse)!!
        assertFalse(parsed.apkUrl!!.endsWith(".zip"))
        assertFalse(parsed.apkUrl!!.endsWith(".tar.gz"))
        assertTrue(parsed.apkUrl!!.endsWith(".apk"))
    }

    @Test
    fun keepsCodeSpansAndFormattingInTheRealNotes() {
        val parsed = UpdateRepository.parseRelease(fullReleaseResponse)!!
        assertTrue("markdown must survive", parsed.notes.contains("**Bold**"))
        assertTrue("inline code must survive", parsed.notes.contains("`code span`"))
        assertTrue("newlines must survive", parsed.notes.contains("\n\n"))
    }
}
