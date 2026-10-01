package com.example.data.model

/**
 * A published GitHub Release, used as the update source.
 *
 * PaperAdda ships as a sideloaded APK and is not on any store, so nothing on a
 * user's phone tells them a newer build exists. The app therefore asks GitHub
 * for the repository's latest release and compares versions itself.
 *
 * A maintainer publishes an update by tagging a release and attaching the APK:
 *
 *     gh release create v1.2 app-release.apk --title "1.2" --notes "…"
 *
 * To force a critical update, add this marker anywhere in the release notes:
 *
 *     <!-- min_supported: 1.1 -->
 */
data class AppRelease(
    val id: String,
    val version: String,
    val notes: String,
    val apkUrl: String,
    val releasePageUrl: String,
    val minSupportedVersion: String?
) {
    fun isNewerThan(installedVersion: String): Boolean =
        compareVersions(version, installedVersion) > 0

    /**
     * True when this release must be installed before the app can be used.
     * Only releases that carry the `min_supported` marker can block.
     */
    fun blocks(installedVersion: String): Boolean {
        val floor = minSupportedVersion ?: return false
        return compareVersions(installedVersion, floor) < 0
    }
}

/**
 * Compares dotted version strings such as "1.2", "v1.10" or "1.2.0".
 *
 * Numeric parts are compared as numbers, so 1.10 is newer than 1.9 (a plain
 * string compare would get this backwards). Missing parts count as 0, so
 * "1.2" and "1.2.0" are the same version.
 */
fun compareVersions(a: String, b: String): Int {
    val left = versionParts(a)
    val right = versionParts(b)
    val size = maxOf(left.size, right.size)
    for (i in 0 until size) {
        val l = left.getOrElse(i) { 0 }
        val r = right.getOrElse(i) { 0 }
        if (l != r) return if (l > r) 1 else -1
    }
    return 0
}

private fun versionParts(raw: String): List<Int> =
    raw.trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.', '-', '_')
        .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

/** Strips a leading "v" for display: "v1.2" -> "1.2". */
fun normalizeVersion(raw: String): String =
    raw.trim().removePrefix("v").removePrefix("V")
