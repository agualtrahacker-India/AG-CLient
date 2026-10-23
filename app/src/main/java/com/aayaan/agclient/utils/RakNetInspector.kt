package com.aayaan.agclient.utils

/**
 * High-performance RakNet & NetherNet packet inspector for real-time telemetry and diagnostics.
 * Inspects raw UDP datagrams passing through tun0 and the proxy relay to give clear human-readable logs
 * for BOTH legacy RakNet and modern WebRTC/NetherNet transports.
 */
object RakNetInspector {

    fun inspect(data: ByteArray, offset: Int, length: Int, dstPort: Int = 19133): String {
        if (length <= 0 || offset + length > data.size) return "EMPTY_PACKET"

        // 1. NetherNet LAN Discovery on Port 7551
        if (dstPort == 7551) {
            return "NETHERNET_DISCOVERY_BROADCAST (Port 7551, $length B)"
        }

        // 2. Check for WebRTC STUN Binding (NetherNet Transport)
        if (length >= 20 && isStunMessage(data, offset, length)) {
            val msgType = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            return when (msgType) {
                0x0001 -> "NETHERNET_STUN_BINDING_REQUEST (WebRTC ICE, $length B)"
                0x0101 -> "NETHERNET_STUN_BINDING_RESPONSE (WebRTC ICE, $length B)"
                0x0111 -> "NETHERNET_STUN_BINDING_ERROR (WebRTC ICE, $length B)"
                else -> "NETHERNET_STUN_MESSAGE (type=0x${msgType.toString(16)}, $length B)"
            }
        }

        // 3. Check for DTLS Handshake / Security Layer (NetherNet Cryptographic Handshake)
        val firstByte = data[offset].toInt() and 0xFF
        if (length >= 13 && firstByte in 20..23) {
            val major = data[offset + 1].toInt() and 0xFF
            if (major == 3 || major == 1 || major == 254 || major == 253) {
                return when (firstByte) {
                    20 -> "NETHERNET_DTLS_CHANGE_CIPHER_SPEC ($length B)"
                    21 -> "NETHERNET_DTLS_ALERT ($length B)"
                    22 -> "NETHERNET_DTLS_HANDSHAKE ($length B)"
                    23 -> "NETHERNET_DTLS_APPLICATION_DATA ($length B)"
                    else -> "NETHERNET_DTLS_RECORD ($length B)"
                }
            }
        }

        // 4. Classic RakNet Protocol Parsing
        val hexId = "0x" + firstByte.toString(16).padStart(2, '0').uppercase()

        return when (firstByte) {
            0x05 -> {
                val proto = if (length >= 18) (data[offset + 17].toInt() and 0xFF).toString() else "unknown"
                "$hexId RAK_OPEN_CONN_REQ_1 (mtu=$length, proto=$proto)"
            }
            0x06 -> {
                val mtu = if (length >= 27) {
                    ((data[offset + 25].toInt() and 0xFF) shl 8) or (data[offset + 26].toInt() and 0xFF)
                } else "unknown"
                "$hexId RAK_OPEN_CONN_REPLY_1 (server_mtu=$mtu)"
            }
            0x07 -> "$hexId RAK_OPEN_CONN_REQ_2 (mtu=$length)"
            0x08 -> "$hexId RAK_OPEN_CONN_REPLY_2"
            0x09 -> "$hexId RAK_CONN_REQ"
            0x10 -> "$hexId RAK_CONN_REQ_ACCEPTED"
            0x13 -> "$hexId RAK_NEW_INCOMING_CONN"
            0x15 -> "$hexId RAK_DISCONNECT_NOTIFICATION"
            0x19 -> {
                val serverProto = if (length >= 18) (data[offset + 17].toInt() and 0xFF).toString() else "unknown"
                "$hexId RAK_INCOMPATIBLE_PROTOCOL_VERSION (server_expects_proto=$serverProto)"
            }
            0x01 -> "$hexId RAK_UNCONNECTED_PING"
            0x02 -> "$hexId RAK_UNCONNECTED_PING_OPEN_CONN"
            0x1c -> "$hexId RAK_UNCONNECTED_PONG"
            0x00 -> "$hexId RAK_CONNECTED_PING"
            0x03 -> "$hexId RAK_CONNECTED_PONG"
            in 0x80..0x8f -> {
                val subDesc = inspectDataFrame(data, offset, length)
                "$hexId RAK_DATA_FRAME [$subDesc]"
            }
            0xfe -> "$hexId BEDROCK_BATCH_PACKET"
            else -> "$hexId UNKNOWN_UDP_PACKET ($length B)"
        }
    }

    private fun isStunMessage(data: ByteArray, offset: Int, length: Int): Boolean {
        // STUN Magic Cookie at offset 4..7 is 0x2112A442
        return (data[offset + 4].toInt() and 0xFF == 0x21) &&
                (data[offset + 5].toInt() and 0xFF == 0x12) &&
                (data[offset + 6].toInt() and 0xFF == 0xA4) &&
                (data[offset + 7].toInt() and 0xFF == 0x42)
    }

    private fun inspectDataFrame(data: ByteArray, offset: Int, length: Int): String {
        if (length < 8) return "len=$length"

        for (i in (offset + 4)..(offset + 12).coerceAtMost(offset + length - 1)) {
            val b = data[i].toInt() and 0xFF
            if (b == 0xFE) {
                return "BedrockBatch (0xFE)"
            }
            if (b == 0x78) {
                return "ZlibCompressedData"
            }
        }
        return "RakFrame (seq, len=$length)"
    }
}
