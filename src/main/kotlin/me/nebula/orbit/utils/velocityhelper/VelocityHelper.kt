package me.nebula.orbit.utils.velocityhelper

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TICKS_PER_SECOND = 20.0

fun Player.launchUp(power: Double) {
    velocity = Vec(velocity.x(), power * TICKS_PER_SECOND, velocity.z())
}

fun Player.launchForward(power: Double) {
    val yawRad = Math.toRadians(position.yaw().toDouble())
    val pitchRad = Math.toRadians(position.pitch().toDouble())
    val xComponent = -sin(yawRad) * cos(pitchRad)
    val yComponent = -sin(pitchRad)
    val zComponent = cos(yawRad) * cos(pitchRad)
    velocity = Vec(
        xComponent * power * TICKS_PER_SECOND,
        yComponent * power * TICKS_PER_SECOND,
        zComponent * power * TICKS_PER_SECOND,
    )
}

fun Player.launchToward(target: Point, power: Double) {
    val dx = target.x() - position.x()
    val dy = target.y() - position.y()
    val dz = target.z() - position.z()
    val dist = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001)
    velocity = Vec(
        (dx / dist) * power * TICKS_PER_SECOND,
        (dy / dist) * power * TICKS_PER_SECOND,
        (dz / dist) * power * TICKS_PER_SECOND,
    )
}

fun Player.knockbackFrom(source: Point, power: Double) {
    val dx = position.x() - source.x()
    val dz = position.z() - source.z()
    val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)
    velocity = Vec(
        (dx / dist) * power * TICKS_PER_SECOND,
        power * TICKS_PER_SECOND * 0.5,
        (dz / dist) * power * TICKS_PER_SECOND,
    )
}

fun Entity.freeze() {
    velocity = Vec.ZERO
}

fun calculateParabolicVelocity(from: Point, to: Point, heightFactor: Double): Vec {
    val dx = to.x() - from.x()
    val dy = to.y() - from.y()
    val dz = to.z() - from.z()
    val horizontalDist = sqrt(dx * dx + dz * dz)
    val gravity = 0.08
    val peakHeight = maxOf(dy, 0.0) + heightFactor
    val vy = sqrt(2.0 * gravity * peakHeight) * TICKS_PER_SECOND
    val timeToApex = vy / (gravity * TICKS_PER_SECOND)
    val fallHeight = peakHeight - dy
    val timeToDescent = if (fallHeight > 0) sqrt(2.0 * fallHeight / gravity) else 0.0
    val totalTime = (timeToApex + timeToDescent).coerceAtLeast(1.0)
    val vx = (dx / totalTime) * TICKS_PER_SECOND
    val vz = (dz / totalTime) * TICKS_PER_SECOND
    return Vec(vx, vy, vz)
}
