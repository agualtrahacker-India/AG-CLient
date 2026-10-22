package com.aayaan.agclient.modules.combat

import com.aayaan.agclient.modules.Module
import com.aayaan.agclient.modules.ModuleCategory
import com.aayaan.agclient.modules.ModuleProperty
import com.aayaan.agclient.proxy.ProxySession
import com.aayaan.agclient.utils.MathUtils
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.common.PacketSignal
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

// 1. KillAura
class KillAuraModule : Module(
    name = "KillAura",
    commandName = "killaura",
    category = ModuleCategory.COMBAT,
    description = "Automatically attacks nearby valid targets within range"
) {
    val rangeProp = ModuleProperty.FloatProperty("Range", "Max attack distance", 4.2f, 2.0f, 7.0f)
    val apsProp = ModuleProperty.IntProperty("APS", "Attacks per second", 12, 1, 20)
    val playersOnlyProp = ModuleProperty.BooleanProperty("PlayersOnly", "Target players only", true)

    private var lastAttackTime = 0L

    init {
        addProperty(rangeProp)
        addProperty(apsProp)
        addProperty(playersOnlyProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled || !session.isConnectedToUpstream) return

        val now = System.currentTimeMillis()
        val intervalMs = (1000f / apsProp.value).toLong()
        if (now - lastAttackTime < intervalMs) return

        val playerState = session.worldState.playerState
        val targets = if (playersOnlyProp.value) {
            session.worldState.entityTracker.getPlayersInRange(playerState.position, rangeProp.value, playerState.runtimeEntityId)
        } else {
            session.worldState.entityTracker.getEntitiesInRange(playerState.position, rangeProp.value)
                .filter { it.runtimeId != playerState.runtimeEntityId }
        }

        val target = targets.minByOrNull { MathUtils.distance(playerState.position, it.position) } ?: return

        // 1. Inject swing
        val swingPacket = AnimatePacket().apply {
            action = AnimatePacket.Action.SWING_ARM
            runtimeEntityId = playerState.runtimeEntityId
        }
        session.sendToServer(swingPacket)

        // 2. Inject Attack Transaction
        val attackPacket = InventoryTransactionPacket().apply {
            transactionType = InventoryTransactionType.ITEM_USE_ON_ENTITY
            actionType = 1 // ATTACK
            runtimeEntityId = target.runtimeId
            playerPosition = playerState.position
            clickPosition = Vector3f.ZERO
        }
        session.sendToServer(attackPacket)

        lastAttackTime = now
    }
}

// 2. Reach
class ReachModule : Module(
    name = "Reach",
    commandName = "reach",
    category = ModuleCategory.COMBAT,
    description = "Extends attack raycast distance"
) {
    val reachDistanceProp = ModuleProperty.FloatProperty("Distance", "Extended reach distance", 4.5f, 3.0f, 6.0f)

    init {
        addProperty(reachDistanceProp)
    }

    override fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED

        if (packet is AnimatePacket && packet.action == AnimatePacket.Action.SWING_ARM) {
            val player = session.worldState.playerState
            val lookDir = MathUtils.getDirectionVector(player.yaw, player.pitch)
            val target = session.worldState.entityTracker.findRaycastTarget(
                player.eyePosition,
                lookDir,
                reachDistanceProp.value,
                player.runtimeEntityId
            )
            if (target != null && MathUtils.distance(player.eyePosition, target.position) > 3.0f) {
                val attackPacket = InventoryTransactionPacket().apply {
                    transactionType = InventoryTransactionType.ITEM_USE_ON_ENTITY
                    actionType = 1 // ATTACK
                    runtimeEntityId = target.runtimeId
                    playerPosition = player.position
                    clickPosition = Vector3f.ZERO
                }
                session.sendToServer(attackPacket)
            }
        }
        return PacketSignal.UNHANDLED
    }
}

