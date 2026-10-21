package com.aayaan.agclient.state

import com.aayaan.agclient.state.models.TrackedEntity
import com.aayaan.agclient.utils.MathUtils
import org.cloudburstmc.math.vector.Vector3f
import java.util.concurrent.ConcurrentHashMap

class EntityTracker {

    private val entities = ConcurrentHashMap<Long, TrackedEntity>()

    fun addEntity(entity: TrackedEntity) {
        entities[entity.runtimeId] = entity
    }

    fun removeEntity(runtimeId: Long): TrackedEntity? {
        return entities.remove(runtimeId)
    }

    fun getEntity(runtimeId: Long): TrackedEntity? {
        return entities[runtimeId]
    }

    fun getAllEntities(): Collection<TrackedEntity> {
        return entities.values
    }

    fun getEntitiesInRange(center: Vector3f, range: Float): List<TrackedEntity> {
        return entities.values.filter { entity ->
            MathUtils.distance(center, entity.position) <= range
        }
    }

    fun getPlayersInRange(center: Vector3f, range: Float, localPlayerRuntimeId: Long): List<TrackedEntity> {
        return entities.values.filter { entity ->
            entity.runtimeId != localPlayerRuntimeId &&
                    (entity.entityType.contains("player", ignoreCase = true) || entity.nameTag.isNotEmpty()) &&
                    MathUtils.distance(center, entity.position) <= range
        }
    }

    fun findRaycastTarget(
        origin: Vector3f,
        direction: Vector3f,
        reach: Float,
        localPlayerRuntimeId: Long
    ): TrackedEntity? {
        var closestTarget: TrackedEntity? = null
        var closestDist = reach

        for (entity in entities.values) {
            if (entity.runtimeId == localPlayerRuntimeId) continue

            val dist = MathUtils.distance(origin, entity.position)
            if (dist > reach) continue

            if (MathUtils.intersectsBoundingBox(origin, direction, entity.boundingBoxMin, entity.boundingBoxMax, reach)) {
                if (dist < closestDist) {
                    closestDist = dist
                    closestTarget = entity
                }
            }
        }
        return closestTarget
    }

    fun clear() {
        entities.clear()
    }
}
