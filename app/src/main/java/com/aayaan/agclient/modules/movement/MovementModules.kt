package com.aayaan.agclient.modules.movement

import com.aayaan.agclient.modules.Module
import com.aayaan.agclient.modules.ModuleCategory
import com.aayaan.agclient.modules.ModuleProperty
import com.aayaan.agclient.proxy.ProxySession
import com.aayaan.agclient.utils.MathUtils
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.AttributeData
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.common.PacketSignal

// 1. Fly
class FlyModule : Module(
    name = "Fly",
    commandName = "fly",
    category = ModuleCategory.MOVEMENT,
    description = "Enables creative flight or velocity flight in survival"
) {
    val speedProp = ModuleProperty.FloatProperty("Speed", "Flight speed", 1.2f, 0.5f, 5.0f)
    val modeProp = ModuleProperty.ModeProperty("Mode", "Flight mode", "Velocity", listOf("Velocity", "Glide"))

    init {
        addProperty(speedProp)
        addProperty(modeProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled) return
        val player = session.worldState.playerState
        val motion = SetEntityMotionPacket().apply {
            runtimeEntityId = player.runtimeEntityId
            this.motion = Vector3f.from(0f, 0.05f * speedProp.value, 0f)
        }
        session.sendToClient(motion)
    }
}

// 2. Speed / Bunnyhop
class SpeedModule : Module(
    name = "Speed",
    commandName = "speed",
    category = ModuleCategory.MOVEMENT,
    description = "Increases ground movement and sprint speed"
) {
    val speedMultiplierProp = ModuleProperty.FloatProperty("Multiplier", "Speed multiplier", 1.6f, 1.0f, 3.5f)

    init {
        addProperty(speedMultiplierProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled) return
        val player = session.worldState.playerState
        val attrPacket = UpdateAttributesPacket().apply {
            runtimeEntityId = player.runtimeEntityId
            attributes.add(
                AttributeData("minecraft:movement", 0.0f, 10.0f, 0.1f * speedMultiplierProp.value, 0.1f)
            )
        }
        session.sendToClient(attrPacket)
    }
}

// 3. Phase
class PhaseModule : Module(
    name = "Phase",
    commandName = "phase",
    category = ModuleCategory.MOVEMENT,
    description = "Allows walking through solid blocks"
) {
    override fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is PlayerAuthInputPacket) {
            val lookDir = MathUtils.getDirectionVector(packet.rotation.y, packet.rotation.x)
            packet.position = packet.position.add(lookDir.mul(0.2f))
        }
        return PacketSignal.UNHANDLED
    }
}

// 4. Jetpack
class JetpackModule : Module(
    name = "Jetpack",
    commandName = "jetpack",
    category = ModuleCategory.MOVEMENT,
    description = "Propels player upward along look angle while jumping"
) {
    val powerProp = ModuleProperty.FloatProperty("Power", "Thrust power", 0.5f, 0.2f, 1.5f)

    init {
        addProperty(powerProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled) return
        val player = session.worldState.playerState
        if (player.isJumping) {
            val thrust = MathUtils.getDirectionVector(player.yaw, player.pitch).mul(powerProp.value)
            val motionPacket = SetEntityMotionPacket().apply {
                runtimeEntityId = player.runtimeEntityId
                motion = Vector3f.from(thrust.x, thrust.y + 0.3f, thrust.z)
            }
            session.sendToClient(motionPacket)
            session.sendToServer(motionPacket)
        }
    }
}

// 5. BoatFly
class BoatFlyModule : Module(
    name = "BoatFly",
    commandName = "boatfly",
    category = ModuleCategory.MOVEMENT,
    description = "Allows flying while riding a boat"
)

// 6. NoSlow
class NoSlowModule : Module(
    name = "NoSlow",
    commandName = "noslow",
    category = ModuleCategory.MOVEMENT,
    description = "Prevents slowdown while eating, bowing, or sneaking"
) {
    override fun onTick(session: ProxySession) {
        if (!isEnabled) return
        val player = session.worldState.playerState
        if (player.isUsingItem || player.isSneaking) {
            val attrPacket = UpdateAttributesPacket().apply {
                runtimeEntityId = player.runtimeEntityId
                attributes.add(
                    AttributeData("minecraft:movement", 0.0f, 10.0f, 0.1f, 0.1f)
                )
            }
            session.sendToClient(attrPacket)
        }
    }
}

