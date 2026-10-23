package com.aayaan.agclient.proxy

import android.util.Base64
import com.aayaan.agclient.commands.CommandManager
import com.aayaan.agclient.gui.InGameGuiManager
import com.aayaan.agclient.modules.ModuleManager
import com.aayaan.agclient.state.WorldStateTracker
import com.aayaan.agclient.utils.Logger
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
            val keyGen = KeyPairGenerator.getInstance("EC")
            try {
                keyGen.initialize(ECGenParameterSpec("secp384r1"))
            } catch (_: Exception) {
                // Fallback to secp256r1 if 384-bit curve is unavailable
                keyGen.initialize(ECGenParameterSpec("secp256r1"))
            }
            proxyKeyPair = keyGen.generateKeyPair()
            Logger.i("PacketManager", "Generated MitM EC KeyPair (${proxyKeyPair?.public?.algorithm})")
        } catch (e: Exception) {
            Logger.w("PacketManager", "Failed to generate EC KeyPair, standard passthrough will be used: ${e.message}")
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
            Logger.w("PacketManager", "Upstream server requested disconnect: ${packet.kickMessage}")
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
            compressionThreshold = 512
        }

        try {
            serverSession.sendPacketImmediately(response)
            serverSession.setCompression(PacketCompressionAlgorithm.ZLIB)
            Logger.i("PacketManager", "Responded with NetworkSettingsPacket & enabled ZLIB compression.")
        } catch (e: Exception) {
            Logger.e("PacketManager", "Failed to send NetworkSettingsPacket to client", e)
        }
    }

    private fun handleClientLoginPacket(packet: LoginPacket) {
        Logger.i("PacketManager", "Intercepted LoginPacket (Proto: ${packet.protocolVersion})")
        proxySession.cachedLoginPacket = packet

        // Extract client's public key from the identity certificate chain
        val extractedKey = extractPublicKeyFromLoginPacket(packet)
        if (extractedKey != null) {
            clientPublicKey = extractedKey
            Logger.i("PacketManager", "Extracted client public key for MitM derivation (${extractedKey.algorithm})")
        } else {
            Logger.w("PacketManager", "Could not extract client public key from LoginPacket. Using transparent handshake.")
        }

        // Trigger upstream connection
        proxySession.connectUpstream()
    }

    private fun handleUpstreamNetworkSettings(packet: NetworkSettingsPacket, clientSession: BedrockClientSession) {
        Logger.i("PacketManager", "Upstream server replied with NetworkSettingsPacket (${packet.compressionAlgorithm})")
        try {
            clientSession.setCompression(packet.compressionAlgorithm)
        } catch (e: Exception) {
            Logger.w("PacketManager", "Could not set upstream compression: ${e.message}")
        }

        // Forward player's cached LoginPacket to remote server
        proxySession.cachedLoginPacket?.let { login ->
            clientSession.sendPacketImmediately(login)
            Logger.i("PacketManager", "Forwarded player's LoginPacket upstream.")
        }
    }

    private fun handleServerHandshakeRequest(packet: ServerToClientHandshakePacket, clientSession: BedrockClientSession) {
        Logger.i("PacketManager", "Intercepted ServerToClientHandshakePacket from upstream server.")

        val jwt = packet.jwt
        if (jwt.isNullOrBlank()) {
            Logger.w("PacketManager", "Handshake JWT is empty, forwarding raw packet to client.")
            serverSession.sendPacketImmediately(packet)
            return
        }

        try {
            val parts = jwt.split(".")
            if (parts.size >= 2) {
                // Header (contains server public key in x5u)
                val headerJsonStr = String(Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                val headerObj = JsonParser.parseString(headerJsonStr).asJsonObject

                // Payload (contains salt)
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
                                Logger.i("PacketManager", "Activated AES encryption on upstream connection to server.")
                            }
                        }

                        // 2. Craft proxy Handshake JWT for Minecraft client: ECDH(clientPrivate, proxyPublic) + salt
                        val proxyHandshakeJwt = craftHandshakeJwt(kPair.public, kPair.private, saltStr)
                        if (proxyHandshakeJwt != null) {
                            val proxyHandshakePacket = ServerToClientHandshakePacket().apply {
                                setJwt(proxyHandshakeJwt)
                            }
                            serverSession.sendPacketImmediately(proxyHandshakePacket)
                            Logger.i("PacketManager", "Crafted & transmitted MitM ServerToClientHandshakePacket to Minecraft client.")
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
        Logger.i("PacketManager", "Intercepted ClientToServerHandshakePacket from Minecraft client.")

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
                        Logger.i("PacketManager", "Activated AES encryption on local Minecraft client session.")
                    }
                }
            } catch (e: Exception) {
                Logger.w("PacketManager", "Downstream cipher initialization failed: ${e.message}")
            }
        }

        // Forward handshake confirmation upstream to target server
        proxySession.clientSession?.sendPacketImmediately(packet)
        Logger.i("PacketManager", "Forwarded ClientToServerHandshake upstream to target server.")
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
            val sigBytes = signature.sign()
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
            val chainData = try {
                val method = login.javaClass.getMethod("getChain")
                method.invoke(login)
            } catch (_: Exception) {
                try {
                    val field = login.javaClass.getDeclaredField("chain")
                    field.isAccessible = true
                    field.get(login)
                } catch (_: Exception) { null }
            }

            if (chainData is Iterable<*>) {
                for (item in chainData) {
                    val key = parseKeyFromJwtString(item?.toString())
                    if (key != null) return key
                }
            } else if (chainData is Array<*>) {
                for (item in chainData) {
                    val key = parseKeyFromJwtString(item?.toString())
                    if (key != null) return key
                }
            }
        } catch (e: Exception) {
            Logger.d("PacketManager", "extractPublicKeyFromLoginPacket error: ${e.message}")
        }
        return null
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
}
