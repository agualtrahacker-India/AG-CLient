package com.aayaan.agclient.proxy

import com.aayaan.agclient.auth.XboxAuthManager
import com.aayaan.agclient.commands.CommandManager
import com.aayaan.agclient.config.ProxyConfig
import com.aayaan.agclient.gui.InGameGuiManager
import com.aayaan.agclient.modules.ModuleManager
import com.aayaan.agclient.state.WorldStateTracker
import com.aayaan.agclient.utils.Logger
import org.cloudburstmc.protocol.bedrock.BedrockClient
import org.cloudburstmc.protocol.bedrock.BedrockClientSession
import org.cloudburstmc.protocol.bedrock.BedrockServerSession
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.common.PacketSignal
import java.net.InetSocketAddress
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

    val worldState = WorldStateTracker()
    private val tickExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    var cachedClientData: String? = null
    var cachedSkinData: String? = null
    var isConnectedToUpstream: Boolean = false
        private set

    init {
        setupLocalSessionHandler()
        startTickLoop()
    }

    private fun setupLocalSessionHandler() {
        serverSession.setBatchHandler { _, packets ->
            for (packet in packets) {
                val signal = handleClientPacket(packet)
                if (signal == PacketSignal.UNHANDLED) {
                    clientSession?.sendPacket(packet)
                }
            }
        }
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

    private fun handleClientLogin(packet: LoginPacket) {
        Logger.i("ProxySession", "Intercepted LoginPacket. Protocol Version: ${packet.protocolVersion}")

        // Initialize Upstream Client connection
        val targetAddress = InetSocketAddress(config.targetIp, config.targetPort)
        val client = BedrockClient(InetSocketAddress("0.0.0.0", 0))

        client.bind().whenComplete { _, bindErr ->
            if (bindErr != null) {
                Logger.e("ProxySession", "Failed to bind client socket", bindErr)
                sendChatToClient("§c[Proxy] Failed to bind outbound proxy socket!")
                return@whenComplete
            }

            client.connect(targetAddress).whenComplete { session, connErr ->
                if (connErr != null) {
                    Logger.e("ProxySession", "Failed to connect to ${config.targetIp}:${config.targetPort}", connErr)
                    sendChatToClient("§c[Proxy] Connection refused by remote server: ${connErr.message}")
                    return@whenComplete
                }

                clientSession = session
                isConnectedToUpstream = true
                Logger.i("ProxySession", "Connected to remote server: ${config.targetIp}:${config.targetPort}")

                setupRemoteSessionHandler(session)

                // Forward LoginPacket with extracted skin & auth
                session.sendPacket(packet)
            }
        }
    }

    private fun setupRemoteSessionHandler(session: BedrockClientSession) {
        session.setBatchHandler { _, packets ->
            for (packet in packets) {
                val signal = handleServerPacket(packet)
                if (signal == PacketSignal.UNHANDLED) {
                    serverSession.sendPacket(packet)
                }
            }
        }
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
            this.message = message
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
        } catch (_: Exception) {}
        worldState.reset()
        Logger.i("ProxySession", "Proxy session terminated.")
    }
}
