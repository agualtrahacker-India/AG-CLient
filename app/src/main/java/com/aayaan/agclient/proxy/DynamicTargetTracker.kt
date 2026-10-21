package com.aayaan.agclient.proxy

import com.aayaan.agclient.utils.Logger

/**
 * Tracks the dynamically intercepted server target when the user taps any server in Minecraft.
 * Allows zero-configuration automatic routing to whichever server the user connects to.
 */
object DynamicTargetTracker {

    @Volatile
    var activeTargetHost: String? = null
        private set

    @Volatile
    var activeTargetPort: Int = 19132
        private set

    fun setTarget(host: String, port: Int = 19132) {
        activeTargetHost = host
        activeTargetPort = port
        Logger.i("DynamicTargetTracker", "Target updated to $host:$port")
    }

    fun clear() {
        activeTargetHost = null
        activeTargetPort = 19132
    }
}