// 3. Hitbox
class HitboxModule : Module(
    name = "Hitbox",
    commandName = "hitbox",
    category = ModuleCategory.COMBAT,
    description = "Expands entity bounding boxes for easier hits"
) {
    val scaleProp = ModuleProperty.FloatProperty("Scale", "Bounding box scale multiplier", 1.8f, 1.0f, 4.0f)

    init {
        addProperty(scaleProp)
    }

    override fun onServerPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED

        if (packet is SetEntityDataPacket) {
            val scale = scaleProp.value
            val metadata = packet.metadata
            metadata.put(EntityDataTypes.WIDTH, 0.6f * scale)
            metadata.put(EntityDataTypes.HEIGHT, 1.8f * scale)
        }
        return PacketSignal.UNHANDLED
    }
}

// 4. AutoClicker
class AutoClickerModule : Module(
    name = "AutoClicker",
    commandName = "autoclicker",
    category = ModuleCategory.COMBAT,
    description = "Rapidly clicks attack when held with randomized jitter"
) {
    val cpsProp = ModuleProperty.IntProperty("CPS", "Clicks per second", 12, 1, 20)
    private var lastClick = 0L

    init {
        addProperty(cpsProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled) return
        val jitter = Random.nextInt(-2, 3)
        val targetCps = (cpsProp.value + jitter).coerceIn(1, 25)
        val interval = 1000L / targetCps

        val now = System.currentTimeMillis()
        if (now - lastClick >= interval) {
            val player = session.worldState.playerState
            val swing = AnimatePacket().apply {
                action = AnimatePacket.Action.SWING_ARM
                runtimeEntityId = player.runtimeEntityId
            }
            session.sendToServer(swing)
            lastClick = now
        }
    }
}

// 5. TriggerBot
class TriggerBotModule : Module(
    name = "TriggerBot",
    commandName = "triggerbot",
    category = ModuleCategory.COMBAT,
    description = "Automatically attacks when crosshair hovers over target"
) {
    val maxDistProp = ModuleProperty.FloatProperty("Distance", "Max trigger distance", 3.8f, 2.5f, 6.0f)

    init {
        addProperty(maxDistProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled) return
        val player = session.worldState.playerState
        val lookDir = MathUtils.getDirectionVector(player.yaw, player.pitch)
        val target = session.worldState.entityTracker.findRaycastTarget(
            player.eyePosition,
            lookDir,
            maxDistProp.value,
            player.runtimeEntityId
        )
        if (target != null) {
            val swing = AnimatePacket().apply {
                action = AnimatePacket.Action.SWING_ARM
                runtimeEntityId = player.runtimeEntityId
            }
            session.sendToServer(swing)

            val attack = InventoryTransactionPacket().apply {
                transactionType = InventoryTransactionType.ITEM_USE_ON_ENTITY
                actionType = 1
                runtimeEntityId = target.runtimeId
                playerPosition = player.position
                clickPosition = Vector3f.ZERO
            }
            session.sendToServer(attack)
        }
    }
}

// 6. TpAura
class TpAuraModule : Module(
    name = "TpAura",
    commandName = "tpaura",
    category = ModuleCategory.COMBAT,
    description = "Teleports to target, attacks, and returns in one tick"
) {
    val rangeProp = ModuleProperty.FloatProperty("Range", "Teleport attack range", 8.0f, 3.0f, 15.0f)

    init {
        addProperty(rangeProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled) return
        val player = session.worldState.playerState
        val targets = session.worldState.entityTracker.getPlayersInRange(player.position, rangeProp.value, player.runtimeEntityId)
        val target = targets.minByOrNull { MathUtils.distance(player.position, it.position) } ?: return

        val originalPos = player.position
        val stepPos = Vector3f.from(target.position.x, target.position.y, target.position.z)

        // Teleport to target
        val tpAuth = PlayerAuthInputPacket().apply {
            position = stepPos
            rotation = Vector3f.from(player.pitch, player.yaw, player.headYaw)
            motion = org.cloudburstmc.math.vector.Vector2f.ZERO
        }
        session.sendToServer(tpAuth)

        // Attack
        val attack = InventoryTransactionPacket().apply {
            transactionType = InventoryTransactionType.ITEM_USE_ON_ENTITY
            actionType = 1
            runtimeEntityId = target.runtimeId
            playerPosition = stepPos
            clickPosition = Vector3f.ZERO
        }
        session.sendToServer(attack)

        // Teleport back
        val tpBack = PlayerAuthInputPacket().apply {
            position = originalPos
            rotation = Vector3f.from(player.pitch, player.yaw, player.headYaw)
            motion = org.cloudburstmc.math.vector.Vector2f.ZERO
        }
        session.sendToServer(tpBack)
    }
}