// 7. Step
class StepModule : Module(
    name = "Step",
    commandName = "step",
    category = ModuleCategory.MOVEMENT,
    description = "Allows stepping up full blocks smoothly"
) {
    val heightProp = ModuleProperty.FloatProperty("Height", "Step height (blocks)", 1.5f, 1.0f, 2.5f)

    init {
        addProperty(heightProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled) return
        val player = session.worldState.playerState
        val dataPacket = SetEntityDataPacket().apply {
            runtimeEntityId = player.runtimeEntityId
            metadata.put(EntityDataTypes.WIDTH, 0.6f)
            metadata.put(EntityDataTypes.HEIGHT, 1.8f)
        }
        session.sendToClient(dataPacket)
    }
}

// 8. WaterWalk
class WaterWalkModule : Module(
    name = "WaterWalk",
    commandName = "waterwalk",
    category = ModuleCategory.MOVEMENT,
    description = "Allows walking on water and lava surfaces"
) {
    override fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is PlayerAuthInputPacket) {
            val footY = packet.position.y.toInt()
            val blockBelow = session.worldState.chunkCache.getBlock(packet.position.x.toInt(), footY - 1, packet.position.z.toInt())
            // If water or lava runtime ID
            if (blockBelow != 0) {
                packet.motion = org.cloudburstmc.math.vector.Vector2f.from(packet.motion.x, 0.0f)
            }
        }
        return PacketSignal.UNHANDLED
    }
}

// 9. AirJump
class AirJumpModule : Module(
    name = "AirJump",
    commandName = "airjump",
    category = ModuleCategory.MOVEMENT,
    description = "Allows jumping repeatedly in mid-air"
) {
    override fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is PlayerAuthInputPacket) {
            val isJump = packet.inputData.any { it.name.contains("JUMP", ignoreCase = true) }
            if (isJump) {
                val motionPacket = SetEntityMotionPacket().apply {
                    runtimeEntityId = session.worldState.playerState.runtimeEntityId
                    motion = Vector3f.from(packet.motion.x, 0.42f, packet.motion.y)
                }
                session.sendToClient(motionPacket)
            }
        }
        return PacketSignal.UNHANDLED
    }
}

// 10. JumpBoost
class JumpBoostModule : Module(
    name = "JumpBoost",
    commandName = "jumpboost",
    category = ModuleCategory.MOVEMENT,
    description = "Client-side modification of jump height mechanics"
) {
    val jumpHeightProp = ModuleProperty.FloatProperty("Height", "Jump height multiplier", 1.5f, 1.0f, 4.0f)

    init {
        addProperty(jumpHeightProp)
    }

    override fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is PlayerAuthInputPacket) {
            val isJump = packet.inputData.any { it.name.contains("JUMP", ignoreCase = true) }
            if (isJump && session.worldState.playerState.isOnGround) {
                val motionPacket = SetEntityMotionPacket().apply {
                    runtimeEntityId = session.worldState.playerState.runtimeEntityId
                    motion = Vector3f.from(0f, 0.42f * jumpHeightProp.value, 0f)
                }
                session.sendToClient(motionPacket)
            }
        }
        return PacketSignal.UNHANDLED
    }
}

// 11. Levitate
class LevitateModule : Module(
    name = "Levitate",
    commandName = "levitate",
    category = ModuleCategory.MOVEMENT,
    description = "Emulates levitation effect packets for gradual vertical ascent"
) {
    val ascentSpeedProp = ModuleProperty.FloatProperty("Speed", "Vertical ascent speed", 0.3f, 0.1f, 1.5f)

    init {
        addProperty(ascentSpeedProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled) return
        val player = session.worldState.playerState
        val motionPacket = SetEntityMotionPacket().apply {
            runtimeEntityId = player.runtimeEntityId
            motion = Vector3f.from(0f, ascentSpeedProp.value, 0f)
        }
        session.sendToClient(motionPacket)
    }
}

// 12. SlowFalling
class SlowFallingModule : Module(
    name = "SlowFalling",
    commandName = "slowfalling",
    category = ModuleCategory.MOVEMENT,
    description = "Clamps maximum negative vertical velocity to simulate slow falling"
) {
    val fallSpeedProp = ModuleProperty.FloatProperty("FallSpeed", "Clamped downward speed", 0.15f, 0.05f, 0.5f)

    init {
        addProperty(fallSpeedProp)
    }

    override fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is PlayerAuthInputPacket) {
            val player = session.worldState.playerState
            if (!player.isOnGround && packet.delta.y < -fallSpeedProp.value) {
                val motionPacket = SetEntityMotionPacket().apply {
                    runtimeEntityId = player.runtimeEntityId
                    motion = Vector3f.from(packet.motion.x, -fallSpeedProp.value, packet.motion.y)
                }
                session.sendToClient(motionPacket)
            }
        }
        return PacketSignal.UNHANDLED
    }
}

