package com.aayaan.agclient.proxy

import com.aayaan.agclient.auth.XboxAuthManager
import com.aayaan.agclient.commands.CommandManager
import com.aayaan.agclient.config.ProxyConfig
import com.aayaan.agclient.gui.InGameGuiManager
import com.aayaan.agclient.modules.ModuleManager
import com.aayaan.agclient.state.WorldStateTracker
import com.aayaan.agclient.utils.Logger
import io.netty.bootstrap.Bootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory
import org.cloudburstmc.protocol.bedrock.BedrockClientSession
import org.cloudburstmc.protocol.bedrock.BedrockServerSession
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.common.PacketSignal
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ProxySession(
    val serverSession: BedrockServerSession, // Local connection to Minecraft Bedrock App
    val config: ProxyConfig,
    val authManager: XboxAuthManager,
    val moduleManager: ModuleManager,
    val commandManager: CommandManager,
    val guiManager: InGameGuiManager
) {

    var clientSession: BedrockClientSession? = null // Remote connection to Target Minecraft Server
        private set

    private var clientEventLoopGroup: NioEventLoopGroup? = null
    val worldState = WorldStateTracker()
    private val tickExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    var cachedClientData: String? = null
    var cachedSkinData: String? = null
    var cachedLoginPacket: LoginPacket? = null
    var clientProtocolVersion: Int = 671
    var isConnectedToUpstream: Boolean = false
        private set

    init {
        setupLocalSessionHandler()
        startTickLoop()
    }

    private fun setupLocalSessionHandler() {
        serverSession.setPacketHandler(object : BedrockPacketHandler {
            override fun handlePacket(packet: BedrockPacket): PacketSignal {
                val signal = handleClientPacket(packet)
                if (signal == PacketSignal.UNHANDLED) {
                    clientSession?.sendPacket(packet)
                }
                return PacketSignal.HANDLED
            }
        })
    }

    private fun startTickLoop() {
        tickExecutor.scheduleAtFixedRate({
            try {
                moduleManager.onTick(this)
            } catch (e: Exception) {
                Logger.e("ProxySession", "Error in proxy session tick", e)
            }
        }, 50, 50, TimeUnit.MILLISECONDS) // 20 ticks per second
    }

    fun handleClientPacket(packet: BedrockPacket): PacketSignal {
        Logger.packet("Client->Proxy", packet.javaClass.simpleName)

        // 0. Intercept Network Settings Request (Modern Bedrock handshake)
        if (packet is RequestNetworkSettingsPacket) {
            handleNetworkSettingsRequest(packet)
            return PacketSignal.HANDLED
        }

        // 1. Intercept Commands (Private Chat Filter)
        if (commandManager.handleClientPacket(packet, this) == PacketSignal.HANDLED) {
            return PacketSignal.HANDLED
        }

        // 2. Intercept Modal Form Responses (GUI)
        if (guiManager.handleClientPacket(packet, this) == PacketSignal.HANDLED) {
            return PacketSignal.HANDLED
        }

        // 3. Update World & Player state
        worldState.handleClientPacket(packet)

        // 4. Intercept Initial Login Packet to connect upstream
        if (packet is LoginPacket) {
            handleClientLogin(packet)
            return PacketSignal.HANDLED
        }

        // 5. Run through active modules
        val moduleSignal = moduleManager.onClientPacket(packet, this)
        if (moduleSignal == PacketSignal.HANDLED) {
            return PacketSignal.HANDLED
        }

        return PacketSignal.UNHANDLED
    }

    private fun handleNetworkSettingsRequest(packet: RequestNetworkSettingsPacket) {
        clientProtocolVersion = packet.protocolVersion
        Logger.i("ProxySession", "Client requested network settings. Protocol Version: $clientProtocolVersion")

        val response = NetworkSettingsPacket().apply {
            compressionAlgorithm = PacketCompressionAlgorithm.ZLIB
            compressionThreshold = 512
        }

        try {
            serverSession.sendPacketImmediately(response)
            serverSession.setCompression(PacketCompressionAlgorithm.ZLIB)
            Logger.i("ProxySession", "Responded with NetworkSettingsPacket & enabled ZLIB compression.")
        } catch (e: Exception) {
            Logger.e("ProxySession", "Failed to send NetworkSettingsPacket", e)
        }
    }

    private fun handleClientLogin(packet: LoginPacket) {
        Logger.i("ProxySession", "Intercepted LoginPacket. Protocol Version: ${packet.protocolVersion}")
        cachedLoginPacket = packet

        // Initialize Upstream Client connection via Netty RakNet Bootstrap
        val targetAddress = InetSocketAddress(config.targetIp, config.targetPort)
        val group = NioEventLoopGroup(2)
        clientEventLoopGroup = group

        val future = CompletableFuture<BedrockClientSession>()

        val bootstrap = Bootstrap()
            .channelFactory(RakChannelFactory.client(NioDatagramChannel::class.java))
            .group(group)
            .handler(object : BedrockClientInitializer() {
                override fun initSession(session: BedrockClientSession) {
                    future.complete(session)
                }
            })

        bootstrap.connect(targetAddress).addListener { connFuture ->
            if (!connFuture.isSuccess) {
                future.completeExceptionally(connFuture.cause())
            }
        }

        future.whenComplete { session, connErr ->
            if (connErr != null) {
                Logger.e("ProxySession", "Failed to connect to ${config.targetIp}:${config.targetPort}", connErr)
                sendChatToClient("§c[Proxy] Connection refused by remote server: ${connErr.message}")
                return@whenComplete
            }

            clientSession = session
            isConnectedToUpstream = true
            Logger.i("ProxySession", "Connected to remote server: ${config.targetIp}:${config.targetPort}")

            // Sync codec with local client session
            try {
                session.codec = serverSession.codec
            } catch (_: Exception) {}

            setupRemoteSessionHandler(session)

            // Request network settings from upstream server
            val upstreamReq = RequestNetworkSettingsPacket().apply {
                protocolVersion = clientProtocolVersion
            }
            try {
                session.sendPacketImmediately(upstreamReq)
                Logger.i("ProxySession", "Sent RequestNetworkSettingsPacket ($clientProtocolVersion) to upstream server.")
            } catch (e: Exception) {
                Logger.e("ProxySession", "Failed to send RequestNetworkSettingsPacket upstream", e)
                session.sendPacket(packet)
            }
        }
    }

    private fun setupRemoteSessionHandler(session: BedrockClientSession) {
        session.setPacketHandler(object : BedrockPacketHandler {
            override fun handlePacket(packet: BedrockPacket): PacketSignal {
                if (packet is NetworkSettingsPacket) {
                    Logger.i("ProxySession", "Upstream server replied with NetworkSettingsPacket: ${packet.compressionAlgorithm}")
                    try {
                        session.setCompression(packet.compressionAlgorithm)
                    } catch (e: Exception) {
                        Logger.w("ProxySession", "Could not set upstream compression: ${e.message}")
                    }

                    // Upstream is ready for authentication! Send cached LoginPacket
                    cachedLoginPacket?.let { login ->
                        session.sendPacket(login)
                        Logger.i("ProxySession", "Forwarded player's LoginPacket to upstream server.")
                    }
                    return PacketSignal.HANDLED
                }

                val signal = handleServerPacket(packet)
                if (signal == PacketSignal.UNHANDLED) {
                    serverSession.sendPacket(packet)
                }
                return PacketSignal.HANDLED
            }
        })
    }

    fun handleServerPacket(packet: BedrockPacket): PacketSignal {
        Logger.packet("Server->Proxy", packet.javaClass.simpleName)

        // 1. Update World & Entity state
        worldState.handleServerPacket(packet)

        // 2. Intercept AvailableCommandsPacket to inject proxy custom commands for autocompletion
        if (packet is AvailableCommandsPacket) {
            commandManager.injectCustomCommands(packet)
        }

        // 3. Run through active modules
        val moduleSignal = moduleManager.onServerPacket(packet, this)
        if (moduleSignal == PacketSignal.HANDLED) {
            return PacketSignal.HANDLED
        }

        return PacketSignal.UNHANDLED
    }

    fun sendToClient(packet: BedrockPacket) {
        serverSession.sendPacket(packet)
    }

    fun sendToServer(packet: BedrockPacket) {
        clientSession?.sendPacket(packet)
    }

    fun sendChatToClient(message: String) {
        if (config.isGhostMode) return // Ghost mode suppresses proxy messages
        val textPacket = TextPacket().apply {
            type = TextPacket.Type.RAW
            setMessage(message)
            isNeedsTranslation = false
            xuid = ""
            sourceName = ""
        }
        serverSession.sendPacket(textPacket)
    }

    fun close() {
        tickExecutor.shutdownNow()
        try {
            clientSession?.disconnect()
            serverSession.disconnect()
            clientEventLoopGroup?.shutdownGracefully()
        } catch (_: Exception) {}
        worldState.reset()
        Logger.i("ProxySession", "Proxy session terminated.")
    }
}
