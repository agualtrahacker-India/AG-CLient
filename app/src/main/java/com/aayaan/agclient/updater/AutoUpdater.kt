package com.aayaan.agclient.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.aayaan.agclient.MainActivity
import com.aayaan.agclient.R
import com.aayaan.agclient.utils.Logger
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(
        val version: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val apkSize: Long
    ) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class ReadyToInstall(val apkFile: File, val requiresUnknownSourcePermission: Boolean = false) : UpdateState()
    data class UpToDate(val currentVersion: String) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class AutoUpdater(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences("ag_client_updater", Context.MODE_PRIVATE)
    private var periodicJob: Job? = null

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val currentVersion: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.9.9"
    } catch (_: Exception) {
        "2.9.9"
    }

    companion object {
        const val REPO_OWNER = "agualtrahacker-India"
        const val REPO_NAME = "AG-Client"
        const val NOTIFICATION_CHANNEL_ID = "ag_client_updates_channel"
        const val NOTIFICATION_ID_UPDATE = 4001
        const val PREF_LAST_CHECK = "last_check_timestamp"
        const val CHECK_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
    }

    init {
        startPeriodicUpdateChecks()
    }

    fun startPeriodicUpdateChecks() {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            while (isActive) {
                val lastCheck = prefs.getLong(PREF_LAST_CHECK, 0L)
                val now = System.currentTimeMillis()
                val elapsed = now - lastCheck

                if (elapsed >= CHECK_INTERVAL_MS || lastCheck == 0L) {
                    checkForUpdates(silent = true, force = true)
                }

                val waitTime = (CHECK_INTERVAL_MS - (System.currentTimeMillis() - prefs.getLong(PREF_LAST_CHECK, now)))
                    .coerceAtLeast(60_000L)
                delay(waitTime)
            }
        }
    }

    fun checkForUpdates(silent: Boolean = false, force: Boolean = false) {
        scope.launch {
            try {
                val lastCheck = prefs.getLong(PREF_LAST_CHECK, 0L)
                val now = System.currentTimeMillis()
                val elapsed = now - lastCheck

                if (!force && lastCheck != 0L && elapsed < CHECK_INTERVAL_MS) {
                    Logger.d("AutoUpdater", "Skipping background update check - last check was ${elapsed / 1000}s ago.")
                    return@launch
                }
                prefs.edit().putLong(PREF_LAST_CHECK, now).apply()

                if (!silent) _updateState.value = UpdateState.Checking
                Logger.i("AutoUpdater", "Checking GitHub for updates (Fastly CDN + GitHub Releases)...")

                var foundVersion: String? = null
                var foundDownloadUrl: String? = null
                var foundReleaseNotes = ""
                var foundApkSize = 0L

                // 1. Primary Strategy: GitHub Raw Content (Fastly CDN with timestamp cache-buster)
                val rawUrls = listOf(
                    "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/connection-test/version.json?t=$now",
                    "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/version.json?t=$now"
                )
                for (rawUrl in rawUrls) {
                    try {
                        val conn = URL(rawUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        conn.setRequestProperty("User-Agent", "AG-Client-Android/$currentVersion")
                        conn.setRequestProperty("Cache-Control", "no-cache")
                        if (conn.responseCode == 200) {
                            val body = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = JsonParser.parseString(body).asJsonObject
                            if (json.has("version")) {
                                foundVersion = json.get("version").asString.removePrefix("v").trim()
                                foundDownloadUrl = if (json.has("downloadUrl")) json.get("downloadUrl").asString else null
                                foundReleaseNotes = if (json.has("releaseNotes")) json.get("releaseNotes").asString else ""
                                Logger.i("AutoUpdater", "Discovered latest release via Fastly CDN version.json: v$foundVersion")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Logger.d("AutoUpdater", "Fastly CDN check failed for $rawUrl: ${e.message}")
                    }
                }

                // 2. Fallback: GitHub Releases Web Redirect (0 rate limit)
                if (foundVersion == null) {
                    try {
                        val webUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
                        val conn = URL(webUrl).openConnection() as HttpURLConnection
                        conn.instanceFollowRedirects = false
                        conn.connectTimeout = 6000
                        conn.readTimeout = 6000
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 AG-Client-Android")
                        val responseCode = conn.responseCode
                        if (responseCode == 302 || responseCode == 301) {
                            val location = conn.getHeaderField("Location")
                            if (!location.isNullOrBlank() && location.contains("/releases/tag/")) {
                                val tag = location.substringAfterLast("/releases/tag/").trim()
                                val cleanTag = tag.removePrefix("v").trim()
                                foundVersion = cleanTag
                                foundDownloadUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$tag/app-release.apk"
                                foundReleaseNotes = "New release $tag is available on GitHub."
                                Logger.i("AutoUpdater", "Discovered latest release via GitHub web redirect: $tag")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.d("AutoUpdater", "Web redirect check: ${e.message}")
                    }
                }

                // 3. Fallback: GitHub REST API
                if (foundVersion == null) {
                    try {
                        val apiUrl = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
                        val conn = URL(apiUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 6000
                        conn.readTimeout = 6000
                        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                        conn.setRequestProperty("User-Agent", "AG-Client-Android/$currentVersion")
                        if (conn.responseCode == 200) {
                            val body = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = JsonParser.parseString(body).asJsonObject
                            val tagName = json.get("tag_name").asString
                            foundVersion = tagName.removePrefix("v").trim()
                            foundReleaseNotes = if (json.has("body") && !json.get("body").isJsonNull) json.get("body").asString else ""
                            if (json.has("assets") && json.get("assets").isJsonArray) {
                                for (asset in json.getAsJsonArray("assets")) {
                                    val assetObj = asset.asJsonObject
                                    val name = assetObj.get("name").asString
                                    if (name.endsWith(".apk", ignoreCase = true)) {
                                        foundDownloadUrl = assetObj.get("browser_download_url").asString
                                        foundApkSize = if (assetObj.has("size")) assetObj.get("size").asLong else 0L
                                        break
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.d("AutoUpdater", "REST API check: ${e.message}")
                    }
                }

                // Evaluate Result
                val targetVer = foundVersion
                if (targetVer != null) {
                    val finalDownloadUrl = foundDownloadUrl ?: "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/v$targetVer/app-release.apk"
                    if (isNewerVersion(targetVer, currentVersion)) {
                        _updateState.value = UpdateState.UpdateAvailable(
                            version = targetVer,
                            releaseNotes = foundReleaseNotes,
                            downloadUrl = finalDownloadUrl,
                            apkSize = foundApkSize
                        )
                        Logger.i("AutoUpdater", "New update available: v$targetVer (current: v$currentVersion)")
                        sendUpdateNotification(targetVer)
                    } else {
                        _updateState.value = UpdateState.UpToDate(currentVersion)
                        Logger.i("AutoUpdater", "AG Client is up to date: v$currentVersion")
                    }
                } else {
                    _updateState.value = UpdateState.UpToDate(currentVersion)
                    Logger.i("AutoUpdater", "Up to date (v$currentVersion). No new release found.")
                }

            } catch (e: Exception) {
                Logger.e("AutoUpdater", "Failed to check for updates", e)
                if (!silent) _updateState.value = UpdateState.Error("Update check failed: ${e.message}")
            }
        }
    }

    private fun sendUpdateNotification(version: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications when a new version of AG Client is released"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New AG Client Update Available!")
                .setContentText("Version v$version is available. Tap to open and update.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID_UPDATE, notification)
            Logger.i("AutoUpdater", "Dispatched update notification for v$version")
        } catch (e: Exception) {
            Logger.e("AutoUpdater", "Failed to send update notification", e)
        }
    }

    fun startDownload(downloadUrl: String) {
        scope.launch {
            try {
                _updateState.value = UpdateState.Downloading(0.0f)
                Logger.i("AutoUpdater", "Starting APK update download from: $downloadUrl")

                // Target downloads directory (external or internal fallback)
                val updateDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(context.cacheDir, "updates")
                if (!updateDir.exists()) updateDir.mkdirs()

                val apkFile = File(updateDir, "AG-Client-update.apk")
                if (apkFile.exists()) apkFile.delete()

                // List of candidate URLs to try if primary URL returns 404
                val candidateUrls = mutableListOf(downloadUrl)
                if (downloadUrl.contains("/releases/download/")) {
                    val tag = downloadUrl.substringAfter("/releases/download/").substringBefore("/")
                    val urlAppRelease = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$tag/app-release.apk"
                    val urlNamedRelease = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$tag/AG-Client-$tag-release.apk"
                    if (!candidateUrls.contains(urlAppRelease)) candidateUrls.add(urlAppRelease)
                    if (!candidateUrls.contains(urlNamedRelease)) candidateUrls.add(urlNamedRelease)
                }

                var downloadSuccess = false
                var lastError: Exception? = null

                for (targetUrl in candidateUrls) {
                    try {
                        Logger.i("AutoUpdater", "Attempting download: $targetUrl")
                        val downloaded = downloadFileWithRedirects(targetUrl, apkFile) { progress ->
                            _updateState.value = UpdateState.Downloading(progress)
                        }
                        if (downloaded && apkFile.exists() && apkFile.length() > 1_000_000L) {
                            downloadSuccess = true
                            Logger.i("AutoUpdater", "Download completed (${apkFile.length() / (1024 * 1024)} MB): ${apkFile.absolutePath}")
                            break
                        }
                    } catch (e: Exception) {
                        lastError = e
                        Logger.w("AutoUpdater", "Download attempt failed for $targetUrl: ${e.message}")
                    }
                }

                if (!downloadSuccess) {
                    throw lastError ?: Exception("Unable to download APK from available release URLs.")
                }

                // Check installation permissions on Android 8+
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        !context.packageManager.canRequestPackageInstalls()

                _updateState.value = UpdateState.ReadyToInstall(apkFile, needsPermission)

                // Trigger package installation intent
                installApk(apkFile)

            } catch (e: Exception) {
                Logger.e("AutoUpdater", "Failed to download update", e)
                _updateState.value = UpdateState.Error("Download failed: ${e.message}")
            }
        }
    }

    private fun downloadFileWithRedirects(
        initialUrl: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        var currentUrl = initialUrl
        var redirects = 0
        val maxRedirects = 8

        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "AG-Client-Android/$currentVersion")
            conn.setRequestProperty("Accept", "*/*")

            val responseCode = conn.responseCode
            Logger.d("AutoUpdater", "HTTP $responseCode from $currentUrl")

            if (responseCode in 300..399) {
                val location = conn.getHeaderField("Location")
                    ?: throw Exception("HTTP redirect ($responseCode) missing Location header")
                currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                conn.disconnect()
                redirects++
                continue
            }

            if (responseCode !in 200..299) {
                conn.disconnect()
                throw Exception("HTTP $responseCode from server: ${conn.responseMessage}")
            }

            val totalBytes = conn.contentLengthLong
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                            onProgress(progress)
                        }
                    }
                }
            }
            conn.disconnect()
            return true
        }
        throw Exception("Too many HTTP redirects (exceeded $maxRedirects)")
    }

    fun openUnknownSourcesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Logger.i("AutoUpdater", "Opened unknown sources settings.")
            } catch (e: Exception) {
                Logger.e("AutoUpdater", "Could not open unknown sources settings", e)
            }
        }
    }

    fun installApk(apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() < 100_000L) {
                _updateState.value = UpdateState.Error("Downloaded APK file is incomplete or missing.")
                return
            }

            // Check if user needs to grant REQUEST_INSTALL_PACKAGES on Android 8.0 - 16
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Logger.w("AutoUpdater", "REQUEST_INSTALL_PACKAGES permission not granted. Prompting user settings.")
                    openUnknownSourcesSettings()
                    _updateState.value = UpdateState.ReadyToInstall(apkFile, requiresUnknownSourcePermission = true)
                    return
                }
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }

            // Grant read permission to all potential package installers
            val resolveInfoList = context.packageManager.queryIntentActivities(
                installIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resolveInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Explicitly grant to common OEM installers (Samsung, Google, AOSP)
            val knownInstallers = listOf(
                "com.google.android.packageinstaller",
                "com.android.packageinstaller",
                "com.samsung.android.packageinstaller"
            )
            for (pkg in knownInstallers) {
                try {
                    context.grantUriPermission(pkg, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }

            context.startActivity(installIntent)
            Logger.i("AutoUpdater", "Launched Android package installer for ${apkFile.name} via FileProvider.")
            _updateState.value = UpdateState.ReadyToInstall(apkFile, requiresUnknownSourcePermission = false)

        } catch (e: Exception) {
            Logger.e("AutoUpdater", "Failed to launch package installer", e)
            _updateState.value = UpdateState.Error("Install launch failed: ${e.message}")
        }
    }

    fun runUpdaterDiagnostic(): String {
        val sb = StringBuilder()
        sb.append("=== AG CLIENT AUTO-UPDATER DIAGNOSTIC ===\n")
        sb.append("  ✔ Current App Version: v$currentVersion\n")
        sb.append("  ✔ Target Package: ${context.packageName}\n")

        val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
        sb.append(if (canInstall) "  ✔ Install Unknown Apps Permission: GRANTED\n" else "  ⚠ Install Unknown Apps Permission: NOT GRANTED (Requires user toggle in Settings)\n")

        val updateDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.cacheDir, "updates")
        sb.append("  ✔ Download Directory: ${updateDir.absolutePath} (Writable: ${updateDir.canWrite()})\n")

        val testFile = File(updateDir, "test_perm.tmp")
        try {
            testFile.writeText("test")
            testFile.delete()
            sb.append("  ✔ File System Write Probe: OK\n")
        } catch (e: Exception) {
            sb.append("  ❌ File System Write Probe: FAILED (${e.message})\n")
        }

        try {
            val dummyApk = File(updateDir, "dummy_check.apk").apply {
                if (!exists()) writeBytes(ByteArray(1024))
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dummyApk)
            dummyApk.delete()
            sb.append("  ✔ FileProvider Content URI Generation: OK ($uri)\n")
        } catch (e: Exception) {
            sb.append("  ❌ FileProvider Content URI: FAILED (${e.message})\n")
        }

        sb.append("========================================\n")
        val report = sb.toString()
        Logger.diag(report)
        return report
    }

    fun isNewerVersion(remoteVersion: String, localVersion: String): Boolean {
        val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V")
        val cleanLocal = localVersion.trim().removePrefix("v").removePrefix("V")

        if (cleanRemote.equals(cleanLocal, ignoreCase = true)) return false

        // Extract numeric segments (e.g. [2, 9, 9] from "2.9.9")
        val rMajorMinor = cleanRemote.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
        val lMajorMinor = cleanLocal.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(rMajorMinor.size, lMajorMinor.size)
        for (i in 0 until maxLen) {
            val r = rMajorMinor.getOrElse(i) { 0 }
            val l = lMajorMinor.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }

        val rHasPreRelease = cleanRemote.contains("-")
        val lHasPreRelease = cleanLocal.contains("-")

        if (!rHasPreRelease && lHasPreRelease) return true
        if (rHasPreRelease && !lHasPreRelease) return false

        if (rHasPreRelease && lHasPreRelease) {
            val rPre = cleanRemote.substringAfter("-")
            val lPre = cleanLocal.substringAfter("-")
            return rPre.compareTo(lPre, ignoreCase = true) > 0
        }

        return false
    }
}
