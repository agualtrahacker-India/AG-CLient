package com.aayaan.agclient.proxy

import com.aayaan.agclient.utils.Logger
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec

/**
 * Dynamically discovers and loads the optimal BedrockCodec for incoming Minecraft clients.
 * Prevents "No PacketCodec is set!" exceptions and connection timeouts across varying client versions.
 */
object BedrockCodecFinder {

    private val codecCache = mutableMapOf<Int, BedrockCodec>()
    private var defaultCodec: BedrockCodec? = null

    private val KNOWN_CODEC_CLASSES = listOf(
        "org.cloudburstmc.protocol.bedrock.codec.v729.Bedrock_v729", // 1.21.30
        "org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712", // 1.21.20
        "org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685", // 1.21.0
        "org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671", // 1.20.80
        "org.cloudburstmc.protocol.bedrock.codec.v662.Bedrock_v662", // 1.20.70
        "org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649", // 1.20.60
        "org.cloudburstmc.protocol.bedrock.codec.v630.Bedrock_v630", // 1.20.50
        "org.cloudburstmc.protocol.bedrock.codec.v622.Bedrock_v622", // 1.20.40
        "org.cloudburstmc.protocol.bedrock.codec.v618.Bedrock_v618", // 1.20.30
        "org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589"  // 1.20.0
    )

    init {
        loadAllCodecs()
    }

    private fun loadAllCodecs() {
        for (className in KNOWN_CODEC_CLASSES) {
            try {
                val clazz = Class.forName(className)
                val field = clazz.getField("CODEC")
                val codec = field.get(null) as? BedrockCodec
                if (codec != null) {
                    val proto = codec.protocolVersion
                    codecCache[proto] = codec
                    if (defaultCodec == null) {
                        defaultCodec = codec // Set the highest available as default
                    }
                    Logger.d("BedrockCodecFinder", "Loaded BedrockCodec: protocol $proto ($className)")
                }
            } catch (_: Throwable) {
                // Class not in current snapshot, ignore safely
            }
        }
        Logger.i("BedrockCodecFinder", "Initialized ${codecCache.size} Bedrock codecs. Default: ${defaultCodec?.protocolVersion}")
    }

    fun findCodec(protocolVersion: Int): BedrockCodec? {
        // 1. Exact match
        codecCache[protocolVersion]?.let { return it }

        // 2. Nearest compatible or default
        return defaultCodec
    }

    fun getDefaultCodec(): BedrockCodec? = defaultCodec
}
