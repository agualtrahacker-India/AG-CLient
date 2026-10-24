package com.aayaan.agclient.proxy

import android.util.Base64
import com.aayaan.agclient.commands.CommandManager
import com.aayaan.agclient.gui.InGameGuiManager
import com.aayaan.agclient.modules.ModuleManager
import com.aayaan.agclient.state.WorldStateTracker
import com.aayaan.agclient.utils.Logger
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.cloudburstmc.protocol.bedrock.BedrockClientSession
import org.cloudburstmc.protocol.bedrock.BedrockServerSession
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.common.PacketSignal
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * AG Client Man-in-the-Middle (MitM) Packet Manager & Encryption Engine (v2.9.0-beta.1).
 *
 * Responsibilities:
 * 1. Intercepts all incoming client packets (Minecraft -> Proxy) and server packets (Server -> Proxy).
 * 2. Manages dynamic ECDH cryptographic key negotiation and derives shared secrets for both legs.
 * 3. Safely activates AES encryption/ciphers on sessions when requested by remote servers.
 * 4. Dispatches packets through CommandManager, InGameGuiManager, WorldStateTracker, and ModuleManager.
 * 5. Provides transparent passthrough fallback for unencrypted/offline sessions.
 */
class PacketManager(
    val proxySession: ProxySession,
    val serverSession: BedrockServerSession,
    val commandManager: CommandManager,
    val guiManager: InGameGuiManager,
    val moduleManager: ModuleManager,
    val worldState: WorldStateTracker
) {

    // Dynamic Ephemeral EC KeyPair for MitM Key Exchange
    private var proxyKeyPair: KeyPair? = null

    // Client and Server Cryptographic Identity Keys
    @Volatile
    private var clientPublicKey: PublicKey? = null
    @Volatile
    private var serverPublicKey: PublicKey? = null
    @Volatile
    private var serverSaltBytes: ByteArray? = null

    // Encryption State Tracking
    @Volatile
    var isClientEncryptionEnabled: Boolean = false
        private set
    @Volatile
    var isServerEncryptionEnabled: Boolean = false
        private set

    // Real-time packet telemetry
    private val packetCounters = ConcurrentHashMap<String, Long>()

    init {
        initializeProxyKeyPair()
    }

    private fun initializeProxyKeyPair() {
        try {
            proxyKeyPair = proxySession.authManager.deviceKeyPair
            Logger.i("PacketManager", "Bound proxy session to persistent device EC KeyPair (${proxyKeyPair?.public?.algorithm})")
        } catch (e: Exception) {
            Logger.w("PacketManager", "Failed to load device KeyPair, generating fallback: ${e.message}")
            try {
                val keyGen = KeyPairGenerator.getInstance("EC")
                keyGen.initialize(ECGenParameterSpec("secp384r1"))
                proxyKeyPair = keyGen.generateKeyPair()
            } catch (_: Exception) {}
        }
    }

    /**
     * Intercept and process packets sent from Minecraft Bedrock app towards the proxy.
     */
    fun onClientPacket(packet: BedrockPacket): PacketSignal {
        val packetName = packet.javaClass.simpleName
        packetCounters.compute(packetName) { _, count -> (count ?: 0L) + 1 }
        Logger.packet("Client->Proxy", packetName)

        // 1. Intercept Network Settings Request (Modern Bedrock handshake)
        if (packet is RequestNetworkSettingsPacket) {
            handleClientNetworkSettingsRequest(packet)
            return PacketSignal.HANDLED
        }

        // 2. Intercept Client Login Packet (Capture identity, public key, skin data)
        if (packet is LoginPacket) {
            handleClientLoginPacket(packet)
            return PacketSignal.HANDLED
        }

        // 3. Intercept Client Encryption Handshake Response
        if (packet is ClientToServerHandshakePacket) {
            handleClientHandshakeResponse(packet)
            return PacketSignal.HANDLED
        }

        // 4. Intercept In-Game Chat Commands (.help, .killaura, etc.)
        try {
            if (commandManager.handleClientPacket(packet, proxySession) == PacketSignal.HANDLED) {
                return PacketSignal.HANDLED
            }
        } catch (t: Throwable) {
            Logger.d("PacketManager", "CommandManager hook: ${t.message}")
        }

        // 5. Intercept Modal Form Responses (In-Game GUI menus)
        try {
            if (guiManager.handleClientPacket(packet, proxySession) == PacketSignal.HANDLED) {
                return PacketSignal.HANDLED
            }
        } catch (t: Throwable) {
            Logger.d("PacketManager", "GuiManager hook: ${t.message}")
        }

        // 6. Update Client World & Player Tracking
        try {
            worldState.handleClientPacket(packet)
        } catch (t: Throwable) {
            Logger.d("PacketManager", "WorldState client hook: ${t.message}")
        }

        // 7. Route through active Cheat/Utility Modules
        try {
            val moduleSignal = moduleManager.onClientPacket(packet, proxySession)
            if (moduleSignal == PacketSignal.HANDLED) {
                return PacketSignal.HANDLED
            }
        } catch (t: Throwable) {
            Logger.d("PacketManager", "ModuleManager client hook: ${t.message}")
        }

        return PacketSignal.UNHANDLED
    }

    /**
     * Intercept and process packets sent from upstream Minecraft server towards the proxy.
     */
    fun onServerPacket(packet: BedrockPacket, clientSession: BedrockClientSession): PacketSignal {
        val packetName = packet.javaClass.simpleName
        packetCounters.compute(packetName) { _, count -> (count ?: 0L) + 1 }
        Logger.packet("Server->Proxy", packetName)

        // 1. Intercept Upstream Network Settings Response
        if (packet is NetworkSettingsPacket) {
            handleUpstreamNetworkSettings(packet, clientSession)
            return PacketSignal.HANDLED
        }

        // 2. Intercept Upstream Encryption Handshake (ServerToClientHandshakePacket)
        if (packet is ServerToClientHandshakePacket) {
            handleServerHandshakeRequest(packet, clientSession)
            return PacketSignal.HANDLED
        }

        // 3. Intercept Server Disconnect
        if (packet is DisconnectPacket) {
            val kick = packet.kickMessage ?: "Unknown reason"
            Logger.w("PacketManager", "[AUTH-ANALYSIS] Upstream server disconnected session: '$kick'")
            if (kick.contains("authenticate", ignoreCase = true) || kick.contains("Xbox", ignoreCase = true) || kick.contains("Microsoft", ignoreCase = true)) {
                Logger.e("PacketManager", "[AUTH-ANALYSIS] Server rejected authentication! Hint: Tap 'Run Auth Diagnostic' on Console tab or re-login in Xbox Auth tab.")
            }
            serverSession.sendPacket(packet)
            proxySession.cleanup()
            return PacketSignal.HANDLED
        }

        // 4. Update World, Entity & Inventory state
        try {
            worldState.handleServerPacket(packet)
        } catch (t: Throwable) {
            Logger.d("PacketManager", "WorldState server hook: ${t.message}")
        }

        // 5. Intercept AvailableCommandsPacket to inject proxy custom commands for in-game autocompletion
        if (packet is AvailableCommandsPacket) {
            try {
                commandManager.injectCustomCommands(packet)
            } catch (t: Throwable) {
                Logger.d("PacketManager", "CommandManager inject hook: ${t.message}")
            }
        }

        // 6. Route through active Cheat/Utility Modules
        try {
            val moduleSignal = moduleManager.onServerPacket(packet, proxySession)
            if (moduleSignal == PacketSignal.HANDLED) {
                return PacketSignal.HANDLED
            }
        } catch (t: Throwable) {
            Logger.d("PacketManager", "ModuleManager server hook: ${t.message}")
        }

        return PacketSignal.UNHANDLED
    }

    // =========================================================================
    // Handshake & MitM Encryption Subroutines
    // =========================================================================

    private fun handleClientNetworkSettingsRequest(packet: RequestNetworkSettingsPacket) {
        val clientProto = packet.protocolVersion
        proxySession.clientProtocolVersion = clientProto
        Logger.i("PacketManager", "Client requested network settings. Protocol Version: $clientProto")

        val matchedCodec = BedrockCodecFinder.findCodec(clientProto)
        if (matchedCodec != null) {
            serverSession.codec = matchedCodec
            Logger.i("PacketManager", "Assigned serverSession codec protocol: ${matchedCodec.protocolVersion}")
        }

        val response = NetworkSettingsPacket().apply {
            compressionAlgorithm = PacketCompressionAlgorithm.ZLIB
            compressionThreshold = 0
        }

        try {
            serverSession.sendPacketImmediately(response)
            serverSession.setCompression(PacketCompressionAlgorithm.ZLIB)
            Logger.i("PacketManager", "Responded with NetworkSettingsPacket (threshold=0) & enabled ZLIB compression.")
        } catch (e: Exception) {
            Logger.e("PacketManager", "Failed to send NetworkSettingsPacket to client", e)
        }

        // Start upstream connection early concurrently with client handshake
        proxySession.connectUpstream()

        // If authenticated with Xbox Live, pre-fetch official Mojang chain in background if not already cached
        val kPair = proxyKeyPair
        if (kPair != null && !proxySession.authManager.liveXstsToken.isNullOrBlank() && proxySession.authManager.cachedMinecraftChain == null) {
            val pubKeyB64 = Base64.encodeToString(kPair.public.encoded, Base64.NO_WRAP)
            proxySession.sessionScope.launch {
                proxySession.authManager.getOrFetchMinecraftChain(pubKeyB64)
            }
        }
    }

    private fun handleClientLoginPacket(packet: LoginPacket) {
        Logger.i("PacketManager", "[AUTH-ANALYSIS] Intercepted Client LoginPacket (Proto: ${packet.protocolVersion})")

        // 1. Extract client's public key from the identity certificate chain
        val extractedKey = extractPublicKeyFromLoginPacket(packet)
        if (extractedKey != null) {
            clientPublicKey = extractedKey
            Logger.i("PacketManager", "[AUTH-ANALYSIS] Extracted client EC public key (${extractedKey.algorithm}, Format: ${extractedKey.format})")
        } else {
            Logger.w("PacketManager", "[AUTH-ANALYSIS] Client public key could not be extracted. Will attempt fallback.")
        }

        // 2. Prepare outbound LoginPacket (re-signing with Xbox Live Mojang chain if active)
        val outbound = prepareOutboundLoginPacket(packet)
        proxySession.cachedLoginPacket = outbound

        // 3. If upstream is already connected, forward LoginPacket immediately!
        val upstream = proxySession.clientSession
        if (proxySession.isConnectedToUpstream && upstream != null && upstream.isConnected) {
            try {
                upstream.sendPacketImmediately(outbound)
                Logger.i("PacketManager", "[AUTH-ANALYSIS] Upstream connected, transmitted outbound LoginPacket to server.")
            } catch (e: Exception) {
                Logger.e("PacketManager", "[AUTH-ANALYSIS] Failed to forward LoginPacket: ${e.message}")
            }
        } else {
            proxySession.connectUpstream()
        }
    }

    private fun handleUpstreamNetworkSettings(packet: NetworkSettingsPacket, clientSession: BedrockClientSession) {
        Logger.i("PacketManager", "[AUTH-ANALYSIS] Upstream server replied with NetworkSettingsPacket (${packet.compressionAlgorithm})")
        try {
            clientSession.setCompression(packet.compressionAlgorithm)
        } catch (e: Exception) {
            Logger.w("PacketManager", "Could not set upstream compression: ${e.message}")
        }

        // Forward player's cached LoginPacket to remote server
        proxySession.cachedLoginPacket?.let { login ->
            val outbound = prepareOutboundLoginPacket(login)
            clientSession.sendPacketImmediately(outbound)
            Logger.i("PacketManager", "[AUTH-ANALYSIS] Transmitted outbound LoginPacket to remote server.")
        }
    }

    private fun handleServerHandshakeRequest(packet: ServerToClientHandshakePacket, clientSession: BedrockClientSession) {
        Logger.i("PacketManager", "[AUTH-ANALYSIS] Intercepted ServerToClientHandshakePacket from upstream server.")

        val jwt = packet.jwt
        if (jwt.isNullOrBlank()) {
            Logger.w("PacketManager", "Handshake JWT is empty, forwarding raw packet to client.")
            serverSession.sendPacketImmediately(packet)
            return
        }

        try {
            val parts = jwt.split(".")
            if (parts.size >= 2) {
                val headerJsonStr = String(Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                val headerObj = JsonParser.parseString(headerJsonStr).asJsonObject

                val payloadJsonStr = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                val payloadObj = JsonParser.parseString(payloadJsonStr).asJsonObject

                val serverX5u = if (headerObj.has("x5u")) headerObj.get("x5u").asString else null
                val saltStr = if (payloadObj.has("salt")) payloadObj.get("salt").asString else null

                if (!serverX5u.isNullOrBlank() && !saltStr.isNullOrBlank()) {
                    val sPubKey = parsePublicKey(serverX5u)
                    val sSalt = Base64.decode(saltStr, Base64.DEFAULT)
                    serverPublicKey = sPubKey
                    serverSaltBytes = sSalt

                    val kPair = proxyKeyPair
                    if (sPubKey != null && kPair != null) {
                        // 1. Derive shared secret for upstream leg: ECDH(proxyPrivate, serverPublic) + salt
                        val upstreamSecret = deriveSharedSecret(kPair.private, sPubKey, sSalt)
                        if (upstreamSecret != null) {
                            val enabled = enableSessionCipher(clientSession, upstreamSecret)
                            if (enabled) {
                                isServerEncryptionEnabled = true
                                Logger.i("PacketManager", "[AUTH-ANALYSIS] Activated AES encryption on upstream connection to server.")
                            }
                        }

                        // 2. Craft proxy Handshake JWT for Minecraft client: ECDH(clientPrivate, proxyPublic) + salt
                        val proxyHandshakeJwt = craftHandshakeJwt(kPair.public, kPair.private, saltStr)
                        if (proxyHandshakeJwt != null) {
                            val proxyHandshakePacket = ServerToClientHandshakePacket().apply {
                                setJwt(proxyHandshakeJwt)
                            }
                            serverSession.sendPacketImmediately(proxyHandshakePacket)
                            Logger.i("PacketManager", "[AUTH-ANALYSIS] Crafted & transmitted MitM ServerToClientHandshakePacket to Minecraft client (with JOSE signature).")
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w("PacketManager", "MitM handshake negotiation failed: ${e.message}. Falling back to transparent forward.")
        }

        // Fallback: pass handshake directly through if MitM crafting cannot proceed
        serverSession.sendPacketImmediately(packet)
    }

    private fun handleClientHandshakeResponse(packet: ClientToServerHandshakePacket) {
        Logger.i("PacketManager", "[AUTH-ANALYSIS] Intercepted ClientToServerHandshakePacket from Minecraft client.")

        val cPubKey = clientPublicKey
        val kPair = proxyKeyPair
        val salt = serverSaltBytes

        if (cPubKey != null && kPair != null && salt != null) {
            try {
                // Derive shared secret for downstream leg: ECDH(proxyPrivate, clientPublic) + salt
                val downstreamSecret = deriveSharedSecret(kPair.private, cPubKey, salt)
                if (downstreamSecret != null) {
                    val enabled = enableSessionCipher(serverSession, downstreamSecret)
                    if (enabled) {
                        isClientEncryptionEnabled = true
                        Logger.i("PacketManager", "[AUTH-ANALYSIS] Activated AES encryption on local Minecraft client session.")
                    }
                }
            } catch (e: Exception) {
                Logger.w("PacketManager", "[AUTH-ANALYSIS] Downstream cipher initialization failed: ${e.message}")
            }
        } else {
            Logger.w("PacketManager", "[AUTH-ANALYSIS] Downstream cipher bypassed: cPubKey=${cPubKey != null}, kPair=${kPair != null}, salt=${salt != null}")
        }

        // Forward handshake confirmation upstream to target server
        proxySession.clientSession?.sendPacketImmediately(packet)
        Logger.i("PacketManager", "[AUTH-ANALYSIS] Forwarded ClientToServerHandshake upstream to target server.")
    }

    // =========================================================================
    // Cryptography Helpers
    // =========================================================================

    private fun deriveSharedSecret(privateKey: PrivateKey, publicKey: PublicKey, salt: ByteArray): SecretKey? {
        return try {
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(publicKey, true)
            val sharedSecretBytes = keyAgreement.generateSecret()

            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            val keyBytes = digest.digest(sharedSecretBytes)

            SecretKeySpec(keyBytes, "AES")
        } catch (e: Exception) {
            Logger.e("PacketManager", "ECDH key agreement / derivation error", e)
            null
        }
    }

    private fun craftHandshakeJwt(publicKey: PublicKey, privateKey: PrivateKey, saltStr: String): String? {
        return try {
            val pubKeyBase64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
            val header = JsonObject().apply {
                addProperty("alg", "ES384")
                addProperty("x5u", pubKeyBase64)
            }
            val payload = JsonObject().apply {
                addProperty("salt", saltStr)
            }

            val headerB64 = Base64.encodeToString(header.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val payloadB64 = Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val signingInput = "$headerB64.$payloadB64"

            val signature = Signature.getInstance("SHA384withECDSA").apply {
                initSign(privateKey)
                update(signingInput.toByteArray(Charsets.US_ASCII))
            }
            // ES384 in JWT requires 96-byte raw JOSE format (R||S), not DER!
            val sigBytes = derToJose(signature.sign(), 48)
            val sigB64 = Base64.encodeToString(sigBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

            "$signingInput.$sigB64"
        } catch (e: Exception) {
            Logger.e("PacketManager", "Failed to craft handshake JWT", e)
            null
        }
    }

    private fun enableSessionCipher(session: Any, secretKey: SecretKey): Boolean {
        return try {
            // Priority 1: Direct enableEncryption(SecretKey) method
            val secretKeyMethod = session.javaClass.getMethod("enableEncryption", SecretKey::class.java)
            secretKeyMethod.invoke(session, secretKey)
            true
        } catch (_: NoSuchMethodException) {
            try {
                // Priority 2: enableEncryption(byte[]) overload
                val byteMethod = session.javaClass.getMethod("enableEncryption", ByteArray::class.java)
                byteMethod.invoke(session, secretKey.encoded)
                true
            } catch (e2: Exception) {
                Logger.w("PacketManager", "enableEncryption method not found on ${session.javaClass.simpleName}: ${e2.message}")
                false
            }
        } catch (t: Throwable) {
            Logger.e("PacketManager", "Error invoking enableEncryption on session", t)
            false
        }
    }

    private fun parsePublicKey(base64Key: String): PublicKey? {
        return try {
            val keyBytes = Base64.decode(base64Key, Base64.DEFAULT)
            val spec = X509EncodedKeySpec(keyBytes)
            val factory = KeyFactory.getInstance("EC")
            factory.generatePublic(spec)
        } catch (e: Exception) {
            Logger.d("PacketManager", "X509 EC public key parse failed: ${e.message}")
            null
        }
    }

    private fun extractPublicKeyFromLoginPacket(login: LoginPacket): PublicKey? {
        try {
            var rawChainObj: Any? = null
            val candidateMethods = listOf("getChain", "getChainData", "getAuthData")
            for (name in candidateMethods) {
                try {
                    val method = login.javaClass.getMethod(name)
                    rawChainObj = method.invoke(login)
                    if (rawChainObj != null) break
                } catch (_: Exception) {}
            }
            if (rawChainObj == null) {
                val candidateFields = listOf("chain", "chainData", "authData", "tokens")
                for (name in candidateFields) {
                    try {
                        val field = login.javaClass.getDeclaredField(name)
                        field.isAccessible = true
                        rawChainObj = field.get(login)
                        if (rawChainObj != null) break
                    } catch (_: Exception) {}
                }
            }
            if (rawChainObj == null) {
                for (field in login.javaClass.declaredFields) {
                    try {
                        field.isAccessible = true
                        val value = field.get(login)
                        if (value is Iterable<*> || value is Array<*> || (value is CharSequence && value.contains("chain"))) {
                            rawChainObj = value
                            break
                        }
                    } catch (_: Exception) {}
                }
            }

            val tokens = mutableListOf<String>()
            when (rawChainObj) {
                is Iterable<*> -> rawChainObj.forEach { it?.let { tokens.add(it.toString()) } }
                is Array<*> -> rawChainObj.forEach { it?.let { tokens.add(it.toString()) } }
                is CharSequence -> {
                    val str = rawChainObj.toString().trim()
                    if (str.startsWith("{")) {
                        try {
                            val json = JsonParser.parseString(str).asJsonObject
                            if (json.has("chain")) {
                                json.getAsJsonArray("chain").forEach { tokens.add(it.asString) }
                            }
                        } catch (_: Exception) {}
                    } else if (str.contains(".")) {
                        tokens.add(str)
                    }
                }
            }

            // In Bedrock's certificate chain:
            // The client's public key is in the token containing "extraData" (the player identity claim)
            // or the last non-CA token. The first token contains Mojang's Root CA key!
            var fallbackKey: PublicKey? = null
            for (token in tokens.reversed()) {
                val key = parseKeyFromJwtString(token)
                if (key != null) {
                    fallbackKey = key
                    if (tokenHasExtraData(token)) {
                        return key
                    }
                }
            }
            if (fallbackKey != null) return fallbackKey

        } catch (e: Exception) {
            Logger.d("PacketManager", "extractPublicKeyFromLoginPacket error: ${e.message}")
        }
        return null
    }

    private fun tokenHasExtraData(jwt: String): Boolean {
        return try {
            val parts = jwt.split(".")
            if (parts.size >= 2) {
                val payloadJsonStr = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                val payload = JsonParser.parseString(payloadJsonStr).asJsonObject
                payload.has("extraData")
            } else false
        } catch (_: Exception) { false }
    }

    private fun parseKeyFromJwtString(jwt: String?): PublicKey? {
        if (jwt.isNullOrBlank()) return null
        return try {
            val parts = jwt.split(".")
            if (parts.size >= 2) {
                // Try payload first
                val payloadJsonStr = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                val payload = JsonParser.parseString(payloadJsonStr).asJsonObject
                if (payload.has("identityPublicKey")) {
                    return parsePublicKey(payload.get("identityPublicKey").asString)
                }

                // Try header
                val headerJsonStr = String(Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                val header = JsonParser.parseString(headerJsonStr).asJsonObject
                if (header.has("x5u")) {
                    return parsePublicKey(header.get("x5u").asString)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun derToJose(der: ByteArray, keySize: Int = 48): ByteArray {
        if (der.size < 8 || der[0] != 0x30.toByte()) return der
        var offset = 2
        if (der[1].toInt() and 0x80 != 0) {
            offset += (der[1].toInt() and 0x7F)
        }
        if (offset >= der.size || der[offset] != 0x02.toByte()) return der
        val rLen = der[offset + 1].toInt() and 0xFF
        val rStart = offset + 2
        val sTypeOffset = rStart + rLen
        if (sTypeOffset >= der.size || der[sTypeOffset] != 0x02.toByte()) return der
        val sLen = der[sTypeOffset + 1].toInt() and 0xFF
        val sStart = sTypeOffset + 2

        val jose = ByteArray(keySize * 2)
        val rSrc = if (rLen > keySize) rStart + (rLen - keySize) else rStart
        val rCount = rLen.coerceAtMost(keySize)
        val rDst = keySize - rCount
        System.arraycopy(der, rSrc, jose, rDst, rCount)

        val sSrc = if (sLen > keySize) sStart + (sLen - keySize) else sStart
        val sCount = sLen.coerceAtMost(keySize)
        val sDst = keySize + (keySize - sCount)
        System.arraycopy(der, sSrc, jose, sDst, sCount)

        return jose
    }

    private fun getClientDataJwt(login: LoginPacket): String? {
        val candidateMethods = listOf("getExtra", "getClientData", "getSkinData")
        for (name in candidateMethods) {
            try {
                val method = login.javaClass.getMethod(name)
                val res = method.invoke(login)
                if (res != null) {
                    val str = res.toString()
                    if (str.contains(".")) return str
                }
            } catch (_: Exception) {}
        }
        val candidateFields = listOf("extra", "clientData", "skinData", "extraJwt")
        for (name in candidateFields) {
            try {
                val field = login.javaClass.getDeclaredField(name)
                field.isAccessible = true
                val res = field.get(login)
                if (res != null) {
                    val str = res.toString()
                    if (str.contains(".")) return str
                }
            } catch (_: Exception) {}
        }
        for (field in login.javaClass.declaredFields) {
            try {
                field.isAccessible = true
                val value = field.get(login)
                if (value != null) {
                    val str = value.toString()
                    if (str.count { it == '.' } == 2 && str.length > 50) {
                        return str
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun reSignClientDataJwt(rawJwt: String, privateKey: PrivateKey): String? {
        return try {
            val parts = rawJwt.split(".")
            if (parts.size < 2) return null
            val headerB64 = parts[0]
            val payloadB64 = parts[1]
            val signingInput = "$headerB64.$payloadB64"

            val signature = Signature.getInstance("SHA384withECDSA").apply {
                initSign(privateKey)
                update(signingInput.toByteArray(Charsets.US_ASCII))
            }
            val sigBytes = derToJose(signature.sign(), 48)
            val sigB64 = Base64.encodeToString(sigBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

            "$signingInput.$sigB64"
        } catch (e: Exception) {
            Logger.e("PacketManager", "Failed to re-sign clientData JWT: ${e.message}")
            null
        }
    }

    private fun setLoginChainAndExtra(login: LoginPacket, chainList: List<String>, extraJwt: String) {
        var chainSet = false
        for (method in login.javaClass.methods) {
            if (method.name.startsWith("set") && (method.name.contains("Chain", ignoreCase = true) || method.name.contains("Auth", ignoreCase = true))) {
                val paramType = method.parameterTypes.firstOrNull() ?: continue
                try {
                    when {
                        paramType.isAssignableFrom(List::class.java) -> {
                            method.invoke(login, chainList)
                            chainSet = true
                            break
                        }
                        paramType == String::class.java -> {
                            val chainJson = JsonObject().apply {
                                val arr = JsonArray()
                                chainList.forEach { arr.add(it) }
                                add("chain", arr)
                            }.toString()
                            method.invoke(login, chainJson)
                            chainSet = true
                            break
                        }
                        paramType.name.contains("AsciiString") -> {
                            val asciiClass = Class.forName("io.netty.util.AsciiString")
                            val ofMethod = asciiClass.getMethod("of", CharSequence::class.java)
                            val asciiList = chainList.map { ofMethod.invoke(null, it) }
                            method.invoke(login, asciiList)
                            chainSet = true
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        if (!chainSet) {
            for (field in login.javaClass.declaredFields) {
                if (field.name.contains("chain", ignoreCase = true) || field.name.contains("auth", ignoreCase = true)) {
                    try {
                        field.isAccessible = true
                        when {
                            field.type.isAssignableFrom(List::class.java) -> field.set(login, chainList)
                            field.type == String::class.java -> field.set(login, chainList.joinToString(","))
                            field.type.name.contains("AsciiString") -> {
                                val asciiClass = Class.forName("io.netty.util.AsciiString")
                                val ofMethod = asciiClass.getMethod("of", CharSequence::class.java)
                                val asciiList = chainList.map { ofMethod.invoke(null, it) }
                                field.set(login, asciiList)
                            }
                        }
                        break
                    } catch (_: Exception) {}
                }
            }
        }

        var extraSet = false
        for (method in login.javaClass.methods) {
            if (method.name.startsWith("set") && (method.name.contains("Extra", ignoreCase = true) || method.name.contains("Skin", ignoreCase = true) || method.name.contains("ClientData", ignoreCase = true))) {
                val paramType = method.parameterTypes.firstOrNull() ?: continue
                try {
                    if (paramType == String::class.java || paramType == CharSequence::class.java) {
                        method.invoke(login, extraJwt)
                        extraSet = true
                        break
                    } else if (paramType.name.contains("AsciiString")) {
                        val asciiClass = Class.forName("io.netty.util.AsciiString")
                        val ofMethod = asciiClass.getMethod("of", CharSequence::class.java)
                        method.invoke(login, ofMethod.invoke(null, extraJwt))
                        extraSet = true
                        break
                    }
                } catch (_: Exception) {}
            }
        }

        if (!extraSet) {
            for (field in login.javaClass.declaredFields) {
                if (field.name.contains("extra", ignoreCase = true) || field.name.contains("skin", ignoreCase = true) || field.name.contains("clientData", ignoreCase = true)) {
                    try {
                        field.isAccessible = true
                        if (field.type == String::class.java || field.type == CharSequence::class.java) {
                            field.set(login, extraJwt)
                        } else if (field.type.name.contains("AsciiString")) {
                            val asciiClass = Class.forName("io.netty.util.AsciiString")
                            val ofMethod = asciiClass.getMethod("of", CharSequence::class.java)
                            field.set(login, ofMethod.invoke(null, extraJwt))
                        }
                        break
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun prepareOutboundLoginPacket(incoming: LoginPacket): LoginPacket {
        val authManager = proxySession.authManager
        val kPair = proxyKeyPair

        if (!authManager.liveXstsToken.isNullOrBlank() && kPair != null) {
            try {
                // Ensure Mojang chain is ready (synchronously awaits up to 4s if background fetch was still pending)
                var mojangChainJson = authManager.cachedMinecraftChain
                if (mojangChainJson.isNullOrBlank()) {
                    Logger.i("PacketManager", "[AUTH-ANALYSIS] Mojang chain not in memory, awaiting background fetch...")
                    mojangChainJson = runBlocking {
                        authManager.ensureChainReady(4000)
                    }
                }

                if (!mojangChainJson.isNullOrBlank()) {
                    val chainArray = JsonParser.parseString(mojangChainJson).asJsonArray
                    val chainList = mutableListOf<String>()
                    chainArray.forEach { chainList.add(it.asString) }

                    // Extract original skin/clientData from incoming LoginPacket
                    val rawClientDataJwt = getClientDataJwt(incoming)
                    val reSignedClientDataJwt = if (!rawClientDataJwt.isNullOrBlank()) {
                        reSignClientDataJwt(rawClientDataJwt, kPair.private)
                    } else null

                    if (chainList.isNotEmpty() && reSignedClientDataJwt != null) {
                        val outbound = LoginPacket().apply {
                            protocolVersion = incoming.protocolVersion
                        }
                        setLoginChainAndExtra(outbound, chainList, reSignedClientDataJwt)
                        Logger.i("PacketManager", "[AUTH-ANALYSIS] Prepared official Mojang-signed LoginPacket for The Hive (Preserving Minecraft skin & model).")
                        return outbound
                    } else {
                        Logger.w("PacketManager", "[AUTH-ANALYSIS] Missing components: chainTokens=${chainList.size}, clientDataSigned=${reSignedClientDataJwt != null}")
                    }
                } else {
                    Logger.w("PacketManager", "[AUTH-ANALYSIS] Mojang chain unavailable. Passing original client LoginPacket.")
                }
            } catch (e: Exception) {
                Logger.w("PacketManager", "Error preparing authenticated LoginPacket: ${e.message}, using incoming packet.")
            }
        }

        return incoming
    }
}
