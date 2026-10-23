package com.aayaan.agclient.proxy

import com.aayaan.agclient.auth.XboxAuthManager
import com.aayaan.agclient.commands.CommandManager
import com.aayaan.agclient.config.ConfigManager
import com.aayaan.agclient.config.ProxyConfig
import com.aayaan.agclient.gui.InGameGuiManager
import com.aayaan.agclient.modules.ModuleManager
import com.aayaan.agclient.state.WorldStateTracker
import com.aayaan.agclient.utils.Logger
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory
import org.cloudburstmc.protocol.bedrock.BedrockClientSession
import org.cloudburstmc.protocol.bedrock.BedrockServerSession
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.common.PacketSignal
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ProxySession(
    val serverSession: BedrockServerSession, // Local connection to Minecraft Bedrock App
    val config: ProxyConfig,
    val configManager: ConfigManager,
    val authManager: XboxAuthManager,
    val moduleManager: ModuleManager,
    val commandManager: CommandManager,
    val guiManager: InGameGuiManager
) {

    var clientSession: BedrockClientSession? = null // Remote connection to Target Minecraft Server
        private set

    private var clientEventLoopGroup: NioEventLoopGroup? = null
    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val worldState = WorldStateTracker()
    private val tickExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    val packetManager: PacketManager = PacketManager(
        proxySession = this,
        serverSession = serverSession,
        commandManager = commandManager,
        guiManager = guiManager,
        moduleManager = moduleManager,
        worldState = worldState
    )

    var cachedClientData: String? = null
    var cachedSkinData: String? = null
    var cachedLoginPacket: LoginPacket? = null
    var clientProtocolVersion: Int = 671
    var isConnectedToUpstream: Boolean = false
        private set
    private val isConnectingUpstream = AtomicBoolean(false)

    init {
        setupLocalSessionHandler()
        startTickLoop()
    }

    private fun setupLocalSessionHandler() {
        serverSession.setPacketHandler(object : BedrockPacketHandler {
            override fun handlePacket(packet: BedrockPacket): PacketSignal {
                try {
                    val signal = packetManager.onClientPacket(packet)
                    if (signal == PacketSignal.UNHANDLED) {
                        clientSession?.sendPacket(packet)
                    }
                } catch (t: Throwable) {
                    Logger.e("ProxySession", "Error processing client packet ${packet.javaClass.simpleName}: ${t.message}", t)
                    try { clientSession?.sendPacket(packet) } catch (_: Exception) {}
                }
                return PacketSignal.HANDLED
            }
        })
    }

    private fun startTickLoop() {
        tickExecutor.scheduleAtFixedRate({
            try {
                if (!serverSession.isConnected) {
                    cleanup()
                    return@scheduleAtFixedRate
                }
                clientSession?.let { upstream ->
                    if (isConnectedToUpstream && !upstream.isConnected) {
                        try {
                            serverSession.disconnect("§c[AG Client] Connection lost to upstream server.")
                        } catch (_: Exception) {}
                        cleanup()
                        return@scheduleAtFixedRate
                    }
                }
                moduleManager.onTick(this)
            } catch (e: Exception) {
                Logger.e("ProxySession", "Error in proxy session tick", e)
            }
        }, 50, 50, TimeUnit.MILLISECONDS) // 20 ticks per second
    }

    fun connectUpstream() {
        if (isConnectedToUpstream || !isConnectingUpstream.compareAndSet(false, true)) {
            return
        }

        sessionScope.launch {
            // Resolve upstream target (Dynamically detected from tapped server or configured fallback)
            var rawHost = (DynamicTargetTracker.activeTargetHost ?: config.targetIp).trim()
            if (rawHost.isBlank() || rawHost == "127.0.0.1" || rawHost == "10.0.0.1" || rawHost.equals("localhost", ignoreCase = true)) {
                rawHost = "geo.hivebedrock.network"
            }
            val targetHost = rawHost
            val targetPort = if (DynamicTargetTracker.activeTargetHost != null) {
                DynamicTargetTracker.activeTargetPort
            } else {
                if (config.targetPort > 0) config.targetPort else 19132
            }

            Logger.upstream("Initiating upstream connection flow to $targetHost:$targetPort...")

            val dnsStart = System.currentTimeMillis()
            val targetAddress = try {
                InetSocketAddress(targetHost, targetPort)
            } catch (e: Exception) {
                isConnectingUpstream.set(false)
                Logger.upstream("DNS lookup failed for $targetHost: ${e.message}")
                try {
                    serverSession.disconnect("§c[AG Client] Cannot resolve server host:\n§e$targetHost\n§7Please verify target IP and internet.")
                } catch (_: Exception) {}
                cleanup()
                return@launch
            }

            val dnsElapsed = System.currentTimeMillis() - dnsStart
            if (targetAddress.isUnresolved) {
                isConnectingUpstream.set(false)
                Logger.upstream("Host $targetHost unresolvable by DNS (${dnsElapsed}ms)")
                try {
                    serverSession.disconnect("§c[AG Client] Host unresolvable:\n§e$targetHost")
                } catch (_: Exception) {}
                cleanup()
                return@launch
            }

            val resolvedIp = targetAddress.address?.hostAddress ?: targetHost
            Logger.upstream("DNS resolved $targetHost -> $resolvedIp in ${dnsElapsed}ms")

            val group = NioEventLoopGroup(2)
            clientEventLoopGroup = group

            val future = CompletableFuture<BedrockClientSession>()
            var pendingSession: BedrockClientSession? = null

            val bootstrap = Bootstrap()
                .channelFactory(RakChannelFactory.client(NioDatagramChannel::class.java))
                .group(group)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 25000)
                .option(org.cloudburstmc.netty.channel.raknet.config.RakChannelOption.RAK_PROTOCOL_VERSION, 11)
                .handler(object : BedrockClientInitializer() {
                    override fun initChannel(ch: Channel) {
                        super.initChannel(ch)
                        ch.pipeline().addLast("upstream-exception-logger", object : ChannelInboundHandlerAdapter() {
                            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                                Logger.e("NettyUpstream", "Upstream Netty pipeline exception: ${cause::class.java.simpleName}: ${cause.message}", cause)
                                super.exceptionCaught(ctx, cause)
                            }
                        })
                    }

                    override fun initSession(session: BedrockClientSession) {
                        pendingSession = session
                    }
                })

            val watchdogTask = tickExecutor.schedule({
                if (!future.isDone) {
                    Logger.upstream("RakNet connection to $resolvedIp:$targetPort timed out after 25 seconds.")
                    future.completeExceptionally(java.util.concurrent.TimeoutException("Remote server $targetHost:$targetPort ($resolvedIp) did not respond within 25 seconds. Please verify target IP & network."))
                }
            }, 25000, TimeUnit.MILLISECONDS)

            Logger.upstream("Transmitting RakNet Open Connection Request 1 to $resolvedIp:$targetPort...")

            bootstrap.connect(targetAddress).addListener { connFuture ->
                if (connFuture.isSuccess) {
                    val s = pendingSession
                    if (s != null) {
                        future.complete(s)
                    } else {
                        future.completeExceptionally(IllegalStateException("Session wrapper was null on connect success"))
                    }
                } else {
                    future.completeExceptionally(connFuture.cause() ?: Exception("Connect failed to $targetHost:$targetPort ($resolvedIp)"))
                }
            }

            future.whenComplete { session, connErr ->
                watchdogTask.cancel(false)
                if (connErr != null) {
                    isConnectingUpstream.set(false)
                    Logger.upstream("Upstream connection to $targetHost ($resolvedIp) failed: ${connErr.message}")
                    try {
                        val msg = connErr.message ?: "Connection timed out"
                        serverSession.disconnect("§c[AG Client] Connection Timed Out!\n§7Server: §e$targetHost:$targetPort\n§7Resolved: §b$resolvedIp\n§cReason: §f$msg\n§ePlease verify target IP in AG Client Console.")
                    } catch (_: Exception) {
                        serverSession.disconnect()
                    }
                    cleanup()
                    return@whenComplete
                }

                clientSession = session
                isConnectedToUpstream = true
                Logger.upstream("Connected to upstream server: $targetHost ($resolvedIp:$targetPort) successfully!")

                // Automatically record successfully connected server to history
                configManager.addServerToHistory(targetHost, targetPort)

                // Sync codec with local client session
                val effectiveCodec = serverSession.codec ?: BedrockCodecFinder.findCodec(clientProtocolVersion) ?: BedrockCodecFinder.getDefaultCodec()
                if (effectiveCodec != null) {
                    session.codec = effectiveCodec
                    serverSession.codec = effectiveCodec
                }

                setupRemoteSessionHandler(session)

                // Request network settings from upstream server using the best codec we support
                val effectiveProto = session.codec?.protocolVersion ?: clientProtocolVersion
                val upstreamReq = RequestNetworkSettingsPacket().apply {
                    protocolVersion = effectiveProto
                }
                try {
                    session.sendPacketImmediately(upstreamReq)
                    Logger.upstream("Sent RequestNetworkSettingsPacket ($effectiveProto) to upstream server.")
                } catch (e: Exception) {
                    Logger.upstream("Failed to send RequestNetworkSettingsPacket upstream: ${e.message}")
                    cachedLoginPacket?.let { session.sendPacket(it) }
                }
            }
        }
    }

    private fun setupRemoteSessionHandler(session: BedrockClientSession) {
        session.setPacketHandler(object : BedrockPacketHandler {
            override fun handlePacket(packet: BedrockPacket): PacketSignal {
                try {
                    val signal = packetManager.onServerPacket(packet, session)
                    if (signal == PacketSignal.UNHANDLED) {
                        serverSession.sendPacket(packet)
                    }
                    handleServerSpawnPacket(packet)
                } catch (t: Throwable) {
                    Logger.e("ProxySession", "Error processing server packet ${packet.javaClass.simpleName}: ${t.message}", t)
                    try { serverSession.sendPacket(packet) } catch (_: Exception) {}
                }
                return PacketSignal.HANDLED
            }
        })
    }

    private var hasSentWelcomeMessage = false

    private fun handleServerSpawnPacket(packet: BedrockPacket) {
        if (packet is PlayStatusPacket) {
            Logger.i("ProxySession", "PlayStatus from server: ${packet.status}")
            if (packet.status == PlayStatusPacket.Status.PLAYER_SPAWN && !hasSentWelcomeMessage) {
                hasSentWelcomeMessage = true
                tickExecutor.schedule({
                    sendWelcomeMessage()
                }, 1200, TimeUnit.MILLISECONDS)
            }
        }
        if (packet is StartGamePacket && !hasSentWelcomeMessage) {
            tickExecutor.schedule({
                if (!hasSentWelcomeMessage) {
                    hasSentWelcomeMessage = true
                    sendWelcomeMessage()
                }
            }, 2500, TimeUnit.MILLISECONDS)
        }
    }

    private fun sendWelcomeMessage() {
        if (config.isGhostMode) return
        sendChatToClient("§8§m----------------------------------------")
        sendChatToClient("§b§lAG Client §fv2.9.0-beta.1 §7- §a§lMade by Aayaan Ghosh")
        sendChatToClient("§aConnected to server successfully!")
        sendChatToClient("§eType §b.help §eor §b/help §eto see command lists.")
        sendChatToClient("§8§m----------------------------------------")
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

    fun cleanup() {
        try {
            sessionScope.cancel()
        } catch (_: Exception) {}
        try {
            tickExecutor.shutdownNow()
        } catch (_: Exception) {}
        try {
            clientEventLoopGroup?.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS)
            clientEventLoopGroup = null
        } catch (_: Exception) {}
        worldState.reset()
    }

    fun close() {
        cleanup()
        try {
            clientSession?.disconnect()
            serverSession.disconnect()
        } catch (_: Exception) {}
        Logger.i("ProxySession", "Proxy session terminated.")
    }
}
