package com.aayaan.agclient.state

import org.cloudburstmc.math.vector.Vector3i
import java.util.concurrent.ConcurrentHashMap

class ChunkCache {

    // Simple block coordinate hash map for fast query: (x, y, z) -> runtimeId or identifier
    private val blockCache = ConcurrentHashMap<Long, Int>()

    private fun blockKey(x: Int, y: Int, z: Int): Long {
        return (x.toLong() and 0x3FFFFFFL shl 38) or
                (y.toLong() and 0xFFFL shl 26) or
                (z.toLong() and 0x3FFFFFFL)
    }

    fun setBlock(x: Int, y: Int, z: Int, runtimeId: Int) {
        if (runtimeId == 0) {
            blockCache.remove(blockKey(x, y, z))
        } else {
            blockCache[blockKey(x, y, z)] = runtimeId
        }
    }

    fun getBlock(x: Int, y: Int, z: Int): Int {
        return blockCache[blockKey(x, y, z)] ?: 0
    }

    fun isAir(x: Int, y: Int, z: Int): Boolean {
        val id = getBlock(x, y, z)
        return id == 0
    }

    fun findNearbyBlocks(center: Vector3i, radius: Int, filterRuntimeId: (Int) -> Boolean): List<Vector3i> {
        val result = mutableListOf<Vector3i>()
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val x = center.x + dx
                    val y = center.y + dy
                    val z = center.z + dz
                    val id = getBlock(x, y, z)
                    if (filterRuntimeId(id)) {
                        result.add(Vector3i.from(x, y, z))
                    }
                }
            }
        }
        return result
    }

    fun clear() {
        blockCache.clear()
    }
}
