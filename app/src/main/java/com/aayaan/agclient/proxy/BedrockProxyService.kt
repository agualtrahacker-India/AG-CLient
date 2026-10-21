package com.aayaan.agclient.proxy

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
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
import java.util.concurrent.ConcurrentHashMap

enum class ServiceState {
    STOPPED, STARTING, RUNNING, ERROR
}

class BedrockProxyService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var serverChannel: Channel? = null
    private var eventLoopGroup: NioEventLoopGroup? = null
    private val activeSessions = ConcurrentHashMap<InetSocketAddress, ProxySession>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopProxy()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Starting proxy server..."))
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
                val group = NioEventLoopGroup()
                eventLoopGroup = group

                // Configure LAN Discovery Pong
                val pong = BedrockPong().apply {
                    edition("MCPE")
                    motd("§bAG Client Beta §7[MITM Proxy]")
                    subMotd("§aTarget: ${config.targetIp}:${config.targetPort}")
                    playerCount(1)
                    maximumPlayerCount(10)
                    gameType("Survival")
                    nintendoLimited(false)
                    ipv4Port(config.localBindPort)
                    ipv6Port(config.localBindPort)
                    version("1.20.80")
                    protocolVersion(671)
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

                            val proxySession = ProxySession(
                                serverSession = session,
                                config = config,
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

                updateNotification("Proxy Active on port ${config.localBindPort} - LAN Broadcast On")

            } catch (e: Exception) {
                Logger.e("ProxyService", "Failed to start Bedrock proxy", e)
                _serviceState.value = ServiceState.ERROR
                updateNotification("Proxy failed to start: ${e.message}")
            }
        }
    }

    private fun stopProxy() {
        try {
            for ((_, session) in activeSessions) {
                session.close()
            }
            activeSessions.clear()
            serverChannel?.close()
            eventLoopGroup?.shutdownGracefully()
            serverChannel = null
            eventLoopGroup = null
        } catch (e: Exception) {
            Logger.e("ProxyService", "Error stopping proxy server", e)
        }
        _serviceState.value = ServiceState.STOPPED
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AGClient::ProxyWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 hours
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Logger.e("ProxyService", "Error releasing WakeLock", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AG Client Proxy Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains local MITM proxy server and background packet processing"
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
            .setContentTitle("AG Client (MITM Proxy)")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Proxy", stopPendingIntent)
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
        const val ACTION_STOP = "com.aayaan.agclient.action.STOP_PROXY"

        private val _serviceState = MutableStateFlow(ServiceState.STOPPED)
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()
    }
}
