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

class AGVirtualSandboxService : VpnService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    @Volatile
    private var lastClientPort = 0
    @Volatile
    private var lastDstPort = 19133
    @Volatile
    private var lastDstIp: ByteArray? = null

    private var ipIdCounter = 0

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

        val notification = buildNotification("Virtual Sandbox Active - Auto-Routing Minecraft")
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
                Logger.i("VirtualSandbox", "Starting local virtual network sandbox for Minecraft...")
                val builder = Builder()
                    .setSession("AG Client Virtual Sandbox")
                    .addAddress("10.0.0.2", 24)
                    .addRoute("10.0.0.0", 24)
                    .setMtu(1400)

                // Route only Minecraft packages through sandbox
                val mcPackages = listOf(
                    "com.mojang.minecraftpe",
                    "com.mojang.minecraftpe.beta",
                    "com.mojang.minecrafttrialpe",
                    "com.microsoft.minecraft"
                )

                for (pkg in mcPackages) {
                    try {
                        builder.addAllowedApplication(pkg)
                        Logger.d("VirtualSandbox", "Added $pkg to sandbox routing filter.")
                    } catch (_: Exception) {}
                }

                vpnInterface = builder.establish()
                Logger.i("VirtualSandbox", "Virtual network sandbox established (10.0.0.2).")

                // Start bidirectional packet relay loop
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

        val tunnelSocket = try {
            DatagramSocket().apply {
                protect(this)
            }
        } catch (e: Exception) {
            Logger.e("VirtualSandbox", "Failed to create protected datagram socket", e)
            return
        }

        // Direction 1: Minecraft -> TUN -> Sandbox -> Local Bedrock Proxy (127.0.0.1:19133)
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(32768)
            val proxyTarget = InetSocketAddress("127.0.0.1", 19133)

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

                    val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
                    val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)

                    val payloadOffset = ihl + 8
                    val payloadLength = length - payloadOffset
                    if (payloadLength <= 0) continue

                    lastClientPort = srcPort
                    lastDstPort = dstPort
                    lastDstIp = byteArrayOf(buffer[12], buffer[13], buffer[14], buffer[15])

                    // Forward UDP packet payload directly to AG Client Bedrock Proxy
                    try {
                        val packet = DatagramPacket(buffer, payloadOffset, payloadLength, proxyTarget)
                        tunnelSocket.send(packet)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
            }
        }

        // Direction 2: Local Bedrock Proxy (127.0.0.1:19133) -> Sandbox -> Minecraft TUN OutputStream
        scope.launch(Dispatchers.IO) {
            val replyBuffer = ByteArray(32768)
            val replyPacket = DatagramPacket(replyBuffer, replyBuffer.size)

            try {
                while (isRunning && isActive) {
                    try {
                        tunnelSocket.receive(replyPacket)
                    } catch (_: Exception) {
                        continue
                    }

                    val replyLen = replyPacket.length
                    val clientPort = lastClientPort
                    if (replyLen > 0 && clientPort > 0) {
                        val ipPacket = buildUdpIpPacket(
                            srcIp = lastDstIp ?: byteArrayOf(10, 0, 0, 1),
                            dstIp = byteArrayOf(10, 0, 0, 2),
                            srcPort = lastDstPort,
                            dstPort = clientPort,
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
                try { tunnelSocket.close() } catch (_: Exception) {}
            }
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
        // UDP Checksum 0 (valid in IPv4 RFC 768)
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

    private fun stopVirtualSandbox() {
        isRunning = false
        _isSandboxRunning.value = false
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
