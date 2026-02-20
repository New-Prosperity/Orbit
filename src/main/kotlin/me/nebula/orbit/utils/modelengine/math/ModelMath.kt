package me.nebula.orbit.utils.modelengine.math

import net.minestom.server.coordinate.Vec
import kotlin.math.*

data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {
    operator fun get(index: Int): Float = when (index) {
        0 -> x; 1 -> y; 2 -> z; 3 -> w
        else -> throw IndexOutOfBoundsException(index)
    }

    fun toFloatArray(): FloatArray = floatArrayOf(x, y, z, w)
}

val QUAT_IDENTITY = Quat(0f, 0f, 0f, 1f)

fun eulerToQuat(pitchDeg: Float, yawDeg: Float, rollDeg: Float): Quat {
    val px = Math.toRadians(pitchDeg.toDouble()).toFloat() * 0.5f
    val yy = Math.toRadians(yawDeg.toDouble()).toFloat() * 0.5f
    val rz = Math.toRadians(rollDeg.toDouble()).toFloat() * 0.5f

    val cx = cos(px); val sx = sin(px)
    val cy = cos(yy); val sy = sin(yy)
    val cz = cos(rz); val sz = sin(rz)

    return Quat(
        x = sx * cy * cz - cx * sy * sz,
        y = cx * sy * cz + sx * cy * sz,
        z = cx * cy * sz - sx * sy * cz,
        w = cx * cy * cz + sx * sy * sz,
    )
}

fun quatToEuler(q: Quat): Triple<Float, Float, Float> {
    val sinPitch = 2f * (q.w * q.x - q.y * q.z)
    val pitch: Float
    val yaw: Float
    val roll: Float

    if (abs(sinPitch) >= 0.999f) {
        pitch = (PI / 2.0).toFloat() * sign(sinPitch)
        yaw = 2f * atan2(q.y, q.w)
        roll = 0f
    } else {
        pitch = asin(sinPitch)
        val sinYaw = 2f * (q.w * q.y + q.x * q.z)
        val cosYaw = 1f - 2f * (q.x * q.x + q.y * q.y)
        yaw = atan2(sinYaw, cosYaw)
        val sinRoll = 2f * (q.w * q.z + q.x * q.y)
        val cosRoll = 1f - 2f * (q.x * q.x + q.z * q.z)
        roll = atan2(sinRoll, cosRoll)
    }

    return Triple(
        Math.toDegrees(pitch.toDouble()).toFloat(),
        Math.toDegrees(yaw.toDouble()).toFloat(),
        Math.toDegrees(roll.toDouble()).toFloat(),
    )
}

fun quatMultiply(a: Quat, b: Quat): Quat = Quat(
    x = a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
    y = a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
    z = a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
    w = a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z,
)

fun quatNormalize(q: Quat): Quat {
    val len = sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w)
    if (len < 1e-6f) return QUAT_IDENTITY
    val inv = 1f / len
    return Quat(q.x * inv, q.y * inv, q.z * inv, q.w * inv)
}

fun quatSlerp(a: Quat, b: Quat, t: Float): Quat {
    var dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
    val nb = if (dot < 0f) { dot = -dot; Quat(-b.x, -b.y, -b.z, -b.w) } else b
    if (dot > 0.9995f) return quatNormalize(Quat(
        a.x + (nb.x - a.x) * t,
        a.y + (nb.y - a.y) * t,
        a.z + (nb.z - a.z) * t,
        a.w + (nb.w - a.w) * t,
    ))
    val theta = acos(dot.coerceIn(-1f, 1f))
    val sinTheta = sin(theta)
    val wa = sin((1f - t) * theta) / sinTheta
    val wb = sin(t * theta) / sinTheta
    return Quat(
        wa * a.x + wb * nb.x,
        wa * a.y + wb * nb.y,
        wa * a.z + wb * nb.z,
        wa * a.w + wb * nb.w,
    )
}

fun quatRotateVec(q: Quat, v: Vec): Vec {
    val qx = q.x.toDouble(); val qy = q.y.toDouble(); val qz = q.z.toDouble(); val qw = q.w.toDouble()
    val vx = v.x(); val vy = v.y(); val vz = v.z()
    val tx = 2.0 * (qy * vz - qz * vy)
    val ty = 2.0 * (qz * vx - qx * vz)
    val tz = 2.0 * (qx * vy - qy * vx)
    return Vec(
        vx + qw * tx + qy * tz - qz * ty,
        vy + qw * ty + qz * tx - qx * tz,
        vz + qw * tz + qx * ty - qy * tx,
    )
}

