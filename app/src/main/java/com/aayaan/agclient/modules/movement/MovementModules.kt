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
                packet.motion = Vector3f.from(packet.motion.x, 0.0f, packet.motion.z)
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
                    motion = Vector3f.from(packet.motion.x, 0.42f, packet.motion.z)
                }
                session.sendToClient(motionPacket)
            }
        }
        return PacketSignal.UNHANDLED
    }
}
