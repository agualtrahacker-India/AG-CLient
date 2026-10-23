package com.aayaan.agclient.utils

/**
 * High-performance RakNet & Bedrock packet inspector for real-time telemetry and diagnostics.
 * Inspects raw UDP datagrams passing through tun0 and the proxy relay to give clear human-readable logs.
 */
object RakNetInspector {

    fun inspect(data: ByteArray, offset: Int, length: Int): String {
        if (length <= 0 || offset + length > data.size) return "EMPTY_PACKET"

        val packetId = data[offset].toInt() and 0xFF
        val hexId = "0x" + packetId.toString(16).padStart(2, '0').uppercase()

        return when (packetId) {
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
            else -> "$hexId UNKNOWN_UDP_PACKET"
        }
    }

    private fun inspectDataFrame(data: ByteArray, offset: Int, length: Int): String {
        // RakNet Data Frame Header is typically 4 bytes (header + sequence number)
        // Then reliability header (1-4 bytes). Bedrock batches begin with 0xfe or zlib header 0x78
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
