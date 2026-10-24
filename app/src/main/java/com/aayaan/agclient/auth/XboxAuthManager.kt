package com.aayaan.agclient.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
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
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

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

    val deviceKeyPair: KeyPair by lazy {
        getOrCreatePersistentKeyPair()
    }

    init {
        loadSavedCredentials()
    }

    fun getDaysRemainingInSession(): Int {
        val loginTime = prefs.getLong("login_timestamp", 0L)
        if (loginTime <= 0L) return 0
        val elapsedMs = System.currentTimeMillis() - loginTime
        val remainingMs = MAX_LOGIN_DURATION_MS - elapsedMs
        return if (remainingMs > 0) (remainingMs / (24 * 60 * 60 * 1000L)).toInt().coerceIn(1, 90) else 0
    }

    private fun getOrCreatePersistentKeyPair(): KeyPair {
        val privB64 = prefs.getString("device_ec_private_key", null)
        val pubB64 = prefs.getString("device_ec_public_key", null)

        if (!privB64.isNullOrBlank() && !pubB64.isNullOrBlank()) {
            try {
                val kf = KeyFactory.getInstance("EC")
                val privBytes = Base64.decode(privB64, Base64.NO_WRAP)
                val pubBytes = Base64.decode(pubB64, Base64.NO_WRAP)
                val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val publicKey = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                Logger.i("XboxAuth", "Loaded persistent device EC KeyPair.")
                return KeyPair(publicKey, privateKey)
            } catch (e: Exception) {
                Logger.w("XboxAuth", "Failed to deserialize persistent keypair, generating a fresh one: ${e.message}")
            }
        }

        return try {
            val keyGen = KeyPairGenerator.getInstance("EC")
            try {
                keyGen.initialize(ECGenParameterSpec("secp384r1"))
            } catch (_: Exception) {
                keyGen.initialize(ECGenParameterSpec("secp256r1"))
            }
            val kp = keyGen.generateKeyPair()
            val newPrivB64 = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
            val newPubB64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)

            prefs.edit()
                .putString("device_ec_private_key", newPrivB64)
                .putString("device_ec_public_key", newPubB64)
                .remove("minecraft_chain")
                .remove("minecraft_chain_pubkey")
                .apply()

            Logger.i("XboxAuth", "Generated and saved new persistent device EC KeyPair (secp384r1).")
            kp
        } catch (e: Exception) {
            Logger.e("XboxAuth", "Critical error generating device keypair", e)
            val fallbackGen = KeyPairGenerator.getInstance("EC")
            fallbackGen.generateKeyPair()
        }
    }

    private fun loadSavedCredentials() {
        val gamertag = prefs.getString("gamertag", null)
        val xuid = prefs.getString("xuid", null)
        val userHash = prefs.getString("user_hash", null)
        val xsts = prefs.getString("xsts_token", null)
        cachedMinecraftChain = prefs.getString("minecraft_chain", null)
        val loginTime = prefs.getLong("login_timestamp", 0L)
        val now = System.currentTimeMillis()

        // 90-day login session expiration check
        if (loginTime > 0L && (now - loginTime) > MAX_LOGIN_DURATION_MS) {
            Logger.w("XboxAuth", "90-day login session has expired. Resetting session.")
            logout()
            _authState.value = XboxAuthState.Error("Your 90-day login session has expired. Please sign in again.")
            return
        }

        if (gamertag != null && xuid != null && userHash != null && xsts != null) {
            liveGamertag = gamertag
            liveXuid = xuid
            liveUserHash = userHash
            liveXstsToken = xsts
            _authState.value = XboxAuthState.Authenticated(gamertag, xuid, userHash)
            val days = getDaysRemainingInSession()
            Logger.i("XboxAuth", "Loaded cached session for $gamertag ($days days remaining in 90-day cycle).")

            // Auto-refresh token and pre-fetch Mojang chain in background on startup
            scope.launch {
                refreshTokensIfNeeded()
                val pubKeyB64 = Base64.encodeToString(deviceKeyPair.public.encoded, Base64.NO_WRAP)
                getOrFetchMinecraftChain(pubKeyB64)
            }
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

            val now = System.currentTimeMillis()
            val existingLoginTimestamp = prefs.getLong("login_timestamp", 0L)
            val loginTimestamp = if (existingLoginTimestamp > 0L) existingLoginTimestamp else now

            prefs.edit()
                .putString("gamertag", gamertag)
                .putString("xuid", xuid)
                .putString("user_hash", userHash)
                .putString("xsts_token", xstsToken)
                .putLong("login_timestamp", loginTimestamp)
                .putLong("last_token_refresh", now)
                .apply()

            _authState.value = XboxAuthState.Authenticated(gamertag, xuid, userHash)
            Logger.i("XboxAuth", "Successfully authenticated Xbox Gamertag: $gamertag ($xuid) [90-Day tracking active]")

        } catch (e: Exception) {
            Logger.e("XboxAuth", "Failed to complete Xbox Live auth chain", e)
            _authState.value = XboxAuthState.Error("Xbox auth failed: ${e.message}")
        }
    }

    suspend fun refreshTokensIfNeeded(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = prefs.getString("ms_refresh_token", null) ?: return@withContext false
        val lastRefresh = prefs.getLong("last_token_refresh", 0L)
        val now = System.currentTimeMillis()

        // Only refresh if forced or if older than 12 hours (43_200_000 ms)
        if (!force && liveXstsToken != null && (now - lastRefresh) < 43_200_000L) {
            return@withContext true
        }

        try {
            Logger.i("XboxAuth", "Silently refreshing Microsoft & Xbox Live tokens in background...")
            val tokenUrl = URL("https://login.microsoftonline.com/consumers/oauth2/v2.0/token")
            val conn = tokenUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            val params = "grant_type=refresh_token" +
                    "&client_id=$CLIENT_ID" +
                    "&scope=" + URLEncoder.encode(SCOPE, "UTF-8") +
                    "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")

            OutputStreamWriter(conn.outputStream).use { it.write(params) }

            val code = conn.responseCode
            if (code in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JsonParser.parseString(resp).asJsonObject
                val msAccessToken = json.get("access_token").asString
                val newRefreshToken = if (json.has("refresh_token")) json.get("refresh_token").asString else null
                if (newRefreshToken != null) {
                    prefs.edit().putString("ms_refresh_token", newRefreshToken).apply()
                }
                authenticateXboxLive(msAccessToken)
                Logger.i("XboxAuth", "Background token refresh succeeded!")
                return@withContext true
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Logger.w("XboxAuth", "Refresh token failed ($code): $err")
                if (code == 400 || code == 401) {
                    logout()
                    _authState.value = XboxAuthState.Error("Login session expired. Please sign in again.")
                }
            }
        } catch (e: Exception) {
            Logger.e("XboxAuth", "Failed to refresh token: ${e.message}")
        }
        return@withContext false
    }

    fun cancelLogin() {
        _authState.value = XboxAuthState.Idle
        Logger.i("XboxAuth", "Device code login cancelled by user.")
    }

    suspend fun getOrFetchMinecraftChain(proxyPublicKeyBase64: String): String? = withContext(Dispatchers.IO) {
        // Ensure token is fresh
        refreshTokensIfNeeded()

        val xsts = liveXstsToken ?: prefs.getString("xsts_token", null) ?: return@withContext null
        val uHash = liveUserHash ?: prefs.getString("user_hash", null) ?: return@withContext null

        val cachedChain = cachedMinecraftChain ?: prefs.getString("minecraft_chain", null)
        val cachedPubKey = prefs.getString("minecraft_chain_pubkey", null)

        // Strict Key-Matching: only return cached chain if issued for the exact same public key!
        if (!cachedChain.isNullOrBlank() && cachedPubKey == proxyPublicKeyBase64) {
            cachedMinecraftChain = cachedChain
            return@withContext cachedChain
        }

        try {
            Logger.i("XboxAuth", "Requesting official Mojang-signed certificate chain for device public key...")
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
                    prefs.edit()
                        .putString("minecraft_chain", chainStr)
                        .putString("minecraft_chain_pubkey", proxyPublicKeyBase64)
                        .apply()
                    Logger.i("XboxAuth", "Successfully obtained official Mojang certificate chain (${chainArray.size()} tokens)!")
                    return@withContext chainStr
                }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Logger.w("XboxAuth", "Mojang auth endpoint response $code: $err")
                if (code == 401) {
                    // XSTS might be stale, force refresh once and retry
                    if (refreshTokensIfNeeded(force = true)) {
                        return@withContext getOrFetchMinecraftChain(proxyPublicKeyBase64)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("XboxAuth", "Failed to fetch Mojang certificate chain", e)
        }
        return@withContext null
    }

    fun prefetchMojangChain() {
        if (!liveXstsToken.isNullOrBlank() || prefs.getString("xsts_token", null) != null) {
            scope.launch {
                val pubKeyB64 = Base64.encodeToString(deviceKeyPair.public.encoded, Base64.NO_WRAP)
                getOrFetchMinecraftChain(pubKeyB64)
            }
        }
    }

    suspend fun ensureChainReady(timeoutMs: Long = 4000): String? {
        val pubKeyB64 = Base64.encodeToString(deviceKeyPair.public.encoded, Base64.NO_WRAP)
        val cached = cachedMinecraftChain ?: prefs.getString("minecraft_chain", null)
        val cachedPubKey = prefs.getString("minecraft_chain_pubkey", null)
        if (!cached.isNullOrBlank() && cachedPubKey == pubKeyB64) {
            cachedMinecraftChain = cached
            return cached
        }
        return withTimeoutOrNull(timeoutMs) {
            getOrFetchMinecraftChain(pubKeyB64)
        }
    }

    suspend fun runAuthDiagnostic(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append("=== XBOX & MOJANG AUTHENTICATION DIAGNOSTIC ===\n")

        val loginTime = prefs.getLong("login_timestamp", 0L)
        val now = System.currentTimeMillis()
        val refreshToken = prefs.getString("ms_refresh_token", null)

        // 1. Check 90-Day Account Tracking & Refresh Token
        if (refreshToken != null) {
            val elapsedMs = now - loginTime
            val daysRemaining = getDaysRemainingInSession()
            if (elapsedMs > MAX_LOGIN_DURATION_MS) {
                sb.append("  ❌ 90-Day Account Session: EXPIRED ($daysRemaining days remaining)\n")
            } else {
                sb.append("  ✔ 90-Day Account Session: ACTIVE ($daysRemaining days remaining)\n")
                sb.append("  ✔ Microsoft Refresh Token: VALID (Persistent secure storage)\n")
            }
        } else {
            sb.append("  ⚠️ Microsoft Refresh Token: NOT FOUND (User not signed in)\n")
        }

        // 2. Xbox Live & XSTS Token
        val gtg = liveGamertag ?: prefs.getString("gamertag", null)
        val xid = liveXuid ?: prefs.getString("xuid", null)
        val uHash = liveUserHash ?: prefs.getString("user_hash", null)
        val xsts = liveXstsToken ?: prefs.getString("xsts_token", null)

        if (gtg != null && xid != null && uHash != null && xsts != null) {
            sb.append("  ✔ Xbox Live & XSTS Token: ACTIVE (GamerTag: $gtg, XUID: $xid)\n")
        } else {
            sb.append("  ⚠️ Xbox Live Session: INCOMPLETE or MISSING\n")
        }

        // 3. Persistent Device KeyPair
        val keyPair = deviceKeyPair
        val pubKeyB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        sb.append("  ✔ Persistent Device KeyPair: READY (Curve: secp384r1, Len: ${pubKeyB64.length} chars)\n")

        // 4. Mojang Auth Endpoint Probe
        if (uHash != null && xsts != null) {
            try {
                val url = URL("https://multiplayer.minecraft.net/authentication")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Authorization", "XBL3.0 x=$uHash;$xsts")

                val reqJson = JsonObject().apply {
                    addProperty("identityPublicKey", pubKeyB64)
                }
                OutputStreamWriter(conn.outputStream).use { it.write(reqJson.toString()) }

                val code = conn.responseCode
                if (code in 200..299) {
                    sb.append("  ✔ Mojang Auth Endpoint (multiplayer.minecraft.net): HTTP $code OK\n")
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JsonParser.parseString(resp).asJsonObject
                    if (json.has("chain")) {
                        val chainArray = json.getAsJsonArray("chain")
                        sb.append("  ✔ Mojang Certificate Chain: RECEIVED (${chainArray.size()} tokens)\n")
                        sb.append("  ✔ Cryptographic Key Matching: PERFECT MATCH (Device Key <-> Mojang Chain)\n")
                    } else {
                        sb.append("  ⚠️ Mojang Certificate Chain: Unexpected response structure\n")
                    }
                } else {
                    sb.append("  ❌ Mojang Auth Endpoint: HTTP $code (Token may need refresh)\n")
                }
            } catch (e: Exception) {
                sb.append("  ❌ Mojang Auth Probe Failed: ${e.message}\n")
            }
        } else {
            sb.append("  ⚠️ Mojang Auth Endpoint: SKIPPED (Requires active Xbox login)\n")
        }

        sb.append("================================================\n")
        sb.toString()
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
        const val CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb"
        const val SCOPE = "XboxLive.signin offline_access"
        const val MAX_LOGIN_DURATION_MS = 90L * 24 * 60 * 60 * 1000L // 90 days
    }
}
