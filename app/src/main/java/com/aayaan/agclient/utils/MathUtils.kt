package com.aayaan.agclient.utils

import org.cloudburstmc.math.vector.Vector3f
import kotlin.math.*

object MathUtils {

    fun distance(pos1: Vector3f, pos2: Vector3f): Float {
        val dx = pos1.x - pos2.x
        val dy = pos1.y - pos2.y
        val dz = pos1.z - pos2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun distanceHorizontal(pos1: Vector3f, pos2: Vector3f): Float {
        val dx = pos1.x - pos2.x
        val dz = pos1.z - pos2.z
        return sqrt(dx * dx + dz * dz)
    }

    fun calculateRotations(from: Vector3f, to: Vector3f): Pair<Float, Float> {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val distHorizontal = sqrt(dx * dx + dz * dz)

        val yaw = (Math.toDegrees(atan2(dz.toDouble(), dx.toDouble())) - 90.0).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy.toDouble(), distHorizontal.toDouble()))).toFloat()

        return Pair(wrapAngle(yaw), wrapAngle(pitch))
    }

    fun wrapAngle(angle: Float): Float {
        var wrapped = angle % 360f
        if (wrapped >= 180f) {
            wrapped -= 360f
        }
        if (wrapped < -180f) {
            wrapped += 360f
        }
        return wrapped
    }

    fun getDirectionVector(yaw: Float, pitch: Float): Vector3f {
        val yawRad = Math.toRadians((-yaw - 90.0)).toFloat()
        val pitchRad = Math.toRadians((-pitch).toDouble()).toFloat()

        val x = cos(pitchRad) * cos(yawRad)
        val y = sin(pitchRad)
        val z = cos(pitchRad) * sin(yawRad)

        return Vector3f.from(x, y, z)
    }

    fun intersectsBoundingBox(
        rayOrigin: Vector3f,
        rayDirection: Vector3f,
        min: Vector3f,
        max: Vector3f,
        maxDistance: Float
    ): Boolean {
        var tmin = (min.x - rayOrigin.x) / if (rayDirection.x != 0f) rayDirection.x else 0.00001f
        var tmax = (max.x - rayOrigin.x) / if (rayDirection.x != 0f) rayDirection.x else 0.00001f

        if (tmin > tmax) {
            val temp = tmin; tmin = tmax; tmax = temp
        }

        var tymin = (min.y - rayOrigin.y) / if (rayDirection.y != 0f) rayDirection.y else 0.00001f
        var tymax = (max.y - rayOrigin.y) / if (rayDirection.y != 0f) rayDirection.y else 0.00001f

        if (tymin > tymax) {
            val temp = tymin; tymin = tymax; tymax = temp
        }

        if ((tmin > tymax) || (tymin > tmax)) return false

        if (tymin > tmin) tmin = tymin
        if (tymax < tmax) tmax = tymax

        var tzmin = (min.z - rayOrigin.z) / if (rayDirection.z != 0f) rayDirection.z else 0.00001f
        var tzmax = (max.z - rayOrigin.z) / if (rayDirection.z != 0f) rayDirection.z else 0.00001f

        if (tzmin > tzmax) {
            val temp = tzmin; tzmin = tzmax; tzmax = temp
        }

        if ((tmin > tzmax) || (tzmin > tmax)) return false

        if (tzmin > tmin) tmin = tzmin
        if (tzmax < tmax) tmax = tzmax

        return tmin in 0f..maxDistance || tmax in 0f..maxDistance
    }
}
