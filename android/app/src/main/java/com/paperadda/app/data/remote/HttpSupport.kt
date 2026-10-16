package com.paperadda.app.data.remote

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/**
 * Why a Supabase call failed. The UI shows [message] verbatim, so each case is
 * written for a student, not a developer.
 */
enum class SyncFailure {
    /** No route to the internet at all. */
    OFFLINE,

    /** Supabase answered with 5xx or a transient status, and retries did not help. */
    SERVER,

    /** The anon key or the request was rejected (401/403) — never a network blip. */
    REJECTED,

    /** The response could not be understood, or the row payload was malformed. */
    MALFORMED
}

/** Every Supabase failure funnels through here so callers get one type to catch. */
class SupabaseException(
    val failure: SyncFailure,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * HTTP plumbing shared by the catalog REST client and the two Edge-Function
 * clients (content requests, feedback).
 *
 * Plain [HttpURLConnection] on purpose: the app has no HTTP dependency, and
 * three call sites do not justify adding one. What this adds on top of raw
 * connections is the part that is easy to get wrong:
 *
 *  - timeouts on connect and read, on every connection, always
 *  - gzip request + response, so a 1000-page of catalog is ~1/5 the bytes
 *  - exactly-once retries with backoff for transient failures only
 *  - one failure type carrying a student-readable reason
 *
 * All the decision logic is pure and internal, so it is unit-tested without a
 * network.
 */
internal object HttpSupport {

    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 25_000
    const val DOWNLOAD_CONNECT_TIMEOUT_MS = 15_000
    const val DOWNLOAD_READ_TIMEOUT_MS = 60_000

    /** Retries after the first failure, so 3 attempts total. */
    const val MAX_ATTEMPTS = 3
    private const val BASE_BACKOFF_MS = 400L
    private const val MAX_BACKOFF_MS = 4_000L

    /** Rows per page. Matches PostgREST's default cap, so paging is explicit. */
    const val PAGE_SIZE = 500

    /** A runaway server cannot make the client page for ever. */
    const val MAX_PAGES = 200

    /**
     * Opens a GET connection to [url] with auth, gzip and timeouts applied.
     * [range] adds a `Range: bytes`/`rows` style header for paged reads.
     * [extraHeaders] are applied last, so callers can override defaults.
     */
    fun get(
        url: URL,
        apiKey: String?,
        useAuthHeader: Boolean = true,
        range: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): HttpURLConnection = open(url) {
        apiKey?.let { setRequestProperty("apikey", it) }
        if (useAuthHeader && apiKey != null) setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Accept-Encoding", "gzip")
        setRequestProperty("User-Agent", "PaperAdda-Android")
        if (range != null) setRequestProperty("Range", range)
        extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
    }

    /** Opens a POST connection with a JSON body already sized and written. */
    fun postJson(url: URL, apiKey: String?, body: JSONObject): HttpURLConnection = open(url) {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Accept-Encoding", "gzip")
        setRequestProperty("User-Agent", "PaperAdda-Android")
        if (apiKey != null) {
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        setFixedLengthStreamingMode(bytes.size)
        outputStream.use { out: OutputStream -> out.write(bytes) }
    }

    /**
     * Runs [block] with retries while the failure looks transient. A 4xx or a
     * malformed body is deterministic — retrying just burns the user's data.
     */
    fun <T> withRetry(block: (attempt: Int) -> T): T {
        var last: SupabaseException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return block(attempt)
            } catch (e: SupabaseException) {
                last = e
                if (!isTransientFailure(e.failure) || attempt == MAX_ATTEMPTS) throw e
                sleep(backoffMs(attempt))
            }
        }
        throw last ?: SupabaseException(SyncFailure.MALFORMED, "Request failed")
    }

    /** Reads a response body, transparently handling a gzip content-encoding. */
    fun readBody(connection: HttpURLConnection): String =
        String(readBytes(connection), Charsets.UTF_8)

    /**
     * Streams the response into [output], decompressing gzip on the fly.
     * Used for PDF downloads: those must never be buffered whole in memory.
     */
    fun copyTo(connection: HttpURLConnection, output: OutputStream) {
        val stream: InputStream = try {
            connection.inputStream
        } catch (e: IOException) {
            connection.errorStream ?: throw e
        }
        val encoding = connection.contentEncoding?.lowercase()
        val source = if (encoding?.contains("gzip") == true) {
            java.util.zip.GZIPInputStream(stream)
        } else {
            stream
        }
        source.use { input -> input.copyTo(output) }
    }

    private fun readBytes(connection: HttpURLConnection): ByteArray {
        val stream: InputStream = try {
            connection.inputStream
        } catch (e: IOException) {
            // Error responses put the useful text here.
            connection.errorStream ?: throw e
        }
        val encoding = connection.contentEncoding?.lowercase()
        return if (encoding?.contains("gzip") == true) {
            java.util.zip.GZIPInputStream(stream).use { it.readBytes() }
        } else {
            stream.use { it.readBytes() }
        }
    }

    /** Maps an HTTP status to the failure kind the UI should show. */
    fun classifyStatus(status: Int): SyncFailure = when (status) {
        in 200..299 -> throw IllegalArgumentException("2xx is not a failure: $status")
        401, 403 -> SyncFailure.REJECTED
        404 -> SyncFailure.REJECTED
        408, 425, 429 -> SyncFailure.SERVER
        in 500..599 -> SyncFailure.SERVER
        else -> SyncFailure.SERVER
    }

    /** True when another attempt could plausibly succeed. */
    fun isTransientFailure(failure: SyncFailure): Boolean = when (failure) {
        SyncFailure.OFFLINE, SyncFailure.SERVER -> true
        SyncFailure.REJECTED, SyncFailure.MALFORMED -> false
    }

    /** Exponential backoff, capped — never long enough to abandon the user. */
    fun backoffMs(attempt: Int): Long =
        (BASE_BACKOFF_MS shl (attempt - 1)).coerceAtMost(MAX_BACKOFF_MS)

    /**
     * A body the UI can show: HTML error pages collapse to nothing, and long
     * payloads are truncated so one bad response cannot fill the snackbar.
     */
    fun summarizeErrorBody(body: String, limit: Int = 160): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return ""
        val text = if (trimmed.startsWith("<")) {
            trimmed.replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        } else {
            trimmed.replace(Regex("\\s+"), " ").trim()
        }
        if (text.isEmpty()) return ""
        // Keep only the first line/object; error bodies often repeat the payload.
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: text
        return if (firstLine.length <= limit) firstLine else firstLine.take(limit - 1) + "…"
    }

    /** The `Range` header value for a page starting at [offset]. */
    fun rangeHeader(offset: Int, limit: Int = PAGE_SIZE): String =
        "$offset-${offset + limit - 1}"

    /**
     * True when a short page means the table is exhausted, i.e. the server
     * returned fewer rows than were asked for.
     */
    fun isLastPage(rowCount: Int, pageSize: Int = PAGE_SIZE): Boolean =
        rowCount < pageSize

    private fun open(url: URL, configure: HttpURLConnection.() -> Unit): HttpURLConnection {
        val connection = url.openConnection() as? HttpURLConnection
            ?: throw SupabaseException(SyncFailure.MALFORMED, "Unsupported URL scheme")
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        // HTTPS is the only transport Supabase is reachable over; anything else
        // is a config error, not a fallback.
        if (connection !is HttpsURLConnection) {
            connection.disconnect()
            throw SupabaseException(SyncFailure.MALFORMED, "Backend must be reached over HTTPS")
        }
        connection.configure()
        return connection
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw SupabaseException(SyncFailure.OFFLINE, "Request interrupted", e)
        }
    }

    /** Turns the common network exceptions into the offline failure. */
    fun failureFor(e: Exception): SupabaseException = when (e) {
        is UnknownHostException -> SupabaseException(
            SyncFailure.OFFLINE,
            "No internet connection. Check your network and try again.",
            e
        )
        is SupabaseException -> e
        else -> SupabaseException(
            SyncFailure.MALFORMED,
            e.localizedMessage ?: "Something went wrong talking to the backend",
            e
        )
    }
}
