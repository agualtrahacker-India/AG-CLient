package com.aayaan.agclient.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aayaan.agclient.proxy.ProxySession
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.common.PacketSignal

abstract class Module(
    val name: String,
    val commandName: String,
    val category: ModuleCategory,
    val description: String
) {
    var isEnabled: Boolean by mutableStateOf(false)

    val properties: MutableList<ModuleProperty<*>> = mutableListOf()

    fun toggle(session: ProxySession? = null): Boolean {
        return if (isEnabled) {
            disable(session)
            false
        } else {
            enable(session)
            true
        }
    }

    fun enable(session: ProxySession? = null) {
        if (!isEnabled) {
            isEnabled = true
            onEnable(session)
        }
    }

    fun disable(session: ProxySession? = null) {
        if (isEnabled) {
            isEnabled = false
            onDisable(session)
        }
    }

    open fun onEnable(session: ProxySession?) {}
    open fun onDisable(session: ProxySession?) {}
    open fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal = PacketSignal.UNHANDLED
    open fun onServerPacket(packet: BedrockPacket, session: ProxySession): PacketSignal = PacketSignal.UNHANDLED
    open fun onTick(session: ProxySession) {}

    fun addProperty(prop: ModuleProperty<*>) {
        properties.add(prop)
    }

    fun getBooleanProperty(propName: String): Boolean {
        return (properties.firstOrNull { it.name.equals(propName, ignoreCase = true) } as? ModuleProperty.BooleanProperty)?.value ?: false
    }

    fun getFloatProperty(propName: String): Float {
        return (properties.firstOrNull { it.name.equals(propName, ignoreCase = true) } as? ModuleProperty.FloatProperty)?.value ?: 0f
    }

    fun getIntProperty(propName: String): Int {
        return (properties.firstOrNull { it.name.equals(propName, ignoreCase = true) } as? ModuleProperty.IntProperty)?.value ?: 0
    }

    fun getStringProperty(propName: String): String {
        return (properties.firstOrNull { it.name.equals(propName, ignoreCase = true) } as? ModuleProperty.StringProperty)?.value ?: ""
    }
}