// 13. TapTp
class TapTpModule : Module(
    name = "TapTp",
    commandName = "taptp",
    category = ModuleCategory.MOVEMENT,
    description = "Teleports position vectors directly to the block face tapped on-screen"
) {
    override fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is InventoryTransactionPacket && packet.transactionType == org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType.ITEM_USE) {
            val targetPos = Vector3f.from(
                packet.blockPosition.x.toFloat() + 0.5f,
                packet.blockPosition.y.toFloat() + 1.2f,
                packet.blockPosition.z.toFloat() + 0.5f
            )
            val move = MovePlayerPacket().apply {
                runtimeEntityId = session.worldState.playerState.runtimeEntityId
                position = targetPos
                rotation = Vector3f.ZERO
                mode = MovePlayerPacket.Mode.NORMAL
                isOnGround = true
            }
            session.sendToClient(move)
            session.sendToServer(move)
            return PacketSignal.HANDLED
        }
        return PacketSignal.UNHANDLED
    }
}

// 14. MotionFly
class MotionFlyModule : Module(
    name = "MotionFly",
    commandName = "motionfly",
    category = ModuleCategory.MOVEMENT,
    description = "Smooth momentum-based flight using velocity packet injection"
) {
    val flySpeedProp = ModuleProperty.FloatProperty("Speed", "Flight motion speed", 1.5f, 0.5f, 5.0f)

    init {
        addProperty(flySpeedProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled) return
        val player = session.worldState.playerState
        val dir = MathUtils.getDirectionVector(player.yaw, player.pitch).mul(flySpeedProp.value)
        val motion = SetEntityMotionPacket().apply {
            runtimeEntityId = player.runtimeEntityId
            this.motion = dir
        }
        session.sendToClient(motion)
    }
}

// 15. AutoRocket
class AutoRocketModule : Module(
    name = "AutoRocket",
    commandName = "autorocket",
    category = ModuleCategory.MOVEMENT,
    description = "Automatically uses firework rockets at set intervals during elytra glide"
) {
    val intervalSecondsProp = ModuleProperty.FloatProperty("Interval", "Rocket use interval (s)", 3.0f, 1.0f, 8.0f)

    init {
        addProperty(intervalSecondsProp)
    }
}

// 16. AutoElytra
class AutoElytraModule : Module(
    name = "AutoElytra",
    commandName = "autoelytra",
    category = ModuleCategory.MOVEMENT,
    description = "Automatically deploys elytra flight upon entering airborne fall states"
)

// 17. BunnyHop
class BunnyHopModule : Module(
    name = "BunnyHop",
    commandName = "bunnyhop",
    category = ModuleCategory.MOVEMENT,
    description = "Maintains optimal sprint-jump chaining to preserve peak velocity"
) {
    override fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is PlayerAuthInputPacket && session.worldState.playerState.isOnGround) {
            val motion = SetEntityMotionPacket().apply {
                runtimeEntityId = session.worldState.playerState.runtimeEntityId
                this.motion = Vector3f.from(packet.motion.x * 1.25f, 0.42f, packet.motion.y * 1.25f)
            }
            session.sendToClient(motion)
        }
        return PacketSignal.UNHANDLED
    }
}

// 18. Glide
class GlideModule : Module(
    name = "Glide",
    commandName = "glide",
    category = ModuleCategory.MOVEMENT,
    description = "Counteracts gravity deceleration to glide forward over distances"
) {
    val glideRateProp = ModuleProperty.FloatProperty("FallRate", "Glide descent rate", 0.05f, 0.01f, 0.2f)

    init {
        addProperty(glideRateProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled) return
        val player = session.worldState.playerState
        if (!player.isOnGround) {
            val motion = SetEntityMotionPacket().apply {
                runtimeEntityId = player.runtimeEntityId
                this.motion = Vector3f.from(0f, -glideRateProp.value, 0f)
            }
            session.sendToClient(motion)
        }
    }
}
