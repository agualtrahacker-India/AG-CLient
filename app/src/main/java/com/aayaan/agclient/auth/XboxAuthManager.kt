package com.aayaan.agclient.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.aayaan.agclient.utils.Logger
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class XboxAuthManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _authState = MutableStateFlow<XboxAuthState>(XboxAuthState.Idle)
    val authState: StateFlow<XboxAuthState> = _authState.asStateFlow()

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "ag_client_xbox_auth",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Logger.e("XboxAuth", "Fallback to private preferences", e)
            context.getSharedPreferences("ag_client_xbox_auth_fallback", Context.MODE_PRIVATE)
        }
    }

    var liveGamertag: String? = null
        private set
    var liveXuid: String? = null
        private set
    var liveUserHash: String? = null
        private set
    var liveXstsToken: String? = null
        private set
    var cachedMinecraftChain: String? = null
        private set

    init {
        loadSavedCredentials()
    }

    private fun loadSavedCredentials() {
        val gamertag = prefs.getString("gamertag", null)
        val xuid = prefs.getString("xuid", null)
        val userHash = prefs.getString("user_hash", null)
        val xsts = prefs.getString("xsts_token", null)
        cachedMinecraftChain = prefs.getString("minecraft_chain", null)

        if (gamertag != null && xuid != null && userHash != null && xsts != null) {
            liveGamertag = gamertag
            liveXuid = xuid
            liveUserHash = userHash
            liveXstsToken = xsts
            _authState.value = XboxAuthState.Authenticated(gamertag, xuid, userHash)
            Logger.i("XboxAuth", "Loaded cached session for $gamertag")
        }
    }

    fun startDeviceCodeLogin() {
        scope.launch {
            try {
                _authState.value = XboxAuthState.Authenticating
                Logger.i("XboxAuth", "Requesting Microsoft Device Code...")

                // Request device code from Microsoft OAuth 2.0 endpoint
                val deviceCodeUrl = URL("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode")
                val conn = deviceCodeUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val body = "client_id=$CLIENT_ID&scope=" + URLEncoder.encode(SCOPE, "UTF-8")
                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $responseCode"
                    Logger.e("XboxAuth", "Device code endpoint error: $err")
                    _authState.value = XboxAuthState.Error("Device code request failed ($responseCode)")
                    return@launch
                }

                val responseJson = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JsonParser.parseString(responseJson).asJsonObject

                val deviceCode = json.get("device_code").asString
                val userCode = json.get("user_code").asString
                val verificationUri = json.get("verification_uri").asString
                val expiresIn = if (json.has("expires_in")) json.get("expires_in").asInt else 900
                val interval = if (json.has("interval")) json.get("interval").asInt else 5

                _authState.value = XboxAuthState.DeviceCodePrompt(
                    userCode = userCode,
                    verificationUri = verificationUri,
                    expiresIn = expiresIn,
                    message = "Go to $verificationUri and enter code: $userCode"
                )
                Logger.i("XboxAuth", "Prompt user with code: $userCode at $verificationUri")

                // Poll token endpoint with resilience to background app switching
                pollTokenEndpoint(deviceCode, interval, expiresIn)

            } catch (e: Exception) {
                Logger.e("XboxAuth", "Device code flow failed", e)
                _authState.value = XboxAuthState.Error("Login error: ${e.message}")
            }
        }
    }

    private suspend fun pollTokenEndpoint(deviceCode: String, initialInterval: Int, expiresInSeconds: Int) {
        val startTime = System.currentTimeMillis()
        val expiryMillis = if (expiresInSeconds > 0) expiresInSeconds * 1000L else 900_000L
        var interval = if (initialInterval > 0) initialInterval else 5

        while (System.currentTimeMillis() - startTime < expiryMillis) {
            delay(interval * 1000L)

            try {
                val tokenUrl = URL("https://login.microsoftonline.com/consumers/oauth2/v2.0/token")
                val conn = tokenUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val params = "grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                        "&client_id=$CLIENT_ID" +
                        "&device_code=" + URLEncoder.encode(deviceCode, "UTF-8")

                OutputStreamWriter(conn.outputStream).use { it.write(params) }

                val code = conn.responseCode
                if (code == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JsonParser.parseString(resp).asJsonObject
                    val msAccessToken = json.get("access_token").asString
                    val refreshToken = if (json.has("refresh_token")) json.get("refresh_token").asString else null

                    if (refreshToken != null) {
                        prefs.edit().putString("ms_refresh_token", refreshToken).apply()
                    }

                    Logger.i("XboxAuth", "OAuth token acquired. Authenticating with Xbox Live...")
                    authenticateXboxLive(msAccessToken)
                    return
                } else {
                    val errResp = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Logger.d("XboxAuth", "Poll status $code: $errResp")

                    if (errResp.contains("authorization_pending")) {
                        continue
                    } else if (errResp.contains("slow_down")) {
                        interval += 5
                        continue
                    } else if (errResp.contains("authorization_declined")) {
                        _authState.value = XboxAuthState.Error("Sign-in cancelled in browser.")
                        return
                    } else if (errResp.contains("expired_token") || errResp.contains("bad_verification_code")) {
                        _authState.value = XboxAuthState.Error("Login session expired. Please tap Retry.")
                        return
                    }
                }
            } catch (e: Exception) {
                // If a temporary network glitch or socket timeout occurs while app is backgrounded, keep polling!
                Logger.w("XboxAuth", "Temporary connection error during poll, will retry: ${e.message}")
            }
        }
        _authState.value = XboxAuthState.Error("Device code timed out. Please tap Retry.")
    }

    private fun authenticateXboxLive(msAccessToken: String) {
        try {
            // 1. User Authenticate (XBL token)
            val xblReq = JsonObject().apply {
                val prop = JsonObject().apply {
                    addProperty("AuthMethod", "RPS")
                    addProperty("SiteName", "user.auth.xboxlive.com")
                    addProperty("RpsTicket", "d=$msAccessToken")
                }
                add("Properties", prop)
                addProperty("RelyingParty", "http://auth.xboxlive.com")
                addProperty("TokenType", "JWT")
            }

            val xblConn = URL("https://user.auth.xboxlive.com/user/authenticate").openConnection() as HttpURLConnection
            xblConn.requestMethod = "POST"
            xblConn.doOutput = true
            xblConn.connectTimeout = 15000
            xblConn.readTimeout = 15000
            xblConn.setRequestProperty("Content-Type", "application/json")
            xblConn.setRequestProperty("Accept", "application/json")
            xblConn.setRequestProperty("x-xbl-contract-version", "1")
            OutputStreamWriter(xblConn.outputStream).use { it.write(xblReq.toString()) }

            val xblCode = xblConn.responseCode
            if (xblCode !in 200..299) {
                val err = xblConn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $xblCode"
                Logger.e("XboxAuth", "XBL auth failed: $err")
                _authState.value = XboxAuthState.Error("Xbox Live auth failed ($xblCode)")
                return
            }

            val xblResp = xblConn.inputStream.bufferedReader().use { it.readText() }
            val xblJson = JsonParser.parseString(xblResp).asJsonObject
            val xblToken = xblJson.get("Token").asString
            val userHash = xblJson.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).asJsonObject.get("uhs").asString

            // 2. XSTS Token for Minecraft Bedrock
            val xstsReq = JsonObject().apply {
                val prop = JsonObject().apply {
                    addProperty("SandboxId", "RETAIL")
                    val userTokens = com.google.gson.JsonArray().apply { add(xblToken) }
                    add("UserTokens", userTokens)
                }
                add("Properties", prop)
                addProperty("RelyingParty", "https://multiplayer.minecraft.net/")
                addProperty("TokenType", "JWT")
            }

            val xstsConn = URL("https://xsts.auth.xboxlive.com/xsts/authorize").openConnection() as HttpURLConnection
            xstsConn.requestMethod = "POST"
            xstsConn.doOutput = true
            xstsConn.connectTimeout = 15000
            xstsConn.readTimeout = 15000
            xstsConn.setRequestProperty("Content-Type", "application/json")
            xstsConn.setRequestProperty("Accept", "application/json")
            xstsConn.setRequestProperty("x-xbl-contract-version", "1")
            OutputStreamWriter(xstsConn.outputStream).use { it.write(xstsReq.toString()) }

            val xstsCode = xstsConn.responseCode
            if (xstsCode !in 200..299) {
                val err = xstsConn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $xstsCode"
                Logger.e("XboxAuth", "XSTS authorize failed: $err")
                _authState.value = XboxAuthState.Error("XSTS token failed ($xstsCode)")
                return
            }

            val xstsResp = xstsConn.inputStream.bufferedReader().use { it.readText() }
            val xstsJson = JsonParser.parseString(xstsResp).asJsonObject
            val xstsToken = xstsJson.get("Token").asString

            val displayClaims = xstsJson.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).asJsonObject
            val gamertag = if (displayClaims.has("gtg")) displayClaims.get("gtg").asString else "AGClientUser"
            val xuid = if (displayClaims.has("xid")) displayClaims.get("xid").asString else "0"

            liveGamertag = gamertag
            liveXuid = xuid
            liveUserHash = userHash
            liveXstsToken = xstsToken

            prefs.edit()
                .putString("gamertag", gamertag)
                .putString("xuid", xuid)
                .putString("user_hash", userHash)
                .putString("xsts_token", xstsToken)
                .apply()

            _authState.value = XboxAuthState.Authenticated(gamertag, xuid, userHash)
            Logger.i("XboxAuth", "Successfully authenticated Xbox Gamertag: $gamertag ($xuid)")

        } catch (e: Exception) {
            Logger.e("XboxAuth", "Failed to complete Xbox Live auth chain", e)
            _authState.value = XboxAuthState.Error("Xbox auth failed: ${e.message}")
        }
    }

    fun cancelLogin() {
        _authState.value = XboxAuthState.Idle
        Logger.i("XboxAuth", "Device code login cancelled by user.")
    }

    suspend fun getOrFetchMinecraftChain(proxyPublicKeyBase64: String): String? = withContext(Dispatchers.IO) {
        val xsts = liveXstsToken ?: prefs.getString("xsts_token", null) ?: return@withContext null
        val uHash = liveUserHash ?: prefs.getString("user_hash", null) ?: return@withContext null

        val cached = cachedMinecraftChain
        if (!cached.isNullOrBlank()) {
            return@withContext cached
        }

        try {
            Logger.i("XboxAuth", "Requesting official Mojang-signed certificate chain for proxy public key...")
            val url = URL("https://multiplayer.minecraft.net/authentication")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "XBL3.0 x=$uHash;$xsts")

            val reqJson = JsonObject().apply {
                addProperty("identityPublicKey", proxyPublicKeyBase64)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(reqJson.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JsonParser.parseString(resp).asJsonObject
                if (json.has("chain")) {
                    val chainArray = json.getAsJsonArray("chain")
                    val chainStr = chainArray.toString()
                    cachedMinecraftChain = chainStr
                    prefs.edit().putString("minecraft_chain", chainStr).apply()
                    Logger.i("XboxAuth", "Successfully obtained official Mojang certificate chain (${chainArray.size()} tokens)!")
                    return@withContext chainStr
                }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Logger.w("XboxAuth", "Mojang auth endpoint response $code: $err")
            }
        } catch (e: Exception) {
            Logger.e("XboxAuth", "Failed to fetch Mojang certificate chain", e)
        }
        return@withContext null
    }

    fun logout() {
        prefs.edit().clear().apply()
        liveGamertag = null
        liveXuid = null
        liveUserHash = null
        liveXstsToken = null
        cachedMinecraftChain = null
        _authState.value = XboxAuthState.Idle
        Logger.i("XboxAuth", "Logged out and cleared tokens.")
    }

    companion object {
        const val CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb" // Public Microsoft OAuth client ID for Minecraft / Xbox
        const val SCOPE = "XboxLive.signin offline_access"
    }
}
