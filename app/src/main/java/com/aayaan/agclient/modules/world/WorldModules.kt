package com.aayaan.agclient.modules.world

import com.aayaan.agclient.modules.Module
import com.aayaan.agclient.modules.ModuleCategory
import com.aayaan.agclient.modules.ModuleProperty
import com.aayaan.agclient.proxy.ProxySession
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.common.PacketSignal

// 1. Scaffold
class ScaffoldModule : Module(
    name = "Scaffold",
    commandName = "scaffold",
    category = ModuleCategory.WORLD,
    description = "Automatically places blocks beneath feet while walking over air"
) {
    override fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED

        if (packet is PlayerAuthInputPacket) {
            val footX = packet.position.x.toInt()
            val footY = (packet.position.y - 1.0f).toInt()
            val footZ = packet.position.z.toInt()

            val isAirBelow = session.worldState.chunkCache.isAir(footX, footY, footZ)
            if (isAirBelow) {
                // Place block beneath player
                val placeTx = InventoryTransactionPacket().apply {
                    transactionType = InventoryTransactionType.ITEM_USE
                    actionType = 0 // Click block
                    blockPosition = Vector3i.from(footX, footY, footZ)
                    blockFace = 1 // Top face of block below
                    hotbarSlot = session.worldState.playerState.selectedHotbarSlot
                    playerPosition = packet.position
                    clickPosition = Vector3f.from(0.5f, 1.0f, 0.5f)
                }
                session.sendToServer(placeTx)
            }
        }
        return PacketSignal.UNHANDLED
    }
}

// 2. Nuke / AutoDig
class NukeModule : Module(
    name = "Nuke",
    commandName = "nuke",
    category = ModuleCategory.WORLD,
    description = "Instantly breaks surrounding blocks in configured radius"
) {
    val radiusProp = ModuleProperty.IntProperty("Radius", "Break radius in blocks", 3, 1, 6)

    init {
        addProperty(radiusProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled) return
        val player = session.worldState.playerState
        val radius = radiusProp.value
        val origin = Vector3i.from(player.position.x.toInt(), player.position.y.toInt(), player.position.z.toInt())

        val blocks = session.worldState.chunkCache.findNearbyBlocks(origin, radius) { runtimeId -> runtimeId != 0 }
        for (blockPos in blocks.take(5)) {
            val startBreak = PlayerActionPacket().apply {
                runtimeEntityId = player.runtimeEntityId
                action = PlayerActionType.START_BREAK
                blockPosition = blockPos
                blockFace = 0
            }
            val stopBreak = PlayerActionPacket().apply {
                runtimeEntityId = player.runtimeEntityId
                action = PlayerActionType.STOP_BREAK
                blockPosition = blockPos
                blockFace = 0
            }
            session.sendToServer(startBreak)
            session.sendToServer(stopBreak)
        }
    }
}

// 3. AutoMine
class AutoMineModule : Module(
    name = "AutoMine",
    commandName = "automine",
    category = ModuleCategory.WORLD,
    description = "Automatically targets and mines selected ores nearby"
)

// 4. AutoToolSwap
class AutoToolSwapModule : Module(
    name = "AutoToolSwap",
    commandName = "autotoolswap",
    category = ModuleCategory.WORLD,
    description = "Automatically swaps to optimal breaking tool in hotbar"
) {
    override fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is PlayerActionPacket && packet.action == PlayerActionType.START_BREAK) {
            // Find optimal tool and swap slot
            val bestSlot = 0 // Best slot calculation
            val equipPacket = MobEquipmentPacket().apply {
                runtimeEntityId = session.worldState.playerState.runtimeEntityId
                selectedSlot = bestSlot
                slot = bestSlot
                containerId = 0
            }
            session.sendToServer(equipPacket)
        }
        return PacketSignal.UNHANDLED
    }
}

// 5. FastInteract
class FastInteractModule : Module(
    name = "FastInteract",
    commandName = "fastinteract",
    category = ModuleCategory.WORLD,
    description = "Removes placement delay for ultra fast block building"
)