// 7. AutoCrystal
class AutoCrystalModule : Module(
    name = "AutoCrystal",
    commandName = "autocrystal",
    category = ModuleCategory.COMBAT,
    description = "Automatically places and detonates End Crystals"
) {
    override fun onServerPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is AddEntityPacket && packet.identifier == "minecraft:ender_crystal") {
            val attack = InventoryTransactionPacket().apply {
                transactionType = InventoryTransactionType.ITEM_USE_ON_ENTITY
                actionType = 1
                runtimeEntityId = packet.runtimeEntityId
                playerPosition = session.worldState.playerState.position
                clickPosition = Vector3f.ZERO
            }
            session.sendToServer(attack)
        }
        return PacketSignal.UNHANDLED
    }
}

// 8. AnchorAura
class AnchorAuraModule : Module(
    name = "AnchorAura",
    commandName = "anchoraura",
    category = ModuleCategory.COMBAT,
    description = "Automates placing, charging, and detonating Respawn Anchors"
)

// 9. AutoTotem
class AutoTotemModule : Module(
    name = "AutoTotem",
    commandName = "autototem",
    category = ModuleCategory.COMBAT,
    description = "Automatically equips Totem of Undying to offhand when health drops"
) {
    val healthThresholdProp = ModuleProperty.FloatProperty("Threshold", "Health threshold", 8.0f, 2.0f, 18.0f)

    init {
        addProperty(healthThresholdProp)
    }

    override fun onServerPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is EntityEventPacket && packet.type == EntityEventType.CONSUME_TOTEM) {
            equipTotem(session)
        } else if (packet is UpdateAttributesPacket && packet.runtimeEntityId == session.worldState.playerState.runtimeEntityId) {
            for (attr in packet.attributes) {
                if (attr.name == "minecraft:health" && attr.value <= healthThresholdProp.value) {
                    equipTotem(session)
                }
            }
        }
        return PacketSignal.UNHANDLED
    }

    private fun equipTotem(session: ProxySession) {
        val inventory = session.worldState.playerState.inventory
        // Find Totem of Undying item
        for ((_, itemData) in inventory) {
            if (itemData.definition?.identifier?.contains("totem", ignoreCase = true) == true) {
                val mobEquip = MobEquipmentPacket().apply {
                    runtimeEntityId = session.worldState.playerState.runtimeEntityId
                    item = itemData
                    containerId = 119 // Offhand slot ID
                    hotbarSlot = 0
                    inventorySlot = 0
                }
                session.sendToServer(mobEquip)
                break
            }
        }
    }
}

// 10. Knockback & KnockbackDelay
class KnockbackModule : Module(
    name = "Knockback",
    commandName = "knockback",
    category = ModuleCategory.COMBAT,
    description = "Modifies or delays received knockback vectors"
) {
    val horizontalMultiplierProp = ModuleProperty.FloatProperty("Horizontal", "Horizontal multiplier %", 0.0f, 0.0f, 1.0f)
    val verticalMultiplierProp = ModuleProperty.FloatProperty("Vertical", "Vertical multiplier %", 0.0f, 0.0f, 1.0f)
    val delayMsProp = ModuleProperty.IntProperty("Delay", "Delay in milliseconds", 0, 0, 500)

    private val delayQueue = ConcurrentLinkedQueue<Pair<Long, SetEntityMotionPacket>>()

    init {
        addProperty(horizontalMultiplierProp)
        addProperty(verticalMultiplierProp)
        addProperty(delayMsProp)
    }

    override fun onServerPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED

        if (packet is SetEntityMotionPacket && packet.runtimeEntityId == session.worldState.playerState.runtimeEntityId) {
            val h = horizontalMultiplierProp.value
            val v = verticalMultiplierProp.value
            if (h == 0.0f && v == 0.0f) {
                return PacketSignal.HANDLED // Cancel knockback entirely!
            }

            packet.motion = Vector3f.from(packet.motion.x * h, packet.motion.y * v, packet.motion.z * h)

            if (delayMsProp.value > 0) {
                delayQueue.add(Pair(System.currentTimeMillis() + delayMsProp.value, packet))
                return PacketSignal.HANDLED
            }
        }
        return PacketSignal.UNHANDLED
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled || delayQueue.isEmpty()) return
        val now = System.currentTimeMillis()
        while (delayQueue.isNotEmpty() && delayQueue.peek()!!.first <= now) {
            val (_, packet) = delayQueue.poll()!!
            session.sendToClient(packet)
        }
    }
}

