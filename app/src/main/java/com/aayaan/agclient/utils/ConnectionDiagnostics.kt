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
 * local proxy reachability, and upstream server connectivity.
 */
object ConnectionDiagnostics {

    data class DiagnosticResult(
        val deviceModel: String,
        val androidVersion: String,
        val networkType: String,
        val sandboxRunning: Boolean,
        val proxyState: String,
        val localBindPort: Int,
        val localProxyResponsive: Boolean,
        val localProxyLatencyMs: Long,
        val targetServer: String,
        val targetResolvedIps: List<String>,
        val dnsLookupMs: Long,
        val upstreamReachable: Boolean,
        val upstreamLatencyMs: Long,
        val upstreamMotd: String,
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

        // 3. Test Local Proxy Responsiveness on 127.0.0.1:localBindPort
        var localProxyResponsive = false
        var localProxyLatencyMs = -1L
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
                localProxyLatencyMs = System.currentTimeMillis() - start
                localProxyResponsive = true
                testSocket.close()
            } catch (_: Exception) {
                localProxyResponsive = false
            }
        }

        // 4. Test Upstream Target DNS Lookup
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

        // 5. Test Upstream RakNet Ping
        var upstreamReachable = false
        var upstreamLatencyMs = -1L
        var upstreamMotd = ""

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
                upstreamLatencyMs = System.currentTimeMillis() - pingStart
                upstreamReachable = true

                // Extract MOTD from RakNet Unconnected Pong (0x1c)
                if (replyPacket.length > 35 && replyBuf[0].toInt() and 0xFF == 0x1C) {
                    val motdLen = ((replyBuf[33].toInt() and 0xFF) shl 8) or (replyBuf[34].toInt() and 0xFF)
                    if (motdLen in 1..1000 && 35 + motdLen <= replyPacket.length) {
                        upstreamMotd = String(replyBuf, 35, motdLen, Charsets.UTF_8)
                    }
                }
                pingSocket.close()
            } catch (_: Exception) {
                upstreamReachable = false
            }
        }

        // Generate summary
        val summary = buildString {
            if (!sandboxRunning) {
                append("⚠️ Virtual Sandbox is NOT active. Tap START SANDBOX before connecting Minecraft.\n")
            } else {
                append("✅ Virtual Sandbox is active (10.0.0.1:$localBindPort).\n")
            }
            if (localProxyResponsive) {
                append("✅ Local proxy listener is healthy on 127.0.0.1:$localBindPort (${localProxyLatencyMs}ms).\n")
            } else {
                append("❌ Local proxy is NOT responding to UDP ping on port $localBindPort.\n")
            }
            if (resolvedIps.isNotEmpty()) {
                append("✅ Upstream DNS resolved: ${resolvedIps.joinToString(", ")} (${dnsLookupMs}ms).\n")
            } else {
                append("❌ Failed to resolve upstream DNS for '$targetServer'. Verify internet access.\n")
            }
            if (upstreamReachable) {
                append("✅ Upstream server ($targetServer:$targetPort) is ALIVE and reachable (${upstreamLatencyMs}ms).\n")
            } else {
                append("⚠️ Upstream server ($targetServer:$targetPort) did not answer UDP ping. Check if carrier or ISP restricts UDP port $targetPort.\n")
            }
        }

        DiagnosticResult(
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            networkType = networkType,
            sandboxRunning = sandboxRunning,
            proxyState = proxyState,
            localBindPort = localBindPort,
            localProxyResponsive = localProxyResponsive,
            localProxyLatencyMs = localProxyLatencyMs,
            targetServer = "$targetServer:$targetPort",
            targetResolvedIps = resolvedIps,
            dnsLookupMs = dnsLookupMs,
            upstreamReachable = upstreamReachable,
            upstreamLatencyMs = upstreamLatencyMs,
            upstreamMotd = upstreamMotd,
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
