package me.nebula.orbit.utils.entitybuilder

import net.minestom.server.coordinate.Point
import kotlin.math.atan2
import kotlin.math.sqrt

interface EntityController {
    fun control(entity: SmartEntity)
}

class WalkController : EntityController {
    private var lastPathTarget: Point? = null

    override fun control(entity: SmartEntity) {
        val target = entity.memory.get(MemoryKeys.MOVE_TARGET) ?: run {
            if (lastPathTarget != null) {
                entity.navigator.setPathTo(null)
                lastPathTarget = null
            }
            return
        }
        val previous = lastPathTarget
        if (previous == null || previous.distanceSquared(target) > REPATH_THRESHOLD_SQ) {
            lastPathTarget = target
            entity.navigator.setPathTo(target)
        }
    }

    companion object {
        private const val REPATH_THRESHOLD_SQ = 2.25
    }
}

class LookController(
    private val maxYawDeltaPerTick: Float = 12f,
    private val maxPitchDeltaPerTick: Float = 8f,
) : EntityController {
    override fun control(entity: SmartEntity) {
        val target = entity.memory.get(MemoryKeys.LOOK_TARGET) ?: run {
            entity.memory.clear(MemoryKeys.LAST_LOOK_YAW)
            entity.memory.clear(MemoryKeys.LAST_LOOK_PITCH)
            return
        }
        val pos = entity.position
        val dx = target.x() - pos.x()
        val dy = target.y() - pos.y()
        val dz = target.z() - pos.z()
        val horizontalDist = sqrt(dx * dx + dz * dz)
        val targetYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val targetPitch = Math.toDegrees(-atan2(dy, horizontalDist)).toFloat()
        val baseYaw = entity.memory.get(MemoryKeys.LAST_LOOK_YAW) ?: pos.yaw()
        val basePitch = entity.memory.get(MemoryKeys.LAST_LOOK_PITCH) ?: pos.pitch()
        val newYaw = approachAngle(baseYaw, targetYaw, maxYawDeltaPerTick)
        val newPitch = approachAngle(basePitch, targetPitch, maxPitchDeltaPerTick)
        entity.setView(newYaw, newPitch)
        entity.memory.set(MemoryKeys.LAST_LOOK_YAW, newYaw)
        entity.memory.set(MemoryKeys.LAST_LOOK_PITCH, newPitch)
    }

    private fun approachAngle(current: Float, target: Float, maxDelta: Float): Float {
        var diff = target - current
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        val clamped = diff.coerceIn(-maxDelta, maxDelta)
        return current + clamped
    }
}
