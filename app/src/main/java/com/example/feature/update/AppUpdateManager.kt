package com.example.feature.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Xml
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

@Immutable
data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String,
    val apkSizeBytes: Long,
)

enum class UpdateCheckStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    OFFLINE_OR_ERROR,
}

@Immutable
data class AppUpdateState(
    val status: UpdateCheckStatus = UpdateCheckStatus.IDLE,
    val showSheet: Boolean = false,
    val release: GitHubReleaseInfo? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedApkPath: String? = null,
    val statusMessage: String? = null,
    val needsInstallPermissionHint: Boolean = false,
) {
    val downloadProgress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

enum class InstallLaunchResult {
    LAUNCHED_INSTALLER,
    NEEDS_UNKNOWN_SOURCES_PERMISSION,
    FAILED,
}

object AppUpdateManager {

    private const val REPO_OWNER_AND_NAME = "Akibxh007/A-Kit"
    const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/$REPO_OWNER_AND_NAME/releases/latest"
    const val RELEASES_ATOM_FEED_URL = "https://github.com/$REPO_OWNER_AND_NAME/releases.atom"
    const val RELEASES_LATEST_WEB_URL = "https://github.com/$REPO_OWNER_AND_NAME/releases/latest"
    const val WEBSITE_URL = "https://a-kit.akib.top"

    private const val GITHUB_API_VERSION = "2022-11-28"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val DOWNLOAD_READ_TIMEOUT_MS = 35_000
    private const val DOWNLOAD_BUFFER_SIZE = 65_536
    private const val DEFAULT_RELEASE_NOTES = "Performance improvements, bug fixes, and stability updates."

    private fun userAgent(versionName: String = BuildConfig.VERSION_NAME): String =
        "A-Kit-Android-App/$versionName"

    sealed interface CheckResult {
        data class UpdateAvailable(val release: GitHubReleaseInfo) : CheckResult
        data class UpToDate(val latestVersion: String) : CheckResult
        data class Error(val message: String) : CheckResult
    }

    /**
     * Checks for the latest A-Kit GitHub release using a resilient multi-layer strategy:
     * 1. GitHub REST API (`/releases/latest`) with mandatory `User-Agent`, `Accept`, and `X-GitHub-Api-Version`.
     * 2. On HTTP 403 rate-limit or any non-2xx/API error, automatically falls back to the public
     *    `releases.atom` XML feed (never rate-limited) + HTML asset discovery / direct APK URL resolution.
     * 3. Secondary fallback via `/releases/latest` HTTP redirect tag extraction if the Atom feed is unavailable.
     */
    suspend fun checkLatestRelease(
        currentVersionName: String = BuildConfig.VERSION_NAME,
    ): CheckResult = withContext(Dispatchers.IO) {
        val release = fetchReleaseFromGitHubApi(currentVersionName)
            ?: fetchReleaseFromAtomFeed(currentVersionName)
            ?: fetchReleaseFromLatestRedirect(currentVersionName)
            ?: return@withContext CheckResult.Error(
                "Offline or unable to reach GitHub. You can also check a-kit.akib.top.",
            )

        if (isRemoteVersionNewer(remoteVersion = release.versionName, localVersion = currentVersionName)) {
            CheckResult.UpdateAvailable(release)
        } else {
            CheckResult.UpToDate(release.versionName)
        }
    }

    private fun fetchReleaseFromGitHubApi(currentVersionName: String): GitHubReleaseInfo? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(LATEST_RELEASE_API_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent(currentVersionName))
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                return null
            }

            val bodyText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(bodyText)

            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isEmpty()) return null

            val cleanVersion = normalizeVersion(tagName)
            val releaseTitle = json.optString("name", tagName).trim().ifEmpty { tagName }
            val releaseNotes = json.optString("body", "").trim().ifEmpty { DEFAULT_RELEASE_NOTES }
            val htmlUrl = json.optString("html_url", RELEASES_LATEST_WEB_URL)
                .takeIf { it.isNotBlank() } ?: RELEASES_LATEST_WEB_URL

            var apkUrl: String? = null
            var apkFileName = "A-Kit-$tagName.apk"
            var apkSize = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                        apkFileName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (apkUrl.isNullOrBlank()) {
                val resolved = resolveFallbackApkAsset(tagName, currentVersionName)
                apkUrl = resolved.first
                apkFileName = resolved.second
                if (apkSize <= 0L) apkSize = resolved.third
            }

            GitHubReleaseInfo(
                tagName = tagName,
                versionName = cleanVersion,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkFileName,
                apkSizeBytes = apkSize,
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Zero-rate-limit public Atom XML feed fallback (`https://github.com/Akibxh007/A-Kit/releases.atom`).
     * Parses the latest `<entry>` `<id>`, `<link>`, `<title>`, and `<content>`.
     */
    private fun fetchReleaseFromAtomFeed(currentVersionName: String): GitHubReleaseInfo? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(RELEASES_ATOM_FEED_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent(currentVersionName))
                setRequestProperty("Accept", "application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
            }

            if (connection.responseCode !in 200..299) {
                return null
            }

            val xmlText = connection.inputStream.bufferedReader().use { it.readText() }
            val entry = parseFirstAtomEntry(xmlText) ?: return null

            val tagName = extractTagFromAtomEntry(entry) ?: return null
            val cleanVersion = normalizeVersion(tagName)
            val htmlUrl = entry.linkHref?.takeIf { it.isNotBlank() }
                ?: "https://github.com/$REPO_OWNER_AND_NAME/releases/tag/$tagName"
            val title = entry.title?.trim()?.takeIf { it.isNotEmpty() } ?: "A-Kit $tagName"
            val notes = stripHtmlToPlainNotes(entry.contentHtml)

            val (apkUrl, apkFileName, apkSize) = resolveFallbackApkAsset(tagName, currentVersionName)

            GitHubReleaseInfo(
                tagName = tagName,
                versionName = cleanVersion,
                releaseTitle = title,
                releaseNotes = notes,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkFileName,
                apkSizeBytes = apkSize,
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Secondary fallback: follows the public web redirect from `/releases/latest` -> `/releases/tag/{tag}`
     * in case both the JSON API and Atom feed fail.
     */
    private fun fetchReleaseFromLatestRedirect(currentVersionName: String): GitHubReleaseInfo? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(RELEASES_LATEST_WEB_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent(currentVersionName))
                setRequestProperty("Accept", "text/html, */*")
            }
            val code = connection.responseCode
            val location = connection.getHeaderField("Location")
            val resolvedUrl = when {
                code in listOf(301, 302, 303, 307, 308) && !location.isNullOrBlank() ->
                    URL(URL(RELEASES_LATEST_WEB_URL), location).toString()
                code in 200..299 -> connection.url.toString()
                else -> return null
            }
            val tagName = extractTagFromUrl(resolvedUrl) ?: return null
            val (apkUrl, apkFileName, apkSize) = resolveFallbackApkAsset(tagName, currentVersionName)

            GitHubReleaseInfo(
                tagName = tagName,
                versionName = normalizeVersion(tagName),
                releaseTitle = "A-Kit $tagName",
                releaseNotes = DEFAULT_RELEASE_NOTES,
                htmlUrl = resolvedUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkFileName,
                apkSizeBytes = apkSize,
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private data class AtomEntryData(
        val id: String?,
        val title: String?,
        val linkHref: String?,
        val contentHtml: String?,
    )

    private fun parseFirstAtomEntry(xml: String): AtomEntryData? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var insideEntry = false
        var entryId: String? = null
        var entryTitle: String? = null
        var entryLink: String? = null
        var entryContent: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.US)) {
                        "entry" -> insideEntry = true
                        "id" -> if (insideEntry && entryId == null) {
                            entryId = parser.nextText().trim()
                        }
                        "title" -> if (insideEntry && entryTitle == null) {
                            entryTitle = parser.nextText().trim()
                        }
                        "link" -> if (insideEntry && entryLink == null) {
                            val href = parser.getAttributeValue(null, "href")?.trim()
                            if (!href.isNullOrEmpty()) {
                                entryLink = href
                            }
                        }
                        "content" -> if (insideEntry && entryContent == null) {
                            entryContent = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (insideEntry && parser.name.equals("entry", ignoreCase = true)) {
                        return AtomEntryData(
                            id = entryId,
                            title = entryTitle,
                            linkHref = entryLink,
                            contentHtml = entryContent,
                        )
                    }
                }
            }
            eventType = parser.next()
        }
        return null
    }

    private fun extractTagFromAtomEntry(entry: AtomEntryData): String? {
        // 1. Prefer `<link href=".../releases/tag/v1.5.0"/>`
        entry.linkHref?.let { href ->
            extractTagFromUrl(href)?.let { return it }
        }
        // 2. Parse `<id>tag:github.com,2008:Repository/123456/v1.5.0</id>`
        entry.id?.let { id ->
            val candidate = id.substringAfterLast('/').trim()
            if (candidate.isNotEmpty() && candidate.any { it.isDigit() }) {
                return candidate
            }
        }
        // 3. Extract version token from `<title>` (e.g. "A-Kit v1.5.0")
        entry.title?.let { title ->
            val match = Regex("v?\\d+\\.\\d+(?:\\.\\d+)?", RegexOption.IGNORE_CASE).find(title)
            if (match != null) {
                val token = match.value
                return if (token.startsWith("v", ignoreCase = true)) token else "v$token"
            }
        }
        return null
    }

    private fun extractTagFromUrl(url: String): String? {
        val marker = "/releases/tag/"
        val idx = url.indexOf(marker)
        if (idx < 0) return null
        val tag = url.substring(idx + marker.length)
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')
        return tag.takeIf { it.isNotEmpty() }
    }

    private fun stripHtmlToPlainNotes(rawHtml: String?): String {
        if (rawHtml.isNullOrBlank()) return DEFAULT_RELEASE_NOTES
        val decoded = runCatching {
            HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }.getOrElse {
            rawHtml.replace(Regex("<[^>]+>"), "\n")
        }
        val cleaned = decoded.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return cleaned.ifEmpty { DEFAULT_RELEASE_NOTES }
    }

    /**
     * Resolves the real downloadable `.apk` asset URL for [tagName]:
     * 1. Queries GitHub's public `expanded_assets/{tag}` HTML fragment (and release HTML fallback)
     *    so non-standard APK filenames are discovered automatically without API rate limits.
     * 2. Falls back to the canonical direct APK URL `https://github.com/Akibxh007/A-Kit/releases/download/{tag}/A-Kit-{tag}.apk`.
     * 3. Fetches `Content-Length` via a lightweight HEAD request when available.
     */
    private fun resolveFallbackApkAsset(
        tagName: String,
        currentVersionName: String,
    ): Triple<String, String, Long> {
        val discoveredUrl = discoverApkUrlFromReleaseHtml(tagName, currentVersionName)
        val finalUrl = discoveredUrl
            ?: "https://github.com/$REPO_OWNER_AND_NAME/releases/download/$tagName/A-Kit-$tagName.apk"
        val fileName = Uri.parse(finalUrl).lastPathSegment
            ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "A-Kit-$tagName.apk"
        val sizeBytes = probeRemoteContentLength(finalUrl, currentVersionName)
        return Triple(finalUrl, fileName, sizeBytes)
    }

    private fun discoverApkUrlFromReleaseHtml(
        tagName: String,
        currentVersionName: String,
    ): String? {
        val candidatePages = listOf(
            "https://github.com/$REPO_OWNER_AND_NAME/releases/expanded_assets/$tagName",
            "https://github.com/$REPO_OWNER_AND_NAME/releases/tag/$tagName",
        )
        val apkHrefRegex = Regex(
            pattern = """href=["']([^"']*/$REPO_OWNER_AND_NAME/releases/download/[^"']+\.apk)["']""",
            option = RegexOption.IGNORE_CASE,
        )

        for (pageUrl in candidatePages) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", userAgent(currentVersionName))
                    setRequestProperty("Accept", "text/html, */*")
                }
                if (conn.responseCode !in 200..299) continue
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val href = apkHrefRegex.find(html)?.groupValues?.getOrNull(1) ?: continue
                return if (href.startsWith("http", ignoreCase = true)) {
                    href
                } else {
                    "https://github.com$href"
                }
            } catch (_: Exception) {
                // Try next page candidate or fall back to canonical direct URL
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun probeRemoteContentLength(url: String, currentVersionName: String): Long {
        var conn: HttpURLConnection? = null
        return try {
            conn = openConnectionFollowingRedirects(
                startUrl = url,
                method = "HEAD",
                versionName = currentVersionName,
            )
            if (conn.responseCode in 200..299) {
                conn.contentLengthLong.coerceAtLeast(0L)
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun downloadApk(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val downloadUrl = release.apkDownloadUrl ?: return@withContext null

        // Save directly into app external Downloads dir (matching FileProvider path), with cache fallback
        val updatesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.apply { mkdirs() }
            ?: File(context.cacheDir, "updates").apply { mkdirs() }

        val safeFileName = release.apkFileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "A-Kit-${release.tagName}.apk" }

        // Clean stale A-Kit update APKs / partial downloads to avoid storage bloat
        updatesDir.listFiles()?.forEach { old ->
            if (old.isFile &&
                old.name.startsWith("A-Kit-", ignoreCase = true) &&
                (old.name.endsWith(".apk", ignoreCase = true) || old.name.endsWith(".part", ignoreCase = true)) &&
                old.name != safeFileName
            ) {
                runCatching { old.delete() }
            }
        }

        val targetFile = File(updatesDir, safeFileName)
        val tempFile = File(updatesDir, "$safeFileName.part")

        var connection: HttpURLConnection? = null
        try {
            connection = openConnectionFollowingRedirects(downloadUrl)
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext null
            }

            val contentLength = connection.contentLengthLong.takeIf { it > 0L } ?: release.apkSizeBytes
            var totalRead = 0L
            var lastEmitMs = 0L

            BufferedInputStream(connection.inputStream, DOWNLOAD_BUFFER_SIZE).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        totalRead += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 100L || (contentLength > 0L && totalRead >= contentLength)) {
                            lastEmitMs = now
                            onProgress(totalRead, contentLength)
                        }
                    }
                    output.flush()
                }
            }

            if (totalRead <= 0L) {
                runCatching { tempFile.delete() }
                return@withContext null
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            onProgress(totalRead, maxOf(contentLength, totalRead))
            targetFile
        } catch (_: Exception) {
            runCatching { tempFile.delete() }
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun installDownloadedApk(context: Context, apkFile: File): InstallLaunchResult {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return InstallLaunchResult.FAILED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(permIntent) }
            return InstallLaunchResult.NEEDS_UNKNOWN_SOURCES_PERMISSION
        }

        return runCatching {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.drop.fileprovider",
                apkFile,
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            InstallLaunchResult.LAUNCHED_INSTALLER
        }.getOrDefault(InstallLaunchResult.FAILED)
    }

    private fun openConnectionFollowingRedirects(
        startUrl: String,
        method: String = "GET",
        versionName: String = BuildConfig.VERSION_NAME,
        maxRedirects: Int = 8,
    ): HttpURLConnection {
        var currentUrl = startUrl
        repeat(maxRedirects) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = if (method == "HEAD") READ_TIMEOUT_MS else DOWNLOAD_READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent(versionName))
                setRequestProperty("Accept", "application/octet-stream, */*")
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Redirect missing Location header")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
            } else {
                return conn
            }
        }
        throw IllegalStateException("Too many redirects while contacting $startUrl")
    }

    fun normalizeVersion(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V").substringBefore("-")
    }

    fun isRemoteVersionNewer(remoteVersion: String, localVersion: String): Boolean {
        val remoteParts = normalizeVersion(remoteVersion).split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = normalizeVersion(localVersion).split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "APK"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = bytes / 1024.0
            String.format(Locale.US, "%.0f KB", kb)
        }
    }
}
