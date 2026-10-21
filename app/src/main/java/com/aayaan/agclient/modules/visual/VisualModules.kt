package com.aayaan.agclient.modules.visual

import com.aayaan.agclient.modules.Module
import com.aayaan.agclient.modules.ModuleCategory
import com.aayaan.agclient.modules.ModuleProperty
import com.aayaan.agclient.proxy.ProxySession
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.common.PacketSignal

// 1. Xray
class XrayModule : Module(
    name = "Xray",
    commandName = "xray",
    category = ModuleCategory.VISUAL,
    description = "Reveals ores by making stone, deepslate, and dirt transparent"
) {
    val opacityProp = ModuleProperty.FloatProperty("Opacity", "Opaque block opacity", 0.2f, 0.0f, 1.0f)

    init {
        addProperty(opacityProp)
    }

    override fun onServerPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        return PacketSignal.UNHANDLED
    }
}

// 2. PlayerESP & ItemESP
class PlayerEspModule : Module(
    name = "PlayerESP",
    commandName = "playeresp",
    category = ModuleCategory.VISUAL,
    description = "Highlights players through walls with glow and nametag flags"
) {
    val nametagsProp = ModuleProperty.BooleanProperty("Nametags", "Always show full nametags", true)

    init {
        addProperty(nametagsProp)
    }

    override fun onServerPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is SetEntityDataPacket) {
            try {
                packet.metadata.setFlag(EntityFlag.GLOWING, true)
                if (nametagsProp.value) {
                    packet.metadata.setFlag(EntityFlag.ALWAYS_SHOW_NAMETAG, true)
                }
            } catch (_: Exception) {}
        }
        return PacketSignal.UNHANDLED
    }
}

class ItemEspModule : Module(
    name = "ItemESP",
    commandName = "itemesp",
    category = ModuleCategory.VISUAL,
    description = "Highlights dropped items on the ground"
)

// 3. Tracers
class TracersModule : Module(
    name = "Tracers",
    commandName = "tracers",
    category = ModuleCategory.VISUAL,
    description = "Draws targeting lines toward nearby player entities"
)

// 4. Freecam
class FreecamModule : Module(
    name = "Freecam",
    commandName = "freecam",
    category = ModuleCategory.VISUAL,
    description = "Allows out-of-body camera flight while body remains frozen"
) {
    private var frozenPosition: Vector3f? = null

    override fun onEnable(session: ProxySession?) {
        session ?: return
        frozenPosition = session.worldState.playerState.position
    }

    override fun onDisable(session: ProxySession?) {
        session ?: return
        val pos = frozenPosition ?: return
        val move = MovePlayerPacket().apply {
            runtimeEntityId = session.worldState.playerState.runtimeEntityId
            position = pos
            rotation = Vector3f.from(0f, 0f, 0f)
            mode = MovePlayerPacket.Mode.NORMAL
            isOnGround = true
        }
        session.sendToServer(move)
        frozenPosition = null
    }

    override fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is PlayerAuthInputPacket) {
            val frozen = frozenPosition
            if (frozen != null) {
                // Keep server body at frozen coordinate
                packet.position = frozen
                packet.motion = org.cloudburstmc.math.vector.Vector2f.ZERO
            }
        }
        return PacketSignal.UNHANDLED
    }
}

// 5. ChunkFinder
class ChunkFinderModule : Module(
    name = "ChunkFinder",
    commandName = "chunkfinder",
    category = ModuleCategory.VISUAL,
    description = "Scans chunk palettes for player-made bases, chests, and portals"
)

// 6. HoleESP
class HoleEspModule : Module(
    name = "HoleESP",
    commandName = "holeesp",
    category = ModuleCategory.VISUAL,
    description = "Detects safe 1x1 obsidian and bedrock blast protection holes"
)

// 7. NoInvisible
class NoInvisibleModule : Module(
    name = "NoInvisible",
    commandName = "noinvisible",
    category = ModuleCategory.VISUAL,
    description = "Forces invisible entities and potion effects to render visibly"
) {
    override fun onServerPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is SetEntityDataPacket) {
            try {
                packet.metadata.setFlag(EntityFlag.INVISIBLE, false)
            } catch (_: Exception) {}
        } else if (packet is MobEffectPacket && packet.eventId == MobEffectPacket.Event.ADD) {
            if (packet.effectId == 14) { // Invisibility effect ID
                return PacketSignal.HANDLED // Strip invisibility effect!
            }
        }
        return PacketSignal.UNHANDLED
    }
}
