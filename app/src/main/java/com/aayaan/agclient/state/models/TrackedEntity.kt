package com.aayaan.agclient.state.models

import org.cloudburstmc.math.vector.Vector3f

data class TrackedEntity(
    val uniqueId: Long,
    val runtimeId: Long,
    val entityType: String,
    var position: Vector3f,
    var motion: Vector3f = Vector3f.ZERO,
    var yaw: Float = 0f,
    var pitch: Float = 0f,
    var headYaw: Float = 0f,
    var boundingBoxWidth: Float = 0.6f,
    var boundingBoxHeight: Float = 1.8f,
    var isInvisible: Boolean = false,
    var isBlocking: Boolean = false,
    var nameTag: String = "",
    val historyPositions: ArrayDeque<Vector3f> = ArrayDeque(20)
) {
    fun updatePosition(newPos: Vector3f) {
        if (historyPositions.size >= 20) {
            historyPositions.removeFirst()
        }
        historyPositions.addLast(position)
        this.position = newPos
    }

    val boundingBoxMin: Vector3f
        get() = Vector3f.from(
            position.x - boundingBoxWidth / 2f,
            position.y,
            position.z - boundingBoxWidth / 2f
        )

    val boundingBoxMax: Vector3f
        get() = Vector3f.from(
            position.x + boundingBoxWidth / 2f,
            position.y + boundingBoxHeight,
            position.z + boundingBoxWidth / 2f
        )
}
