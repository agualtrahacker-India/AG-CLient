package com.aayaan.agclient.proxy

import android.app.Notification
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
import com.aayaan.agclient.AGClientApplication
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
import java.net.InetAddress
import java.nio.ByteBuffer

class BedrockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = createNotification("Auto-Intercept Active: Tap any server in Minecraft!")
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
            startVpnTunnel()
        } catch (t: Throwable) {
            Logger.e("VpnService", "Failed to startForeground for VPN", t)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun startVpnTunnel() {
        if (isRunning) return
        isRunning = true
        _vpnState.value = true

        scope.launch {
            try {
                val builder = Builder()
                    .setSession("AG Client Redirect")
                    .addAddress("10.8.0.2", 32)
                    .addDnsServer("10.8.0.1")
                    .addRoute("10.8.0.1", 32) // Route only DNS traffic through the tunnel
                    .setBlocking(true)

                // Restrict VPN exclusively to Minecraft so normal phone apps (YouTube, WhatsApp, Browser) are 100% untouched
                val candidatePackages = listOf(
                    "com.mojang.minecraftpe",
                    "com.mojang.minecraftpe.beta",
                    "com.mojang.minecrafttrialpe",
                    "com.microsoft.minecraft"
                )
                var allowedAppAdded = false
                for (pkg in candidatePackages) {
                    try {
                        packageManager.getPackageInfo(pkg, 0)
                        builder.addAllowedApplication(pkg)
                        allowedAppAdded = true
                        Logger.i("VpnService", "Restricted VPN exclusively to Minecraft: $pkg")
                    } catch (_: Exception) {}
                }

                if (!allowedAppAdded) {
                    try {
                        builder.addAllowedApplication("com.mojang.minecraftpe")
                        Logger.i("VpnService", "Defaulted allowed application to com.mojang.minecraftpe")
                    } catch (e: Exception) {
                        Logger.w("VpnService", "Could not restrict application: ${e.message}")
                    }
                }

                vpnInterface = builder.establish()
                Logger.i("VpnService", "VPN Tunnel established in blocking mode. Intercepting Minecraft DNS...")

                runDnsLoop()
            } catch (t: Throwable) {
                Logger.e("VpnService", "Failed to establish VPN tunnel", t)
                stopVpn()
            }
        }
    }

    private suspend fun runDnsLoop() = withContext(Dispatchers.IO) {
        val pfd = vpnInterface ?: return@withContext
        val inStream = FileInputStream(pfd.fileDescriptor)
        val outStream = FileOutputStream(pfd.fileDescriptor)
        val packetBuffer = ByteArray(4096)

        Logger.i("VpnService", "Starting VPN DNS loop...")

        while (isRunning && isActive) {
            try {
                val length = inStream.read(packetBuffer)
                if (length > 0) {
                    val packetCopy = packetBuffer.copyOf(length)
                    handleIpPacket(packetCopy, length, outStream)
                }
            } catch (e: Exception) {
                if (!isRunning) break
                delay(10)
            }
        }
    }

    private fun handleIpPacket(
        data: ByteArray,
        length: Int,
        vpnOut: FileOutputStream
    ) {
        // Simple IPv4 UDP DNS inspection
        if (length < 28) return
        val versionAndIHL = data[0].toInt() and 0xFF
        if ((versionAndIHL ushr 4) != 4) return // Only IPv4
        val ihl = (versionAndIHL and 0x0F) * 4
        val protocol = data[9].toInt() and 0xFF
        if (protocol != 17) return // Only UDP

        val srcPort = ((data[ihl].toInt() and 0xFF) shl 8) or (data[ihl + 1].toInt() and 0xFF)
        val dstPort = ((data[ihl + 2].toInt() and 0xFF) shl 8) or (data[ihl + 3].toInt() and 0xFF)

        if (dstPort == 53) {
            // DNS Request
            val udpPayloadOffset = ihl + 8
            val udpPayloadLen = length - udpPayloadOffset
            if (udpPayloadLen < 12) return

            val dnsData = data.copyOfRange(udpPayloadOffset, length)
            val domain = extractDnsDomain(dnsData)

            val configuredTarget = (application as? AGClientApplication)?.configManager?.currentConfig?.targetIp ?: ""

            val isGameServer = domain.contains("hivebedrock.network") ||
                    domain.contains("inpvp.net") ||
                    domain.contains("cubecraft.net") ||
                    domain.contains("mineplex.com") ||
                    domain.contains("galaxite.net") ||
                    domain.contains("lifeboat.net") ||
                    domain.contains("lbsg.net") ||
                    domain.contains("pixelparadise.gg") ||
                    domain.contains("nethergames.org") ||
                    domain.contains("flarepe.com") ||
                    domain.contains("rushnation.net") ||
                    domain.contains("hyperlandsmc.net") ||
                    domain.contains("zeqa.net") ||
                    domain.contains("falixnodes.net") ||
                    domain.contains("aternos.me") ||
                    domain.contains("minehut.gg") ||
                    (configuredTarget.isNotEmpty() && domain.contains(configuredTarget, ignoreCase = true))

            if (isGameServer) {
                Logger.i("VpnService", "Intercepted DNS query for $domain -> Routing to AG Client (127.0.0.1)")
                DynamicTargetTracker.setTarget(domain, 19132)
                updateNotification("Connected to: $domain (AG Client Ready)")
                val responseDns = craftLocalDnsResponse(dnsData, "127.0.0.1")
                val responseIp = craftUdpIpPacket(
                    srcIp = data.copyOfRange(16, 20), // original dst
                    dstIp = data.copyOfRange(12, 16), // original src
                    srcPort = 53,
                    dstPort = srcPort,
                    payload = responseDns
                )
                synchronized(vpnOut) {
                    try {
                        vpnOut.write(responseIp)
                    } catch (_: Exception) {}
                }
            } else {
                // Forward non-game DNS queries (e.g. Xbox Live auth, Microsoft login, etc.) asynchronously
                // so the main VPN loop never blocks and multiple queries execute concurrently
                val srcIp = data.copyOfRange(16, 20)
                val dstIp = data.copyOfRange(12, 16)
                scope.launch(Dispatchers.IO) {
                    val upstreamServers = listOf("1.1.1.1", "8.8.8.8")
                    var respDns: ByteArray? = null

                    for (dnsServer in upstreamServers) {
                        var socket: DatagramSocket? = null
                        try {
                            socket = DatagramSocket()
                            protect(socket)
                            socket.soTimeout = 2000
                            val reqPacket = DatagramPacket(dnsData, dnsData.size, InetAddress.getByName(dnsServer), 53)
                            socket.send(reqPacket)

                            val respBuffer = ByteArray(2048)
                            val respPacket = DatagramPacket(respBuffer, respBuffer.size)
                            socket.receive(respPacket)
                            respDns = respPacket.data.copyOf(respPacket.length)
                            break
                        } catch (_: Exception) {
                            // Try next DNS server
                        } finally {
                            try { socket?.close() } catch (_: Exception) {}
                        }
                    }

                    if (respDns != null) {
                        val responseIp = craftUdpIpPacket(
                            srcIp = srcIp,
                            dstIp = dstIp,
                            srcPort = 53,
                            dstPort = srcPort,
                            payload = respDns
                        )
                        synchronized(vpnOut) {
                            try {
                                vpnOut.write(responseIp)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    private fun extractDnsDomain(dns: ByteArray): String {
        val sb = StringBuilder()
        var pos = 12
        while (pos < dns.size) {
            val len = dns[pos].toInt() and 0xFF
            if (len == 0) break
            pos++
            if (sb.isNotEmpty()) sb.append(".")
            for (i in 0 until len) {
                if (pos < dns.size) {
                    sb.append(dns[pos].toInt().toChar())
                    pos++
                }
            }
        }
        return sb.toString()
    }

    private fun craftLocalDnsResponse(query: ByteArray, redirectIp: String): ByteArray {
        val bb = ByteBuffer.allocate(512)
        // Transaction ID
        bb.put(query[0])
        bb.put(query[1])
        // Flags: Standard response, No error (0x8180)
        bb.putShort(0x8180.toShort())
        // Questions: 1
        bb.putShort(1)

        // Find end of Question QNAME
        var qPos = 12
        while (qPos < query.size && query[qPos].toInt() != 0) {
            qPos++
        }
        qPos++ // Skip terminating 0x00 byte

        if (qPos + 4 > query.size) {
            return query // Safety fallback for truncated packets
        }

        val qType = ((query[qPos].toInt() and 0xFF) shl 8) or (query[qPos + 1].toInt() and 0xFF)
        val questionLen = (qPos + 4) - 12

        if (qType == 28) {
            // AAAA (IPv6) query -> Return NODATA (0 answers, NOERROR) so Minecraft cleanly falls back to IPv4
            bb.putShort(0.toShort()) // Answer RRs: 0
            bb.putShort(0.toShort()) // Authority RRs: 0
            bb.putShort(0.toShort()) // Additional RRs: 0
            bb.put(query, 12, questionLen)
        } else {
            // A (IPv4) query -> Return redirectIp (127.0.0.1)
            bb.putShort(1.toShort()) // Answer RRs: 1
            bb.putShort(0.toShort()) // Authority RRs: 0
            bb.putShort(0.toShort()) // Additional RRs: 0
            bb.put(query, 12, questionLen)

            // Answer Section: Name Pointer (0xc00c)
            bb.putShort(0xc00c.toShort())
            bb.putShort(1.toShort()) // Type: A (0x0001)
            bb.putShort(1.toShort()) // Class: IN (0x0001)
            bb.putInt(60) // TTL: 60s
            bb.putShort(4.toShort()) // Data length: 4 bytes
            val ipParts = redirectIp.split(".").map { it.toInt().toByte() }
            for (b in ipParts) bb.put(b)
        }

        val out = ByteArray(bb.position())
        System.arraycopy(bb.array(), 0, out, 0, out.size)
        return out
    }

    private fun craftUdpIpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val totalLength = 20 + 8 + payload.size
        val bb = ByteBuffer.allocate(totalLength)

        // IPv4 Header
        bb.put(0x45.toByte()) // Version 4, IHL 5
        bb.put(0x00.toByte()) // DSCP / ECN
        bb.putShort(totalLength.toShort())
        bb.putShort(0.toShort()) // Identification
        bb.putShort(0x4000.toShort()) // Flags: Don't Fragment
        bb.put(64.toByte()) // TTL
        bb.put(17.toByte()) // Protocol: UDP
        bb.putShort(0.toShort()) // Checksum (0 for now)
        bb.put(srcIp)
        bb.put(dstIp)

        // UDP Header
        bb.putShort(srcPort.toShort())
        bb.putShort(dstPort.toShort())
        bb.putShort((8 + payload.size).toShort())
        bb.putShort(0.toShort()) // UDP Checksum optional in IPv4

        // Payload
        bb.put(payload)

        // Compute IPv4 header checksum
        val data = bb.array()
        var sum = 0
        for (i in 0 until 20 step 2) {
            if (i == 10) continue // Skip checksum field
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val checksum = (sum.inv() and 0xFFFF).toShort()
        data[10] = ((checksum.toInt() ushr 8) and 0xFF).toByte()
        data[11] = (checksum.toInt() and 0xFF).toByte()

        return data
    }

    private fun stopVpn() {
        isRunning = false
        _vpnState.value = false
        scope.cancel()
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {}
        Logger.i("VpnService", "VPN Tunnel stopped.")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AG Client Redirect Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Provides zero-IP tap-to-join redirection for Minecraft Featured Servers"
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

        val stopIntent = Intent(this, BedrockVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AG Client (Auto-Redirect)")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Redirect", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, createNotification(content))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ag_client_vpn_channel"
        const val NOTIFICATION_ID = 2002
        const val ACTION_START = "com.aayaan.agclient.action.START_VPN"
        const val ACTION_STOP = "com.aayaan.agclient.action.STOP_VPN"

        private val _vpnState = MutableStateFlow(false)
        val vpnState: StateFlow<Boolean> = _vpnState.asStateFlow()
    }
}
