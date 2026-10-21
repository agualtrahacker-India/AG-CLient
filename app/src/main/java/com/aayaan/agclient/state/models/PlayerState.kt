package com.aayaan.agclient.state.models

import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData

data class PlayerState(
    var runtimeEntityId: Long = 0L,
    var uniqueEntityId: Long = 0L,
    var position: Vector3f = Vector3f.ZERO,
    var motion: Vector3f = Vector3f.ZERO,
    var yaw: Float = 0f,
    var pitch: Float = 0f,
    var headYaw: Float = 0f,
    var isOnGround: Boolean = true,
    var isJumping: Boolean = false,
    var isSneaking: Boolean = false,
    var isSprinting: Boolean = false,
    var isUsingItem: Boolean = false,
    var health: Float = 20f,
    var maxHealth: Float = 20f,
    var hunger: Float = 20f,
    var movementSpeed: Float = 0.1f,
    var stepHeight: Float = 0.6f,
    var mayFly: Boolean = false,
    var isFlying: Boolean = false,
    var selectedHotbarSlot: Int = 0,
    val inventory: MutableMap<Int, ItemData> = mutableMapOf()
) {
    val eyePosition: Vector3f
        get() = Vector3f.from(position.x, position.y + 1.62f, position.z)
}
