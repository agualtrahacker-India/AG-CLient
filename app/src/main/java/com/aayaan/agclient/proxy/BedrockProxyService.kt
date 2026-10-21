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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.cloudburstmc.protocol.bedrock.BedrockServer
import org.cloudburstmc.protocol.bedrock.BedrockServerEventHandler
import org.cloudburstmc.protocol.bedrock.BedrockServerSession
import org.cloudburstmc.protocol.bedrock.util.BedrockPong
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

enum class ServiceState {
    STOPPED, STARTING, RUNNING, ERROR
}

class BedrockProxyService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var server: BedrockServer? = null
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
                val bedrockServer = BedrockServer(bindAddress)

                // Configure LAN Discovery Pong
                val pong = BedrockPong().apply {
                    edition = "MCPE"
                    motd = "§bAG Client Beta §7[MITM Proxy]"
                    subMotd = "§aTarget: ${config.targetIp}:${config.targetPort}"
                    playerCount = 1
                    maximumPlayerCount = 10
                    gameType = "Survival"
                    nintendoLimited = false
                    ipv4Port = config.localBindPort
                    ipv6Port = config.localBindPort
                }

                // Register Bedrock Server event handler
                bedrockServer.setHandler(object : BedrockServerEventHandler {
                    override fun onConnectionRequest(address: InetSocketAddress): Boolean = true

                    override fun onQuery(address: InetSocketAddress): BedrockPong = pong

                    override fun onSessionCreation(session: BedrockServerSession) {
                        Logger.i("ProxyService", "Local Minecraft client connected: ${session.socketAddress}")

                        val proxySession = ProxySession(
                            serverSession = session,
                            config = config,
                            authManager = app.xboxAuthManager,
                            moduleManager = app.moduleManager,
                            commandManager = app.commandManager,
                            guiManager = app.guiManager
                        )
                        activeSessions[session.socketAddress] = proxySession
                    }
                })

                bedrockServer.bind().join()
                server = bedrockServer
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
            server?.close()
            server = null
            _serviceState.value = ServiceState.STOPPED
            Logger.i("ProxyService", "Bedrock proxy service stopped.")
        } catch (e: Exception) {
            Logger.e("ProxyService", "Error stopping proxy server", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.service_notification_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, BedrockProxyService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AG Client Beta - Proxy Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Proxy", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AGClient:ProxyWakeLock").apply {
            acquire(24 * 60 * 60 * 1000L) // 24 hours max
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProxy()
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "ag_client_proxy_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.aayaan.agclient.action.START"
        const val ACTION_STOP = "com.aayaan.agclient.action.STOP"

        private val _serviceState = MutableStateFlow(ServiceState.STOPPED)
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()
    }
}
