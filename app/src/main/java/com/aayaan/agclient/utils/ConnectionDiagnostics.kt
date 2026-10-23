package com.aayaan.agclient.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.aayaan.agclient.config.ProxyConfig
import com.aayaan.agclient.proxy.AGVirtualSandboxService
import com.aayaan.agclient.proxy.BedrockProxyService
import com.aayaan.agclient.proxy.ServiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.*
import java.nio.ByteBuffer

/**
 * Diagnostic Engine for verifying network environment, sandbox routing,
 * and testing BOTH RakNet and NetherNet connectivity simultaneously.
 */
object ConnectionDiagnostics {

    data class DiagnosticResult(
        val deviceModel: String,
        val androidVersion: String,
        val networkType: String,
        val sandboxRunning: Boolean,
        val proxyState: String,
        val localBindPort: Int,
        // RakNet Diagnostics
        val localRakNetResponsive: Boolean,
        val localRakNetLatencyMs: Long,
        val upstreamRakNetReachable: Boolean,
        val upstreamRakNetLatencyMs: Long,
        val upstreamRakNetMotd: String,
        // NetherNet Diagnostics
        val localNetherNetTcpOpen: Boolean,
        val upstreamNetherNetTcpOpen: Boolean,
        val upstreamNetherNetLatencyMs: Long,
        val netherNet7551Active: Boolean,
        // Target Host info
        val targetServer: String,
        val targetResolvedIps: List<String>,
        val dnsLookupMs: Long,
        val summary: String
    )

