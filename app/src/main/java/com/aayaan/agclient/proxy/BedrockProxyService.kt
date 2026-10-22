package com.aayaan.agclient.proxy

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aayaan.agclient.AGClientApplication
import com.aayaan.agclient.MainActivity
import com.aayaan.agclient.R
import com.aayaan.agclient.utils.Logger
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption
import org.cloudburstmc.protocol.bedrock.BedrockPong
import org.cloudburstmc.protocol.bedrock.BedrockServerSession
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockServerInitializer
import java.net.InetSocketAddress
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

enum class ServiceState {
    STOPPED, STARTING, RUNNING, ERROR
}

class BedrockProxyService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var serverChannel: Channel? = null
    private var eventLoopGroup: NioEventLoopGroup? = null
    private val activeSessions = ConcurrentHashMap<InetSocketAddress, ProxySession>()

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            acquireLocks()
        } catch (t: Throwable) {
            Logger.e("ProxyService", "Error during onCreate", t)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopProxy()
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = createNotification("Starting proxy server...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Logger.e("ProxyService", "Failed to startForeground", t)
            _serviceState.value = ServiceState.ERROR
            return START_NOT_STICKY
        }

        startProxy()

        return START_STICKY
    }

    private fun startProxy() {
        scope.launch {
            try {
                _serviceState.value = ServiceState.STARTING
                val app = application as AGClientApplication
                val config = app.configManager.currentConfig

                val bindAddress = InetSocketAddress("0.0.0.0", config.localBindPort)
                val group = NioEventLoopGroup(2)
                eventLoopGroup = group

                // Configure RakNet Server Pong
                val defaultCodec = BedrockCodecFinder.getDefaultCodec()
                val targetSubMotd = if (config.targetIp.isNotBlank()) "§aTarget: ${config.targetIp}:${config.targetPort}" else "§eSet Target in AG App"
                val pong = BedrockPong().apply {
                    edition("MCPE")
                    motd("§bAG Client v2.1 §7[Proxy]")
                    subMotd(targetSubMotd)
                    playerCount(1)
                    maximumPlayerCount(10)
                    gameType("Survival")
                    nintendoLimited(false)
                    ipv4Port(config.localBindPort)
                    ipv6Port(config.localBindPort)
                    version(defaultCodec?.minecraftVersion ?: "1.21.20")
                    protocolVersion(defaultCodec?.protocolVersion ?: 712)
                }

                val bootstrap = ServerBootstrap()
                    .channelFactory(RakChannelFactory.server(NioDatagramChannel::class.java))
                    .group(group)
                    .option(RakChannelOption.RAK_ADVERTISEMENT, pong.toByteBuf())
                    .option(RakChannelOption.RAK_HANDLE_PING, true)
                    .childHandler(object : BedrockServerInitializer() {
                        override fun initSession(session: BedrockServerSession) {
                            val remoteAddress = session.socketAddress as? InetSocketAddress ?: return
                            Logger.i("ProxyService", "Local Minecraft client connected: $remoteAddress")

                            // Crucial: Assign initial codec so session can serialize/deserialize packets without throwing
                            val initialCodec = BedrockCodecFinder.getDefaultCodec()
                            if (initialCodec != null) {
                                session.codec = initialCodec
                                Logger.i("ProxyService", "Assigned initial codec (${initialCodec.protocolVersion}) to local session.")
                            }

                            val proxySession = ProxySession(
                                serverSession = session,
                                config = config,
                                configManager = app.configManager,
                                authManager = app.xboxAuthManager,
                                moduleManager = app.moduleManager,
                                commandManager = app.commandManager,
                                guiManager = app.guiManager
                            )
                            activeSessions[remoteAddress] = proxySession
                        }
                    })

                val channelFuture = bootstrap.bind(bindAddress).sync()
                serverChannel = channelFuture.channel()

                _serviceState.value = ServiceState.RUNNING
                Logger.i("ProxyService", "Bedrock proxy server bound to 0.0.0.0:${config.localBindPort}")

                updateNotification("Proxy Active on port ${config.localBindPort}")

            } catch (t: Throwable) {
                Logger.e("ProxyService", "Failed to start Bedrock proxy", t)
                _serviceState.value = ServiceState.ERROR
                updateNotification("Proxy failed to start: ${t.message}")
            }
        }
    }

    private var lanBroadcastJob: Job? = null

    private fun startLanBroadcaster() {
        lanBroadcastJob?.cancel()
        lanBroadcastJob = scope.launch(Dispatchers.IO) {
            val socket = try {
                DatagramSocket().apply {
                    broadcast = true
                }
            } catch (e: Exception) {
                Logger.w("ProxyService", "Failed to open UDP broadcast socket: ${e.message}")
                return@launch
            }

            val magic = byteArrayOf(
                0x00.toByte(), 0xff.toByte(), 0xff.toByte(), 0x00.toByte(),
                0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(),
                0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(),
                0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte()
            )

            val serverGuid = 12345678901234L
            val port = (application as AGClientApplication).configManager.currentConfig.localBindPort

            Logger.i("ProxyService", "Started active LAN Pong broadcaster (1.21 compatible).")

            while (isActive) {
                try {
                    val currentConfig = (application as AGClientApplication).configManager.currentConfig
                    val targetName = if (currentConfig.targetIp.isNotBlank()) currentConfig.targetIp else "AG Proxy"
                    val broadcastPort = currentConfig.localBindPort
                    // Bedrock Unconnected Pong: Edition;MOTD;Protocol;Version;Players;MaxPlayers;GUID;SubMOTD;GameMode;GameModeNum;PortIPv4;PortIPv6;
                    val motd = "MCPE;§bAG Client v2.1 §7[$targetName];712;1.21.20;1;10;$serverGuid;AG Client;Survival;1;$broadcastPort;$broadcastPort;"
                    val motdBytes = motd.toByteArray(Charsets.UTF_8)

                    val buffer = ByteBuffer.allocate(1 + 8 + 8 + 16 + 2 + motdBytes.size).apply {
                        put(0x1c.toByte()) // ID_UNCONNECTED_PONG
                        putLong(System.currentTimeMillis())
                        putLong(serverGuid)
                        put(magic)
                        putShort(motdBytes.size.toShort())
                        put(motdBytes)
                    }.array()

                    val targets = mutableListOf(
                        InetSocketAddress("255.255.255.255", 19132),
                        InetSocketAddress("255.255.255.255", 19133),
                        InetSocketAddress("127.0.0.1", 19132),
                        InetSocketAddress("127.0.0.1", 19133)
                    )

                    try {
                        val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
                        for (intf in interfaces) {
                            if (!intf.isLoopback && intf.isUp) {
                                for (intfAddr in intf.interfaceAddresses) {
                                    intfAddr.broadcast?.let { bcast ->
                                        targets.add(InetSocketAddress(bcast, 19132))
                                        targets.add(InetSocketAddress(bcast, 19133))
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    for (target in targets) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size, target)
                            socket.send(packet)
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Logger.d("ProxyService", "LAN broadcast tick error: ${e.message}")
                }
                delay(1500L) // Broadcast every 1.5 seconds
            }
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun stopProxy() {
        try {
            lanBroadcastJob?.cancel()
            lanBroadcastJob = null
            for ((_, session) in activeSessions) {
                session.close()
            }
            activeSessions.clear()
            serverChannel?.close()
            eventLoopGroup?.shutdownGracefully()
            serverChannel = null
            eventLoopGroup = null
        } catch (t: Throwable) {
            Logger.e("ProxyService", "Error stopping proxy server", t)
        }
        _serviceState.value = ServiceState.STOPPED
        releaseLocks()
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AGClient::ProxyWakeLock"
            ).apply {
                acquire(10 * 60 * 60 * 1000L) // 10 hours
            }
        } catch (t: Throwable) {
            Logger.e("ProxyService", "Error acquiring wake lock", t)
        }

        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "AGClient::ProxyWifiLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            Logger.i("ProxyService", "Acquired WifiLock (FULL_HIGH_PERF) for proxy stability.")
        } catch (t: Throwable) {
            Logger.w("ProxyService", "Error acquiring WifiLock: ${t.message}")
        }

        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("AGClient::LANMulticast").apply {
                setReferenceCounted(true)
                acquire()
            }
            Logger.i("ProxyService", "Acquired Wifi MulticastLock for LAN game discovery.")
        } catch (t: Throwable) {
            Logger.w("ProxyService", "Error acquiring MulticastLock: ${t.message}")
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AG Client Proxy Service",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Maintains local MITM proxy server and background packet processing"
            setShowBadge(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, BedrockProxyService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AG Client v2.1.0 (Active Proxy)")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Proxy", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onDestroy() {
        stopProxy()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "ag_client_proxy_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.aayaan.agclient.action.START_PROXY"
        const val ACTION_STOP = "com.aayaan.agclient.action.STOP_PROXY"

        private val _serviceState = MutableStateFlow(ServiceState.STOPPED)
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()
    }
}
