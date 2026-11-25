package com.example.feature.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
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

    const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/Akibxh007/A-Kit/releases/latest"
    const val WEBSITE_URL = "https://a-kit.akib.top"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val DOWNLOAD_BUFFER_SIZE = 65_536

    sealed interface CheckResult {
        data class UpdateAvailable(val release: GitHubReleaseInfo) : CheckResult
        data class UpToDate(val latestVersion: String) : CheckResult
        data class Error(val message: String) : CheckResult
    }

    suspend fun checkLatestRelease(
        currentVersionName: String = BuildConfig.VERSION_NAME,
    ): CheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(LATEST_RELEASE_API_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "A-Kit-Android/$currentVersionName")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext CheckResult.Error("GitHub returned HTTP $code")
            }

            val bodyText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(bodyText)

            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isEmpty()) {
                return@withContext CheckResult.Error("Release tag missing from GitHub response")
            }

            val cleanVersion = normalizeVersion(tagName)
            val releaseTitle = json.optString("name", tagName).trim().ifEmpty { tagName }
            val releaseNotes = json.optString("body", "").trim()
                .ifEmpty { "Performance improvements, bug fixes, and stability updates." }
            val htmlUrl = json.optString("html_url", "https://github.com/Akibxh007/A-Kit/releases/latest")

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

            if (isRemoteVersionNewer(remoteVersion = cleanVersion, localVersion = currentVersionName)) {
                CheckResult.UpdateAvailable(
                    GitHubReleaseInfo(
                        tagName = tagName,
                        versionName = cleanVersion,
                        releaseTitle = releaseTitle,
                        releaseNotes = releaseNotes,
                        htmlUrl = htmlUrl,
                        apkDownloadUrl = apkUrl,
                        apkFileName = apkFileName,
                        apkSizeBytes = apkSize,
                    ),
                )
            } else {
                CheckResult.UpToDate(cleanVersion)
            }
        } catch (e: Exception) {
            CheckResult.Error("Offline or unable to reach GitHub")
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun downloadApk(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val downloadUrl = release.apkDownloadUrl ?: return@withContext null
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Clean previous cached APKs so cache stays compact
        updatesDir.listFiles()?.forEach { old ->
            if (old.isFile && old.name != release.apkFileName) {
                runCatching { old.delete() }
            }
        }

        val safeFileName = release.apkFileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
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
                        if (now - lastEmitMs >= 120L || (contentLength > 0L && totalRead >= contentLength)) {
                            lastEmitMs = now
                            onProgress(totalRead, contentLength)
                        }
                    }
                    output.flush()
                }
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

    private fun openConnectionFollowingRedirects(startUrl: String, maxRedirects: Int = 5): HttpURLConnection {
        var currentUrl = startUrl
        repeat(maxRedirects) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "A-Kit-Android/${BuildConfig.VERSION_NAME}")
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
        throw IllegalStateException("Too many redirects while downloading APK")
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