    suspend fun runFullDiagnostics(context: Context, config: ProxyConfig): DiagnosticResult = withContext(Dispatchers.IO) {
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})"
        val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        // 1. Network Connectivity
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val networkType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular / Mobile Data"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            else -> "Unknown / Disconnected"
        }

        // 2. Services State
        val sandboxRunning = AGVirtualSandboxService.isSandboxRunning.value
        val proxyState = BedrockProxyService.serviceState.value.name
        val localBindPort = config.localBindPort

        // 3. RakNet: Test Local Proxy on 127.0.0.1:localBindPort (UDP)
        var localRakNetResponsive = false
        var localRakNetLatencyMs = -1L
        if (BedrockProxyService.serviceState.value == ServiceState.RUNNING) {
            try {
                val testSocket = DatagramSocket()
                testSocket.soTimeout = 1200
                val pingPacket = createRakNetPingPacket()
                val target = InetSocketAddress("127.0.0.1", localBindPort)
                val start = System.currentTimeMillis()
                testSocket.send(DatagramPacket(pingPacket, pingPacket.size, target))

                val replyBuf = ByteArray(2048)
                val replyPacket = DatagramPacket(replyBuf, replyBuf.size)
                testSocket.receive(replyPacket)
                localRakNetLatencyMs = System.currentTimeMillis() - start
                localRakNetResponsive = true
                testSocket.close()
            } catch (_: Exception) {
                localRakNetResponsive = false
            }
        }

        // 4. NetherNet: Test Local Companion TCP Port on 127.0.0.1:localBindPort
        var localNetherNetTcpOpen = false
        try {
            val tcpSocket = Socket()
            tcpSocket.connect(InetSocketAddress("127.0.0.1", localBindPort), 1000)
            localNetherNetTcpOpen = true
            tcpSocket.close()
        } catch (_: Exception) {
            localNetherNetTcpOpen = false
        }

        // 5. NetherNet: Test Port 7551 Discovery Listener
        var netherNet7551Active = false
        try {
            val ds = DatagramSocket()
            ds.soTimeout = 600
            val p = DatagramPacket(ByteArray(16), 16, InetSocketAddress("127.0.0.1", 7551))
            ds.send(p)
            netherNet7551Active = true
            ds.close()
        } catch (_: Exception) {
            netherNet7551Active = false
        }

        // 6. Target DNS Resolution
        val targetServer = config.targetIp.ifBlank { "geo.hivebedrock.network" }
        val targetPort = if (config.targetPort > 0) config.targetPort else 19132
        val resolvedIps = mutableListOf<String>()
        var dnsLookupMs = -1L

        val dnsStart = System.currentTimeMillis()
        try {
            val addresses = InetAddress.getAllByName(targetServer)
            dnsLookupMs = System.currentTimeMillis() - dnsStart
            addresses.forEach { resolvedIps.add(it.hostAddress ?: "") }
        } catch (_: Exception) {
            dnsLookupMs = System.currentTimeMillis() - dnsStart
        }

        // 7. RakNet: Test Upstream UDP RakNet Ping
        var upstreamRakNetReachable = false
        var upstreamRakNetLatencyMs = -1L
        var upstreamRakNetMotd = ""

        if (resolvedIps.isNotEmpty()) {
            try {
                val pingSocket = DatagramSocket()
                pingSocket.soTimeout = 2500
                val pingPacket = createRakNetPingPacket()
                val targetAddr = InetSocketAddress(resolvedIps.first(), targetPort)
                val pingStart = System.currentTimeMillis()
                pingSocket.send(DatagramPacket(pingPacket, pingPacket.size, targetAddr))

                val replyBuf = ByteArray(2048)
                val replyPacket = DatagramPacket(replyBuf, replyBuf.size)
                pingSocket.receive(replyPacket)
                upstreamRakNetLatencyMs = System.currentTimeMillis() - pingStart
                upstreamRakNetReachable = true

                if (replyPacket.length > 35 && replyBuf[0].toInt() and 0xFF == 0x1C) {
                    val motdLen = ((replyBuf[33].toInt() and 0xFF) shl 8) or (replyBuf[34].toInt() and 0xFF)
                    if (motdLen in 1..1000 && 35 + motdLen <= replyPacket.length) {
                        upstreamRakNetMotd = String(replyBuf, 35, motdLen, Charsets.UTF_8)
                    }
                }
                pingSocket.close()
            } catch (_: Exception) {
                upstreamRakNetReachable = false
            }
        }

        // 8. NetherNet: Test Upstream TCP Signaling Port
        var upstreamNetherNetTcpOpen = false
        var upstreamNetherNetLatencyMs = -1L
        if (resolvedIps.isNotEmpty()) {
            try {
                val tcpStart = System.currentTimeMillis()
                val s = Socket()
                s.connect(InetSocketAddress(resolvedIps.first(), targetPort), 2500)
                upstreamNetherNetLatencyMs = System.currentTimeMillis() - tcpStart
                upstreamNetherNetTcpOpen = true
                s.close()
            } catch (_: Exception) {
                upstreamNetherNetTcpOpen = false
            }
        }

        // 9. Generate Comparative Summary
        val summary = buildString {
            append("=== TRANSPORT TEST COMPARISON (RAKNET vs NETHERNET) ===\n")
            if (sandboxRunning) {
                append("✅ Sandbox Routing: Active (10.0.0.1:$localBindPort)\n")
            } else {
                append("⚠️ Sandbox Routing: INACTIVE (Start sandbox before playing)\n")
            }

            append("\n[1. RAKNET PROTOCOL (UDP)]:\n")
            if (localRakNetResponsive) {
                append("  • Local RakNet Listener: HEALTHY (${localRakNetLatencyMs}ms on 127.0.0.1:$localBindPort)\n")
            } else {
                append("  • Local RakNet Listener: UNRESPONSIVE on port $localBindPort\n")
            }
            if (upstreamRakNetReachable) {
                append("  • Upstream Server: REACHABLE via RakNet (${upstreamRakNetLatencyMs}ms)\n")
                if (upstreamRakNetMotd.isNotBlank()) {
                    append("  • MOTD: ${upstreamRakNetMotd.take(60)}\n")
                }
            } else {
                append("  • Upstream Server: RakNet UDP did not reply on port $targetPort\n")
            }

            append("\n[2. NETHERNET PROTOCOL (WebRTC / TCP)]:\n")
            if (localNetherNetTcpOpen) {
                append("  • Local NetherNet TCP Gateway: OPEN on port $localBindPort\n")
            } else {
                append("  • Local NetherNet TCP Gateway: Not listening\n")
            }
            if (upstreamNetherNetTcpOpen) {
                append("  • Upstream Server: NetherNet TCP signaling ACCEPTED (${upstreamNetherNetLatencyMs}ms)\n")
            } else {
                append("  • Upstream Server: NetherNet TCP connection closed / refused (Server uses classic RakNet UDP)\n")
            }

            append("\n[3. TARGET DNS & HOST]:\n")
            if (resolvedIps.isNotEmpty()) {
                append("  • $targetServer resolved to ${resolvedIps.joinToString(", ")} in ${dnsLookupMs}ms\n")
            } else {
                append("  • DNS FAILURE: Cannot resolve $targetServer\n")
            }
        }

        DiagnosticResult(
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            networkType = networkType,
            sandboxRunning = sandboxRunning,
            proxyState = proxyState,
            localBindPort = localBindPort,
            localRakNetResponsive = localRakNetResponsive,
            localRakNetLatencyMs = localRakNetLatencyMs,
            upstreamRakNetReachable = upstreamRakNetReachable,
            upstreamRakNetLatencyMs = upstreamRakNetLatencyMs,
            upstreamRakNetMotd = upstreamRakNetMotd,
            localNetherNetTcpOpen = localNetherNetTcpOpen,
            upstreamNetherNetTcpOpen = upstreamNetherNetTcpOpen,
            upstreamNetherNetLatencyMs = upstreamNetherNetLatencyMs,
            netherNet7551Active = netherNet7551Active,
            targetServer = "$targetServer:$targetPort",
            targetResolvedIps = resolvedIps,
            dnsLookupMs = dnsLookupMs,
            summary = summary
        )
    }

    private fun createRakNetPingPacket(): ByteArray {
        val magic = byteArrayOf(
            0x00.toByte(), 0xff.toByte(), 0xff.toByte(), 0x00.toByte(),
            0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(),
            0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(),
            0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte()
        )
        val buf = ByteBuffer.allocate(1 + 8 + 16 + 8)
        buf.put(0x01.toByte()) // ID_UNCONNECTED_PING
        buf.putLong(System.currentTimeMillis())
        buf.put(magic)
        buf.putLong(98765432101234L) // Client GUID
        return buf.array()
    }
}
