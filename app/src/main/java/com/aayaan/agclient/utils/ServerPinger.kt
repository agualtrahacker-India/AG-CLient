package com.aayaan.agclient.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

data class ServerStatus(
    val isOnline: Boolean,
    val latencyMs: Long = -1,
    val motd: String = "",
    val version: String = "",
    val protocol: Int = 0,
    val playerCount: Int = 0,
    val maxPlayers: Int = 0,
    val errorMessage: String? = null
)

/**
 * Pings target Minecraft Bedrock servers using standard RakNet Unconnected Ping.
 * Accurately detects whether a server exists, is reachable, and is currently online.
 */
object ServerPinger {

    private val RAKNET_MAGIC = byteArrayOf(
        0x00.toByte(), 0xff.toByte(), 0xff.toByte(), 0x00.toByte(),
        0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(),
        0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(),
        0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte()
    )

    suspend fun pingServer(host: String, port: Int, timeoutMs: Int = 3000): ServerStatus = withContext(Dispatchers.IO) {
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) {
            return@withContext ServerStatus(
                isOnline = false,
                errorMessage = "Empty server address"
            )
        }

        val startTime = System.currentTimeMillis()
        var socket: DatagramSocket? = null
        try {
            val address = InetAddress.getByName(trimmedHost)
            socket = DatagramSocket()
            socket.soTimeout = timeoutMs

            // RakNet ID_UNCONNECTED_PING (0x01)
            val sendBuf = ByteBuffer.allocate(1 + 8 + 16 + 8).apply {
                put(0x01.toByte())
                putLong(startTime)
                put(RAKNET_MAGIC)
                putLong(12345678901234L)
            }.array()

            val sendPacket = DatagramPacket(sendBuf, sendBuf.size, address, port)
            socket.send(sendPacket)

            val recvBuf = ByteArray(2048)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
            socket.receive(recvPacket)

            val latency = System.currentTimeMillis() - startTime
            val data = recvPacket.data

            // Verify ID_UNCONNECTED_PONG (0x1c)
            if (recvPacket.length >= 35 && data[0] == 0x1c.toByte()) {
                val motdLength = ((data[33].toInt() and 0xFF) shl 8) or (data[34].toInt() and 0xFF)
                val safeLen = minOf(motdLength, recvPacket.length - 35)
                val motdRaw = String(data, 35, safeLen, Charsets.UTF_8)
                val parts = motdRaw.split(";")

                return@withContext ServerStatus(
                    isOnline = true,
                    latencyMs = latency,
                    motd = parts.getOrNull(1) ?: motdRaw,
                    protocol = parts.getOrNull(2)?.toIntOrNull() ?: 0,
                    version = parts.getOrNull(3) ?: "",
                    playerCount = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                    maxPlayers = parts.getOrNull(5)?.toIntOrNull() ?: 0
                )
            } else {
                return@withContext ServerStatus(
                    isOnline = true,
                    latencyMs = latency,
                    motd = "Server responding",
                    errorMessage = null
                )
            }
        } catch (e: java.net.UnknownHostException) {
            return@withContext ServerStatus(
                isOnline = false,
                errorMessage = "Host does not exist (DNS lookup failed)"
            )
        } catch (e: java.net.SocketTimeoutException) {
            return@withContext ServerStatus(
                isOnline = false,
                errorMessage = "Server offline / unreachable (timed out)"
            )
        } catch (e: Exception) {
            return@withContext ServerStatus(
                isOnline = false,
                errorMessage = e.message ?: "Failed to connect"
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
