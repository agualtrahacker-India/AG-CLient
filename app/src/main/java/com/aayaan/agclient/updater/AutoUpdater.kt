package com.aayaan.agclient.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
    data class ReadyToInstall(val apkFile: File) : UpdateState()
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
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.9.0-beta.1"
    } catch (_: Exception) {
        "2.9.0-beta.1"
    }

    companion object {
        const val REPO_OWNER = "agualtrahacker-India"
        const val REPO_NAME = "AG-CLient"
        const val API_LATEST_RELEASE = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
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
                Logger.i("AutoUpdater", "Checking GitHub for updates (rate-limit free mode)...")

                var foundVersion: String? = null
                var foundDownloadUrl: String? = null
                var foundReleaseNotes = ""
                var foundApkSize = 0L

                // 1. Unlimited Strategy 1: GitHub Raw Content (Fastly CDN, 0 rate limit)
                val rawUrls = listOf(
                    "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/connection-test/version.json",
                    "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/version.json"
                )
                for (rawUrl in rawUrls) {
                    try {
                        val conn = URL(rawUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        conn.setRequestProperty("User-Agent", "AG-Client-Android/$currentVersion")
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
                    } catch (_: Exception) {}
                }

                // 2. Unlimited Strategy 2: GitHub Releases Web Redirect (0 rate limit)
                if (foundVersion == null) {
                    try {
                        val webUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
                        val conn = URL(webUrl).openConnection() as HttpURLConnection
                        conn.instanceFollowRedirects = false
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 AG-Client-Android")
                        val responseCode = conn.responseCode
                        if (responseCode == 302 || responseCode == 301) {
                            val location = conn.getHeaderField("Location")
                            if (!location.isNullOrBlank() && location.contains("/releases/tag/")) {
                                val tag = location.substringAfterLast("/releases/tag/").trim()
                                val cleanTag = tag.removePrefix("v").trim()
                                foundVersion = cleanTag
                                foundDownloadUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$tag/AG-Client-$tag-release.apk"
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
                        val conn = URL(API_LATEST_RELEASE).openConnection() as HttpURLConnection
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
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
                    val finalDownloadUrl = foundDownloadUrl ?: "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
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
                Logger.i("AutoUpdater", "Downloading APK update from $downloadUrl...")

                val updateDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
                val apkFile = File(updateDir, "AG-Client-update.apk")
                if (apkFile.exists()) apkFile.delete()

                val url = URL(downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000

                // Follow redirects (GitHub assets redirect to AWS S3)
                var currentConn = conn
                var responseCode = currentConn.responseCode
                while (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    val newUrl = currentConn.getHeaderField("Location")
                    currentConn = URL(newUrl).openConnection() as HttpURLConnection
                    responseCode = currentConn.responseCode
                }

                val totalBytes = currentConn.contentLengthLong
                var downloadedBytes = 0L

                currentConn.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                                _updateState.value = UpdateState.Downloading(progress)
                            }
                        }
                    }
                }

                Logger.i("AutoUpdater", "APK downloaded successfully to ${apkFile.absolutePath}")
                _updateState.value = UpdateState.ReadyToInstall(apkFile)

                // Prompt user to install
                installApk(apkFile)

            } catch (e: Exception) {
                Logger.e("AutoUpdater", "Failed to download update", e)
                _updateState.value = UpdateState.Error("Download failed: ${e.message}")
            }
        }
    }

    fun installApk(apkFile: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
            Logger.i("AutoUpdater", "Launched Android package installer intent.")
        } catch (e: Exception) {
            Logger.e("AutoUpdater", "Failed to launch package installer", e)
            _updateState.value = UpdateState.Error("Install launch failed: ${e.message}")
        }
    }

    fun isNewerVersion(remoteVersion: String, localVersion: String): Boolean {
        val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V")
        val cleanLocal = localVersion.trim().removePrefix("v").removePrefix("V")

        if (cleanRemote.equals(cleanLocal, ignoreCase = true)) return false

        // Extract numeric segments (e.g. [2, 0, 1] from "2.0.1" or "2.0.1-beta")
        val rMajorMinor = cleanRemote.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
        val lMajorMinor = cleanLocal.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(rMajorMinor.size, lMajorMinor.size)
        for (i in 0 until maxLen) {
            val r = rMajorMinor.getOrElse(i) { 0 }
            val l = lMajorMinor.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }

        // If numeric components are identical (e.g. "2.0" vs "2.0-beta"):
        // A release without a hyphen suffix is newer than a pre-release version
        val rHasPreRelease = cleanRemote.contains("-")
        val lHasPreRelease = cleanLocal.contains("-")

        if (!rHasPreRelease && lHasPreRelease) {
            return true // e.g. "2.0" is newer than "2.0-beta"
        }
        if (rHasPreRelease && !lHasPreRelease) {
            return false // e.g. "2.0-beta" is not newer than "2.0"
        }

        // If both have pre-releases (e.g. "2.0-beta.2" vs "2.0-beta.1"):
        if (rHasPreRelease && lHasPreRelease) {
            val rPre = cleanRemote.substringAfter("-")
            val lPre = cleanLocal.substringAfter("-")
            return rPre.compareTo(lPre, ignoreCase = true) > 0
        }

        return false
    }
}