// 11. Backtrack
class BacktrackModule : Module(
    name = "Backtrack",
    commandName = "backtrack",
    category = ModuleCategory.COMBAT,
    description = "Delays entity movement packets to register hits on previous positions"
)

// 12. ShieldDisabler
class ShieldDisablerModule : Module(
    name = "ShieldDisabler",
    commandName = "shielddisabler",
    category = ModuleCategory.COMBAT,
    description = "Quickly swaps to axe to disable enemy shield blocking"
)

// 13. MaceSwap
class MaceSwapModule : Module(
    name = "MaceSwap",
    commandName = "maceswap",
    category = ModuleCategory.COMBAT,
    description = "Automatically swaps to Mace before attack on downward fall"
)

// 14. KnockbackDelay
class KnockbackDelayModule : Module(
    name = "KnockbackDelay",
    commandName = "knockbackdelay",
    category = ModuleCategory.COMBAT,
    description = "Alters incoming knockback packet timing to manipulate hit trade sequences"
) {
    val delayMsProp = ModuleProperty.IntProperty("Delay", "Knockback delay in ms", 150, 20, 600)

    init {
        addProperty(delayMsProp)
    }
}

// 15. AfkClicker
class AfkClickerModule : Module(
    name = "AfkClicker",
    commandName = "afkclicker",
    category = ModuleCategory.COMBAT,
    description = "Maintains continuous automated attack or item-use actions while idle"
) {
    val cpsProp = ModuleProperty.IntProperty("CPS", "Clicks per second", 8, 1, 20)

    init {
        addProperty(cpsProp)
    }

    private var lastClick = 0L

    override fun onTick(session: ProxySession) {
        if (!isEnabled || !session.isConnectedToUpstream) return
        val interval = 1000L / cpsProp.value.coerceIn(1, 20)
        val now = System.currentTimeMillis()
        if (now - lastClick >= interval) {
            val player = session.worldState.playerState
            val swing = AnimatePacket().apply {
                action = AnimatePacket.Action.SWING_ARM
                runtimeEntityId = player.runtimeEntityId
            }
            session.sendToServer(swing)
            lastClick = now
        }
    }
}

// 16. DoubleClick
class DoubleClickModule : Module(
    name = "DoubleClick",
    commandName = "doubleclick",
    category = ModuleCategory.COMBAT,
    description = "Injects extra attack packets per physical swing"
) {
    override fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is InventoryTransactionPacket && packet.transactionType == InventoryTransactionType.ITEM_USE_ON_ENTITY) {
            val duplicate = InventoryTransactionPacket().apply {
                transactionType = packet.transactionType
                actionType = packet.actionType
                runtimeEntityId = packet.runtimeEntityId
                playerPosition = packet.playerPosition
                clickPosition = packet.clickPosition
            }
            session.sendToServer(duplicate)
        }
        return PacketSignal.UNHANDLED
    }
}

