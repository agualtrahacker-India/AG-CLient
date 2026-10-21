package com.aayaan.agclient.state

import com.aayaan.agclient.state.models.PlayerState
import com.aayaan.agclient.state.models.TrackedEntity
import com.aayaan.agclient.utils.Logger
import org.cloudburstmc.protocol.bedrock.packet.*

class WorldStateTracker {

    val playerState = PlayerState()
    val entityTracker = EntityTracker()
    val chunkCache = ChunkCache()

    fun handleClientPacket(packet: BedrockPacket) {
        when (packet) {
            is PlayerAuthInputPacket -> {
                playerState.position = packet.position
                playerState.yaw = packet.rotation.y
                playerState.pitch = packet.rotation.x
                playerState.headYaw = packet.rotation.z
                playerState.motion = org.cloudburstmc.math.vector.Vector3f.from(packet.motion.x, 0f, packet.motion.y)

                val inputNames = packet.inputData.map { it.name }
                playerState.isJumping = inputNames.any { it.contains("JUMP", ignoreCase = true) }
                playerState.isSneaking = inputNames.any { it.contains("SNEAK", ignoreCase = true) }
                playerState.isSprinting = inputNames.any { it.contains("SPRINT", ignoreCase = true) }
            }
            is MovePlayerPacket -> {
                if (packet.runtimeEntityId == playerState.runtimeEntityId) {
                    playerState.position = packet.position
                    playerState.yaw = packet.rotation.y
                    playerState.pitch = packet.rotation.x
                    playerState.headYaw = packet.rotation.z
                    playerState.isOnGround = packet.isOnGround
                }
            }
            is MobEquipmentPacket -> {
                if (packet.runtimeEntityId == playerState.runtimeEntityId) {
                    playerState.selectedHotbarSlot = packet.hotbarSlot
                }
            }
        }
    }

    fun handleServerPacket(packet: BedrockPacket) {
        when (packet) {
            is StartGamePacket -> {
                playerState.runtimeEntityId = packet.runtimeEntityId
                playerState.uniqueEntityId = packet.uniqueEntityId
                playerState.position = packet.playerPosition
                try {
                    packet.rotation?.let { rot ->
                        playerState.pitch = rot.x
                        playerState.yaw = rot.y
                    }
                } catch (_: Exception) {}
                Logger.i("WorldState", "Game started. Player runtime ID: ${packet.runtimeEntityId}")
            }
            is UpdateAttributesPacket -> {
                if (packet.runtimeEntityId == playerState.runtimeEntityId) {
                    for (attr in packet.attributes) {
                        when (attr.name) {
                            "minecraft:health" -> {
                                playerState.health = attr.value
                                playerState.maxHealth = attr.maximum
                            }
                            "minecraft:player.hunger" -> {
                                playerState.hunger = attr.value
                            }
                            "minecraft:movement" -> {
                                playerState.movementSpeed = attr.value
                            }
                        }
                    }
                }
            }
            is InventoryContentPacket -> {
                // Container ID 0 is standard player inventory
                if (packet.containerId == 0) {
                    playerState.inventory.clear()
                    packet.contents.forEachIndexed { index, itemData ->
                        playerState.inventory[index] = itemData
                    }
                }
            }
            is AddPlayerPacket -> {
                val entity = TrackedEntity(
                    uniqueId = packet.uniqueEntityId,
                    runtimeId = packet.runtimeEntityId,
                    entityType = "minecraft:player",
                    position = packet.position,
                    motion = packet.motion,
                    yaw = packet.rotation.y,
                    pitch = packet.rotation.x,
                    headYaw = packet.rotation.z,
                    nameTag = packet.username
                )
                entityTracker.addEntity(entity)
            }
            is AddEntityPacket -> {
                val entity = TrackedEntity(
                    uniqueId = packet.uniqueEntityId,
                    runtimeId = packet.runtimeEntityId,
                    entityType = packet.identifier,
                    position = packet.position,
                    motion = packet.motion,
                    yaw = packet.rotation.y,
                    pitch = packet.rotation.x,
                    headYaw = packet.headRotation
                )
                entityTracker.addEntity(entity)
            }
            is MoveEntityAbsolutePacket -> {
                val entity = entityTracker.getEntity(packet.runtimeEntityId)
                if (entity != null) {
                    entity.updatePosition(packet.position)
                    entity.yaw = packet.rotation.y
                    entity.pitch = packet.rotation.x
                    entity.headYaw = packet.rotation.z
                }
            }
            is RemoveEntityPacket -> {
                entityTracker.removeEntity(packet.uniqueEntityId)
            }
            is UpdateBlockPacket -> {
                chunkCache.setBlock(packet.blockPosition.x, packet.blockPosition.y, packet.blockPosition.z, packet.definition.runtimeId)
            }
            is SetEntityMotionPacket -> {
                if (packet.runtimeEntityId == playerState.runtimeEntityId) {
                    playerState.motion = packet.motion
                } else {
                    entityTracker.getEntity(packet.runtimeEntityId)?.motion = packet.motion
                }
            }
        }
    }

    fun reset() {
        entityTracker.clear()
        chunkCache.clear()
    }
}
