package com.example

import com.example.ui.isAllowedLegacyNoteUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the legacy note-URL gate (audit lead
 * pyqhub-note-fileurl-action-view-unvalidated-v1).
 * Pure JVM (no Robolectric): isAllowedLegacyNoteUrl uses java.net.URI only.
 * Only https links on our own Supabase host may reach the external viewer.
 */
class LegacyNoteUrlTest {

    private val host = "bpewbnnywbbaluvnirdx.supabase.co"

    @Test
    fun `supabase storage urls are allowed`() {
        assertTrue(
            isAllowedLegacyNoteUrl(
                "https://$host/storage/v1/object/public/papers/note.pdf"
            )
        )
    }

    @Test
    fun `case and whitespace are tolerated`() {
        assertTrue(
            isAllowedLegacyNoteUrl(
                "  HTTPS://$host/storage/v1/object/public/papers/n.pdf  "
            )
        )
    }

    @Test
    fun `http downgrade is blocked`() {
        assertFalse(isAllowedLegacyNoteUrl("http://$host/storage/x.pdf"))
    }

    @Test
    fun `executable and app schemes are blocked`() {
        listOf(
            "javascript:alert(1)",
            "data:text/html,<h1>x</h1>",
            "intent://x#Intent;scheme=https;end",
            "file:///etc/passwd",
            "market://details?id=com.evil",
            "tel:+911234567890",
            "content://media/external/1"
        ).forEach { assertFalse("allowed: $it", isAllowedLegacyNoteUrl(it)) }
    }

    @Test
    fun `foreign hosts are blocked`() {
        listOf(
            "https://evil.com/$host/x.pdf",
            "https://drive.google.com/file/d/123/view",
            "https://bpewbnnywbbaluvnirdx.supabase.co.evil.com/x.pdf"
        ).forEach { assertFalse("allowed: $it", isAllowedLegacyNoteUrl(it)) }
    }

    @Test
    fun `userinfo trick is blocked`() {
        assertFalse(
            isAllowedLegacyNoteUrl("https://$host@evil.com/x.pdf")
        )
    }

    @Test
    fun `empty and non-url input is blocked`() {
        listOf("", "   ", "not a url", "://missing-scheme").forEach {
            assertFalse("allowed: '$it'", isAllowedLegacyNoteUrl(it))
        }
    }
}