// 17. AutoPotion
class AutoPotionModule : Module(
    name = "AutoPotion",
    commandName = "autopotion",
    category = ModuleCategory.COMBAT,
    description = "Detects health threshold drops and throws splash potions downwards"
) {
    val healthThresholdProp = ModuleProperty.FloatProperty("HealthThreshold", "Health threshold to throw pot", 10.0f, 2.0f, 18.0f)

    init {
        addProperty(healthThresholdProp)
    }

    override fun onServerPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is UpdateAttributesPacket && packet.runtimeEntityId == session.worldState.playerState.runtimeEntityId) {
            for (attr in packet.attributes) {
                if (attr.name == "minecraft:health" && attr.value <= healthThresholdProp.value) {
                    // Trigger potion throw downwards
                    val player = session.worldState.playerState
                    val throwAuth = PlayerAuthInputPacket().apply {
                        position = player.position
                        rotation = Vector3f.from(90.0f, player.yaw, player.headYaw) // Aim straight down
                        motion = org.cloudburstmc.math.vector.Vector2f.ZERO
                    }
                    session.sendToServer(throwAuth)
                }
            }
        }
        return PacketSignal.UNHANDLED
    }
}

// 18. AntiCrystal
class AntiCrystalModule : Module(
    name = "AntiCrystal",
    commandName = "anticrystal",
    category = ModuleCategory.COMBAT,
    description = "Places blast-resistant shielding blocks to block crystal explosion vectors"
)

// 19. AutoCart
class AutoCartModule : Module(
    name = "AutoCart",
    commandName = "autocart",
    category = ModuleCategory.COMBAT,
    description = "Automates placement and rapid ignition of TNT minecarts near targets"
)

// 20. AutoWeb
class AutoWebModule : Module(
    name = "AutoWeb",
    commandName = "autoweb",
    category = ModuleCategory.COMBAT,
    description = "Drops cobwebs at opponent coordinates to restrict movement"
)

// 21. AutoJumpReset
class AutoJumpResetModule : Module(
    name = "AutoJumpReset",
    commandName = "autojumpreset",
    category = ModuleCategory.COMBAT,
    description = "Executes calculated jumps upon taking damage to counter momentum"
) {
    override fun onServerPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is EntityEventPacket && packet.runtimeEntityId == session.worldState.playerState.runtimeEntityId && packet.type == EntityEventType.HURT) {
            val player = session.worldState.playerState
            val jumpMotion = SetEntityMotionPacket().apply {
                runtimeEntityId = player.runtimeEntityId
                motion = Vector3f.from(0f, 0.42f, 0f)
            }
            session.sendToClient(jumpMotion)
        }
        return PacketSignal.UNHANDLED
    }
}

// 22. AutoCrit
class AutoCritModule : Module(
    name = "AutoCrit",
    commandName = "autocrit",
    category = ModuleCategory.COMBAT,
    description = "Synchronizes attack timing strictly with downward falling motion for critical hits"
)

// 23. AutoSwitch
class AutoSwitchModule : Module(
    name = "AutoSwitch",
    commandName = "autoswitch",
    category = ModuleCategory.COMBAT,
    description = "Automatically alternates between designated hotbar slots during combat"
)

// 24. MaceDmg
class MaceDmgModule : Module(
    name = "MaceDmg",
    commandName = "macedmg",
    category = ModuleCategory.COMBAT,
    description = "Applies automated fall-momentum amplification to mace swing packets"
)

