package me.nebula.orbit.utils.entitybuilder

import kotlin.math.atan2
import kotlin.math.sqrt

interface EntityController {
    fun control(entity: SmartEntity)
}

class WalkController : EntityController {
    private var lastTargetHash: Long = 0

    override fun control(entity: SmartEntity) {
        val target = entity.memory.get(MemoryKeys.MOVE_TARGET) ?: run {
            if (lastTargetHash != 0L) {
                entity.navigator.setPathTo(null)
                lastTargetHash = 0
            }
            return
        }
        val hash = target.blockX().toLong() * 31 + target.blockY().toLong() * 7 + target.blockZ().toLong()
        if (hash != lastTargetHash) {
            lastTargetHash = hash
            entity.navigator.setPathTo(target)
        }
    }
}

class LookController : EntityController {
    override fun control(entity: SmartEntity) {
        val target = entity.memory.get(MemoryKeys.LOOK_TARGET) ?: return
        val pos = entity.position
        val dx = target.x() - pos.x()
        val dy = target.y() - pos.y()
        val dz = target.z() - pos.z()
        val horizontalDist = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-atan2(dy, horizontalDist)).toFloat()
        entity.setView(yaw, pitch)
    }
}