fun quatInverse(q: Quat): Quat {
    val lenSq = q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w
    if (lenSq < 1e-12f) return QUAT_IDENTITY
    val inv = 1f / lenSq
    return Quat(-q.x * inv, -q.y * inv, -q.z * inv, q.w * inv)
}

fun clamp(value: Float, min: Float, max: Float): Float =
    if (value < min) min else if (value > max) max else value

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

fun lerpVec(a: Vec, b: Vec, t: Double): Vec = Vec(
    a.x() + (b.x() - a.x()) * t,
    a.y() + (b.y() - a.y()) * t,
    a.z() + (b.z() - a.z()) * t,
)

fun wrapDegrees(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

fun colorArgb(a: Int, r: Int, g: Int, b: Int): Int =
    (a shl 24) or (r shl 16) or (g shl 8) or b

data class OrientedBoundingBox(
    val center: Vec,
    val halfExtents: Vec,
    val rotation: Quat,
) {
    fun containsPoint(point: Vec): Boolean {
        val local = quatRotateVec(quatInverse(rotation), point.sub(center))
        return abs(local.x()) <= halfExtents.x() &&
                abs(local.y()) <= halfExtents.y() &&
                abs(local.z()) <= halfExtents.z()
    }

    fun intersects(other: OrientedBoundingBox): Boolean {
        val t = other.center.sub(center)
        val axesA = arrayOf(
            quatRotateVec(rotation, Vec(1.0, 0.0, 0.0)),
            quatRotateVec(rotation, Vec(0.0, 1.0, 0.0)),
            quatRotateVec(rotation, Vec(0.0, 0.0, 1.0)),
        )
        val axesB = arrayOf(
            quatRotateVec(other.rotation, Vec(1.0, 0.0, 0.0)),
            quatRotateVec(other.rotation, Vec(0.0, 1.0, 0.0)),
            quatRotateVec(other.rotation, Vec(0.0, 0.0, 1.0)),
        )
        val heA = doubleArrayOf(halfExtents.x(), halfExtents.y(), halfExtents.z())
        val heB = doubleArrayOf(other.halfExtents.x(), other.halfExtents.y(), other.halfExtents.z())

        fun separatingAxis(axis: Vec): Boolean {
            if (axis.lengthSquared() < 1e-12) return false
            val projT = abs(dot(t, axis))
            var projA = 0.0
            for (i in 0..2) projA += heA[i] * abs(dot(axesA[i], axis))
            var projB = 0.0
            for (i in 0..2) projB += heB[i] * abs(dot(axesB[i], axis))
            return projT > projA + projB
        }

        for (a in axesA) if (separatingAxis(a)) return false
        for (b in axesB) if (separatingAxis(b)) return false
        for (a in axesA) for (b in axesB) {
            val cross = a.cross(b)
            if (separatingAxis(cross)) return false
        }
        return true
    }

    fun rayTrace(origin: Vec, direction: Vec, maxDistance: Double): Double? {
        val invRot = quatInverse(rotation)
        val localOrigin = quatRotateVec(invRot, origin.sub(center))
        val localDir = quatRotateVec(invRot, direction)

        var tMin = 0.0
        var tMax = maxDistance
        val he = doubleArrayOf(halfExtents.x(), halfExtents.y(), halfExtents.z())
        val o = doubleArrayOf(localOrigin.x(), localOrigin.y(), localOrigin.z())
        val d = doubleArrayOf(localDir.x(), localDir.y(), localDir.z())

        for (i in 0..2) {
            if (abs(d[i]) < 1e-12) {
                if (o[i] < -he[i] || o[i] > he[i]) return null
            } else {
                val inv = 1.0 / d[i]
                var t1 = (-he[i] - o[i]) * inv
                var t2 = (he[i] - o[i]) * inv
                if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
                tMin = maxOf(tMin, t1)
                tMax = minOf(tMax, t2)
                if (tMin > tMax) return null
            }
        }
        return tMin
    }
}

private fun dot(a: Vec, b: Vec): Double = a.x() * b.x() + a.y() * b.y() + a.z() * b.z()

private fun Vec.cross(other: Vec): Vec = Vec(
    y() * other.z() - z() * other.y(),
    z() * other.x() - x() * other.z(),
    x() * other.y() - y() * other.x(),
)
