package com.aayaan.agclient.config

data class ServerHistoryItem(
    val ip: String,
    val port: Int = 19132,
    val name: String = "",
    val lastJoined: Long = System.currentTimeMillis()
)

data class ProxyConfig(
    var targetIp: String = "geo.hivebedrock.network", // Default to The Hive
    var targetPort: Int = 19132,
    var localBindPort: Int = 19133, // Default 19133 to prevent collision with Minecraft on 19132
    var isXboxAuthEnabled: Boolean = true,
    var isGhostMode: Boolean = false,
    val serverHistory: MutableList<ServerHistoryItem> = mutableListOf(),
    val moduleStates: MutableMap<String, Boolean> = mutableMapOf(),
    val moduleProperties: MutableMap<String, MutableMap<String, Any>> = mutableMapOf()
)
