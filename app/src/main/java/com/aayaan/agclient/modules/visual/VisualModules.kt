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
                packet.metadata.setFlag(EntityFlag.ALWAYS_SHOW_NAME, true)
                if (nametagsProp.value) {
                    packet.metadata.setFlag(EntityFlag.CAN_SHOW_NAME, true)
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
        } else if (packet is MobEffectPacket && packet.event == MobEffectPacket.Event.ADD) {
            if (packet.effectId == 14) { // Invisibility effect ID
                return PacketSignal.HANDLED // Strip invisibility effect!
            }
        }
        return PacketSignal.UNHANDLED
    }
}

// 8. Freelook
class FreelookModule : Module(
    name = "Freelook",
    commandName = "freelook",
    category = ModuleCategory.VISUAL,
    description = "Uncouples the rendering camera from player character orientation"
)

// 9. ArrayList
class ArrayListModule : Module(
    name = "ArrayList",
    commandName = "arraylist",
    category = ModuleCategory.VISUAL,
    description = "Renders an on-screen overlay of all currently enabled modules"
)

// 10. ArmorHud
class ArmorHudModule : Module(
    name = "ArmorHud",
    commandName = "armorhud",
    category = ModuleCategory.VISUAL,
    description = "Displays current armor condition and durability metrics on the HUD"
)

// 11. InventoryHud
class InventoryHudModule : Module(
    name = "InventoryHud",
    commandName = "inventoryhud",
    category = ModuleCategory.VISUAL,
    description = "Projects inventory slots and item counts onto the active HUD"
)

// 12. TargetHud
class TargetHudModule : Module(
    name = "TargetHud",
    commandName = "targethud",
    category = ModuleCategory.VISUAL,
    description = "Displays current opponent metrics (health, armor, distance)"
)

// 13. BlockTracers
class BlockTracersModule : Module(
    name = "BlockTracers",
    commandName = "blocktracers",
    category = ModuleCategory.VISUAL,
    description = "Renders lines pointing toward specific high-value blocks"
)

// 14. Schematic
class SchematicModule : Module(
    name = "Schematic",
    commandName = "schematic",
    category = ModuleCategory.VISUAL,
    description = "Renders holographic blueprint layers directly into the local environment"
)

// 15. SusChunkFinder
class SusChunkFinderModule : Module(
    name = "SusChunkFinder",
    commandName = "suschunkfinder",
    category = ModuleCategory.VISUAL,
    description = "Flags anomalous chunk states, player activity indicators, and hidden stashes"
)

// 16. Waypoints
class WaypointsModule : Module(
    name = "Waypoints",
    commandName = "waypoints",
    category = ModuleCategory.VISUAL,
    description = "Renders directional beacons and distance calculations for saved map locations"
)
