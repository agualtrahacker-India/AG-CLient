package com.aayaan.agclient.config

data class ProxyConfig(
    var targetIp: String = "geo.hivebedrock.network",
    var targetPort: Int = 19132,
    var localBindPort: Int = 19132,
    var isXboxAuthEnabled: Boolean = true,
    var isGhostMode: Boolean = false,
    val moduleStates: MutableMap<String, Boolean> = mutableMapOf(),
    val moduleProperties: MutableMap<String, MutableMap<String, Any>> = mutableMapOf()
)
