package com.aayaan.agclient.proxy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aayaan.agclient.MainActivity
import com.aayaan.agclient.R
import com.aayaan.agclient.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class AGVirtualSandboxService : VpnService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var ipIdCounter = 0

    private val clientRoutes = ConcurrentHashMap<Int, ClientRoute>()

    private class ClientRoute(
        val clientIp: ByteArray,
        val clientPort: Int,
        val serverPort: Int,
        val serverIp: ByteArray,
        val socket: DatagramSocket,
        val job: Job
    )

    companion object {
        const val ACTION_START = "com.aayaan.agclient.SANDBOX_START"
        const val ACTION_STOP = "com.aayaan.agclient.SANDBOX_STOP"
        const val CHANNEL_ID = "ag_virtual_sandbox_channel"
        const val NOTIFICATION_ID = 3001

        private val _isSandboxRunning = MutableStateFlow(false)
        val isSandboxRunning: StateFlow<Boolean> = _isSandboxRunning.asStateFlow()

        fun isPrepared(context: Context): Boolean {
            return prepare(context) == null
        }

        fun getPrepareIntent(context: Context): Intent? {
            return prepare(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVirtualSandbox()
            ACTION_STOP -> stopVirtualSandbox()
        }
        return START_STICKY
    }

    private fun startVirtualSandbox() {
        if (isRunning) return
        isRunning = true
        _isSandboxRunning.value = true

        // Automatically ensure BedrockProxyService is running so local relay target is available
        if (BedrockProxyService.serviceState.value != ServiceState.RUNNING) {
            Logger.i("VirtualSandbox", "Starting BedrockProxyService to listen for sandbox relay traffic...")
            val proxyIntent = Intent(this, BedrockProxyService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(proxyIntent)
                } else {
                    startService(proxyIntent)
                }
            } catch (e: Exception) {
                Logger.e("VirtualSandbox", "Failed to start BedrockProxyService", e)
            }
        }

        val localPort = (application as? com.aayaan.agclient.AGClientApplication)?.configManager?.currentConfig?.localBindPort ?: 19133
        val notification = buildNotification("Virtual Sandbox Active (10.0.0.1:$localPort)")
        try {
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
        } catch (e: Exception) {
            Logger.e("VirtualSandbox", "Failed to start foreground", e)
        }

        scope.launch {
            try {
                Logger.i("VirtualSandbox", "Establishing local virtual network sandbox (10.0.0.2 / 10.0.0.1)...")
                val builder = Builder()
                    .setSession("AG Client Virtual Sandbox")
                    .addAddress("10.0.0.2", 24)
                    .addRoute("10.0.0.0", 24)
                    .setMtu(1400)
                    .allowBypass()

                try {
                    builder.addDisallowedApplication(packageName)
                    Logger.i("VirtualSandbox", "Configured sandbox VPN: AG Client disallowed, all game packages permitted.")
                } catch (e: Exception) {
                    Logger.w("VirtualSandbox", "Could not disallow self package: ${e.message}")
                }

                vpnInterface = builder.establish()
                Logger.i("VirtualSandbox", "Virtual network sandbox established (10.0.0.2 / gateway 10.0.0.1).")

                runPacketRelayLoop()

            } catch (e: Exception) {
                Logger.e("VirtualSandbox", "Failed to establish virtual network sandbox", e)
                stopVirtualSandbox()
            }
        }
    }

    private fun runPacketRelayLoop() {
        val pfd = vpnInterface ?: return
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)

        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(32768)
            val localPort = (application as? com.aayaan.agclient.AGClientApplication)?.configManager?.currentConfig?.localBindPort ?: 19133
            val proxyTarget = InetSocketAddress("127.0.0.1", localPort)

            try {
                while (isRunning && isActive) {
                    val length = try {
                        inputStream.read(buffer)
                    } catch (_: Exception) {
                        -1
                    }

                    if (length < 28) continue

                    // Parse IPv4 header
                    val versionAndIhl = buffer[0].toInt() and 0xFF
                    val version = versionAndIhl shr 4
                    if (version != 4) continue

                    val ihl = (versionAndIhl and 0x0F) * 4
                    if (ihl < 20 || length < ihl + 8) continue

                    val protocol = buffer[9].toInt() and 0xFF
                    if (protocol != 17) continue // UDP only

                    // Extract IPs: Bytes 12..15 = Source IP (Minecraft), Bytes 16..19 = Dest IP (10.0.0.1)
                    val srcIp = byteArrayOf(buffer[12], buffer[13], buffer[14], buffer[15])
                    val dstIp = byteArrayOf(buffer[16], buffer[17], buffer[18], buffer[19])

                    // Extract Ports
                    val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
                    val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)

                    val payloadOffset = ihl + 8
                    val payloadLength = length - payloadOffset
                    if (payloadLength <= 0) continue

                    // Dedicated multiplexed route per clientPort
                    val route = getOrCreateRoute(srcIp, srcPort, dstPort, dstIp, outputStream)
                    try {
                        val packet = DatagramPacket(buffer, payloadOffset, payloadLength, proxyTarget)
                        route.socket.send(packet)
                        Logger.d("VirtualSandbox", "Relayed $payloadLength bytes: 10.0.0.2:$srcPort -> 127.0.0.1:$localPort")
                    } catch (e: Exception) {
                        Logger.w("VirtualSandbox", "Failed to forward packet to proxy 127.0.0.1:$localPort: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Logger.e("VirtualSandbox", "Packet relay loop error", e)
            }
        }
    }

    private fun getOrCreateRoute(
        clientIp: ByteArray,
        clientPort: Int,
        serverPort: Int,
        serverIp: ByteArray,
        outputStream: FileOutputStream
    ): ClientRoute {
        val existing = clientRoutes[clientPort]
        if (existing != null && !existing.socket.isClosed && existing.job.isActive) {
            return existing
        }

        if (existing != null) {
            try { existing.job.cancel() } catch (_: Exception) {}
            try { existing.socket.close() } catch (_: Exception) {}
            clientRoutes.remove(clientPort)
        }

        return clientRoutes.computeIfAbsent(clientPort) {
            // Must NOT call protect(this) here: this socket only communicates with 127.0.0.1 loopback
            val relaySocket = DatagramSocket()

            val job = scope.launch(Dispatchers.IO) {
                val replyBuf = ByteArray(32768)
                val replyPacket = DatagramPacket(replyBuf, replyBuf.size)

                try {
                    while (isRunning && isActive) {
                        try {
                            relaySocket.receive(replyPacket)
                        } catch (_: Exception) {
                            break
                        }

                        val replyLen = replyPacket.length
                        if (replyLen > 0) {
                            Logger.d("VirtualSandbox", "Proxy replied $replyLen bytes, routing to 10.0.0.2:$clientPort")
                            val ipPacket = buildUdpIpPacket(
                                srcIp = serverIp, // 10.0.0.1 (the virtual server)
                                dstIp = clientIp, // Minecraft client IP
                                srcPort = serverPort, // dstPort from client
                                dstPort = clientPort, // Minecraft client port
                                payload = replyPacket.data,
                                payloadOffset = replyPacket.offset,
                                payloadLength = replyLen
                            )

                            try {
                                synchronized(outputStream) {
                                    outputStream.write(ipPacket)
                                    outputStream.flush()
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    try { relaySocket.close() } catch (_: Exception) {}
                }
            }

            ClientRoute(clientIp, clientPort, serverPort, serverIp, relaySocket, job)
        }
    }

    private fun buildUdpIpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
        payloadOffset: Int,
        payloadLength: Int
    ): ByteArray {
        val totalLength = 20 + 8 + payloadLength
        val packet = ByteArray(totalLength)

        // IPv4 Header (20 bytes)
        packet[0] = 0x45.toByte() // Version 4, IHL 5
        packet[1] = 0x00.toByte() // DSCP / ECN
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()

        val id = ++ipIdCounter
        packet[4] = ((id shr 8) and 0xFF).toByte()
        packet[5] = (id and 0xFF).toByte()
        packet[6] = 0x40.toByte() // DF (Don't fragment)
        packet[7] = 0x00.toByte()
        packet[8] = 64.toByte()   // TTL
        packet[9] = 17.toByte()   // Protocol UDP

        // Checksum at bytes 10..11 initially 0
        packet[10] = 0.toByte()
        packet[11] = 0.toByte()

        // Src IP (4 bytes)
        System.arraycopy(srcIp, 0, packet, 12, 4)
        // Dst IP (4 bytes)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        // Calculate IPv4 Header Checksum
        val ipChecksum = computeIpChecksum(packet, 0, 20)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // UDP Header (8 bytes)
        packet[20] = ((srcPort shr 8) and 0xFF).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = ((dstPort shr 8) and 0xFF).toByte()
        packet[23] = (dstPort and 0xFF).toByte()

        val udpLength = 8 + payloadLength
        packet[24] = ((udpLength shr 8) and 0xFF).toByte()
        packet[25] = (udpLength and 0xFF).toByte()

        // Standard RFC 768: 0x0000 indicates UDP checksum unused, preventing Linux kernel drop
        packet[26] = 0.toByte()
        packet[27] = 0.toByte()

        // Payload
        System.arraycopy(payload, payloadOffset, packet, 28, payloadLength)

        return packet
    }

    private fun computeIpChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    private fun computeUdpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
        payloadOffset: Int,
        payloadLength: Int
    ): Int {
        val udpLen = 8 + payloadLength
        var sum = 0

        // Pseudo-header:
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
        sum += 17 // Protocol UDP
        sum += udpLen

        // UDP Header:
        sum += srcPort
        sum += dstPort
        sum += udpLen

        // Payload:
        var i = payloadOffset
        val end = payloadOffset + payloadLength
        while (i < end - 1) {
            sum += ((payload[i].toInt() and 0xFF) shl 8) or (payload[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (payload[i].toInt() and 0xFF) shl 8
        }

        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        var csum = sum.inv() and 0xFFFF
        if (csum == 0) csum = 0xFFFF
        return csum
    }

    private fun stopVirtualSandbox() {
        isRunning = false
        _isSandboxRunning.value = false

        for ((_, route) in clientRoutes) {
            try { route.job.cancel() } catch (_: Exception) {}
            try { route.socket.close() } catch (_: Exception) {}
        }
        clientRoutes.clear()

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {}

        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Logger.i("VirtualSandbox", "Virtual network sandbox stopped.")
    }

    override fun onDestroy() {
        stopVirtualSandbox()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AG Client Virtual Sandbox",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status notification for the AG Client virtual network sandbox."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AGVirtualSandboxService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AG Client Virtual Sandbox (Active)")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sandbox", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