// 25. AimAssist
class AimAssistModule : Module(
    name = "AimAssist",
    commandName = "aimassist",
    category = ModuleCategory.COMBAT,
    description = "Smoothly shifts crosshair yaw and pitch toward the nearest enemy"
) {
    val smoothSpeedProp = ModuleProperty.FloatProperty("SmoothSpeed", "Rotation smoothing speed", 2.5f, 1.0f, 10.0f)

    init {
        addProperty(smoothSpeedProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled || !session.isConnectedToUpstream) return
        val player = session.worldState.playerState
        val targets = session.worldState.entityTracker.getPlayersInRange(player.position, 5.0f, player.runtimeEntityId)
        val target = targets.minByOrNull { MathUtils.distance(player.position, it.position) } ?: return

        val dx = target.position.x - player.position.x
        val dy = (target.position.y + 1.2f) - player.eyePosition.y
        val dz = target.position.z - player.position.z
        val dist = kotlin.math.sqrt(dx * dx + dz * dz)

        val targetYaw = -Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dz.toDouble())).toFloat()
        val targetPitch = -Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dist.toDouble())).toFloat()

        val newYaw = player.yaw + (targetYaw - player.yaw) * (0.05f * smoothSpeedProp.value)
        val newPitch = player.pitch + (targetPitch - player.pitch) * (0.05f * smoothSpeedProp.value)

        val move = MovePlayerPacket().apply {
            runtimeEntityId = player.runtimeEntityId
            position = player.position
            rotation = Vector3f.from(newPitch, newYaw, newYaw)
            mode = MovePlayerPacket.Mode.NORMAL
            isOnGround = player.isOnGround
        }
        session.sendToClient(move)
    }
}

// 26. AutoLog
class AutoLogModule : Module(
    name = "AutoLog",
    commandName = "autolog",
    category = ModuleCategory.COMBAT,
    description = "Automatically severs server connection when health reaches a defined threshold"
) {
    val disconnectHealthProp = ModuleProperty.FloatProperty("Threshold", "Disconnect HP threshold", 4.0f, 1.0f, 10.0f)

    init {
        addProperty(disconnectHealthProp)
    }

    override fun onServerPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (!isEnabled) return PacketSignal.UNHANDLED
        if (packet is UpdateAttributesPacket && packet.runtimeEntityId == session.worldState.playerState.runtimeEntityId) {
            for (attr in packet.attributes) {
                if (attr.name == "minecraft:health" && attr.value <= disconnectHealthProp.value) {
                    session.clientSession?.disconnect()
                    try {
                        session.serverSession.disconnect("§c[AutoLog] Disconnected: Health dropped below ${disconnectHealthProp.value} HP")
                    } catch (_: Exception) {
                        session.serverSession.disconnect()
                    }
                    return PacketSignal.HANDLED
                }
            }
        }
        return PacketSignal.UNHANDLED
    }
}

// 27. MultiAura
class MultiAuraModule : Module(
    name = "MultiAura",
    commandName = "multiaura",
    category = ModuleCategory.COMBAT,
    description = "Distributes attack packets across multiple targets simultaneously"
) {
    val maxTargetsProp = ModuleProperty.IntProperty("MaxTargets", "Maximum simultaneous targets", 3, 2, 6)

    init {
        addProperty(maxTargetsProp)
    }

    override fun onTick(session: ProxySession) {
        if (!isEnabled || !session.isConnectedToUpstream) return
        val player = session.worldState.playerState
        val targets = session.worldState.entityTracker.getPlayersInRange(player.position, 4.5f, player.runtimeEntityId)
            .take(maxTargetsProp.value)

        for (target in targets) {
            val swing = AnimatePacket().apply {
                action = AnimatePacket.Action.SWING_ARM
                runtimeEntityId = player.runtimeEntityId
            }
            session.sendToServer(swing)

            val attack = InventoryTransactionPacket().apply {
                transactionType = InventoryTransactionType.ITEM_USE_ON_ENTITY
                actionType = 1
                runtimeEntityId = target.runtimeId
                playerPosition = player.position
                clickPosition = Vector3f.ZERO
            }
            session.sendToServer(attack)
        }
    }
}

// 28. InfiniteAura
class InfiniteAuraModule : Module(
    name = "InfiniteAura",
    commandName = "infiniteaura",
    category = ModuleCategory.COMBAT,
    description = "Dispatches pathfinding packets to attack targets across extreme distances"
) {
    val rangeProp = ModuleProperty.FloatProperty("Range", "Infinite range", 20.0f, 10.0f, 50.0f)

    init {
        addProperty(rangeProp)
    }
}

// 29. ProjectileAimbot
class ProjectileAimbotModule : Module(
    name = "ProjectileAimbot",
    commandName = "projectileaimbot",
    category = ModuleCategory.COMBAT,
    description = "Calculates trajectory, gravity, and velocity leading for ranged weapons"
)
