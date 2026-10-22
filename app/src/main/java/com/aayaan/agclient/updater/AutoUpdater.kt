package com.aayaan.agclient.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
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

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val currentVersion: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.1.0"
    } catch (_: Exception) {
        "2.1.0"
    }

    companion object {
        const val REPO_OWNER = "agualtrahacker-India"
        const val REPO_NAME = "AG-CLient"
        const val API_LATEST_RELEASE = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    }

    fun checkForUpdates(silent: Boolean = false) {
        scope.launch {
            try {
                if (!silent) _updateState.value = UpdateState.Checking
                Logger.i("AutoUpdater", "Checking GitHub for updates at $API_LATEST_RELEASE...")

                val url = URL(API_LATEST_RELEASE)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "AG-Client-Android/$currentVersion")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JsonParser.parseString(body).asJsonObject

                    val tagName = json.get("tag_name").asString
                    val cleanTag = tagName.removePrefix("v").trim()
                    val releaseNotes = if (json.has("body") && !json.get("body").isJsonNull) json.get("body").asString else ""

                    // Find APK asset
                    var downloadUrl: String? = null
                    var apkSize: Long = 0L

                    if (json.has("assets") && json.get("assets").isJsonArray) {
                        for (asset in json.getAsJsonArray("assets")) {
                            val assetObj = asset.asJsonObject
                            val name = assetObj.get("name").asString
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = assetObj.get("browser_download_url").asString
                                apkSize = if (assetObj.has("size")) assetObj.get("size").asLong else 0L
                                break
                            }
                        }
                    }

                    if (isNewerVersion(cleanTag, currentVersion) && downloadUrl != null) {
                        _updateState.value = UpdateState.UpdateAvailable(
                            version = cleanTag,
                            releaseNotes = releaseNotes,
                            downloadUrl = downloadUrl,
                            apkSize = apkSize
                        )
                        Logger.i("AutoUpdater", "New update available: $cleanTag (current: $currentVersion)")
                    } else {
                        _updateState.value = UpdateState.UpToDate(currentVersion)
                        Logger.i("AutoUpdater", "AG Client is up to date: $currentVersion")
                    }
                } else if (code == 404) {
                    _updateState.value = UpdateState.UpToDate(currentVersion)
                    Logger.i("AutoUpdater", "No releases published yet on GitHub. Up to date.")
                } else {
                    val err = "HTTP $code from GitHub releases API"
                    Logger.w("AutoUpdater", err)
                    if (!silent) _updateState.value = UpdateState.Error(err)
                }
            } catch (e: Exception) {
                Logger.e("AutoUpdater", "Failed to check for updates", e)
                if (!silent) _updateState.value = UpdateState.Error("Update check failed: ${e.message}")
            }
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
