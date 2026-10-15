package com.paperadda.app

import com.paperadda.app.data.remote.HttpSupport
import com.paperadda.app.data.remote.SyncFailure
import com.paperadda.app.data.remote.stableOrderClause
import com.paperadda.app.ui.isPlausibleEmail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The HTTP layer's decisions, tested without a network. These rules are what
 * keep a flaky college Wi-Fi from turning into a blank screen, and a truncated
 * catalog from looking complete.
 */
class HttpSupportTest {

    // ---- paging: the silent-truncation bug ----

    @Test
    fun `range header covers the page and nothing else`() {
        assertEquals("0-499", HttpSupport.rangeHeader(0))
        assertEquals("500-999", HttpSupport.rangeHeader(500))
        assertEquals("1000-1499", HttpSupport.rangeHeader(1000, 500))
    }

    @Test
    fun `a short page is the last page`() {
        assertTrue(HttpSupport.isLastPage(0))
        assertTrue(HttpSupport.isLastPage(499))
        assertFalse("a full page means keep asking", HttpSupport.isLastPage(500))
    }

    @Test
    fun `offsets step by the page size so no row is skipped`() {
        var offset = 0
        val requested = mutableListOf<String>()
        repeat(3) {
            requested += HttpSupport.rangeHeader(offset)
            offset += HttpSupport.PAGE_SIZE
        }
        assertEquals(listOf("0-499", "500-999", "1000-1499"), requested)
    }

    // ---- retries: which failures deserve another attempt ----

    @Test
    fun `offline and server failures are retried`() {
        assertTrue(HttpSupport.isTransientFailure(SyncFailure.OFFLINE))
        assertTrue(HttpSupport.isTransientFailure(SyncFailure.SERVER))
    }

    @Test
    fun `rejected and malformed are not retried`() {
        // Retrying a 401 or a broken payload just burns the user's data plan.
        assertFalse(HttpSupport.isTransientFailure(SyncFailure.REJECTED))
        assertFalse(HttpSupport.isTransientFailure(SyncFailure.MALFORMED))
    }

    @Test
    fun `backoff grows but stays capped`() {
        assertEquals(400L, HttpSupport.backoffMs(1))
        assertEquals(800L, HttpSupport.backoffMs(2))
        assertEquals(1600L, HttpSupport.backoffMs(3))
        assertTrue("never longer than the cap", HttpSupport.backoffMs(10) <= 4_000L)
    }

    @Test
    fun `every paged read ends with a unique tiebreaker`() {
        // The sort key must be unique or pages repeat and skip rows.
        assertEquals("year.desc,id.asc", stableOrderClause("year.desc"))
        assertEquals("id.asc,id.asc", stableOrderClause("id.asc"))
        assertTrue(stableOrderClause("updated_at.desc").endsWith("id.asc"))
    }

    // ---- status classification ----

    @Test
    fun `status codes map to the failure the UI should show`() {
        // 4xx are answered requests: retrying them cannot change the outcome.
        assertEquals(SyncFailure.REJECTED, HttpSupport.classifyStatus(401))
        assertEquals(SyncFailure.REJECTED, HttpSupport.classifyStatus(403))
        assertEquals(SyncFailure.REJECTED, HttpSupport.classifyStatus(404))
        // Rate limiting and 5xx are worth another attempt.
        assertEquals(SyncFailure.SERVER, HttpSupport.classifyStatus(408))
        assertEquals(SyncFailure.SERVER, HttpSupport.classifyStatus(425))
        assertEquals(SyncFailure.SERVER, HttpSupport.classifyStatus(429))
        assertEquals(SyncFailure.SERVER, HttpSupport.classifyStatus(500))
        assertEquals(SyncFailure.SERVER, HttpSupport.classifyStatus(503))
    }

    @Test
    fun `2xx is never a failure`() {
        for (status in listOf(200, 201, 204, 206, 299)) {
            try {
                HttpSupport.classifyStatus(status)
                throw AssertionError("$status should not classify as a failure")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
    }

    // ---- error bodies: never shown raw ----

    @Test
    fun `html error pages collapse to plain text`() {
        val body = "<html><head><title>502 Bad Gateway</title></head><body><h1>Bad Gateway</h1>" +
            "<p>The server returned an invalid or incomplete response.</p></body></html>"
        val summary = HttpSupport.summarizeErrorBody(body)
        assertFalse("no markup leaks into the snackbar", summary.contains('<'))
        assertTrue(summary.contains("502 Bad Gateway"))
    }

    @Test
    fun `only the first line of a multi-line body is used`() {
        // Whitespace collapses before the split, so a message spread over
        // several lines stays one readable sentence instead of being cut.
        val summary = HttpSupport.summarizeErrorBody("first useful line\nsecond line\nthird")
        assertEquals("first useful line second line third", summary)
    }

    @Test
    fun `a long body is truncated with an ellipsis`() {
        val summary = HttpSupport.summarizeErrorBody("x".repeat(500), limit = 50)
        assertEquals(50, summary.length)
        assertTrue(summary.endsWith("…"))
    }

    @Test
    fun `a long multi-line body is truncated too`() {
        val summary = HttpSupport.summarizeErrorBody("word ".repeat(100))
        assertTrue(summary.length <= 161)
        assertTrue(summary.endsWith("…"))
    }

    @Test
    fun `an empty body summarises to nothing`() {
        assertEquals("", HttpSupport.summarizeErrorBody("   "))
        assertEquals("", HttpSupport.summarizeErrorBody(""))
    }

    // ---- email validation ----

    @Test
    fun `normal addresses pass`() {
        assertTrue(isPlausibleEmail("student@college.edu"))
        assertTrue(isPlausibleEmail("a.b-c_d@sub.domain.co.in"))
    }

    @Test
    fun `broken addresses are rejected`() {
        assertFalse(isPlausibleEmail("student"))
        assertFalse(isPlausibleEmail("student@"))
        assertFalse(isPlausibleEmail("@college.edu"))
        assertFalse(isPlausibleEmail("student@@college.edu"))
        assertFalse(isPlausibleEmail("student@college"))
        assertFalse(isPlausibleEmail("student name@college.edu"))
        assertFalse(isPlausibleEmail("student@college."))
        assertFalse(isPlausibleEmail(""))
    }

    @Test
    fun `an over-long address is rejected before it is sent`() {
        assertFalse(isPlausibleEmail("a".repeat(250) + "@college.edu"))
    }
}
