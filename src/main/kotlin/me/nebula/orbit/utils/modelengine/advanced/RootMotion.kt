package me.nebula.orbit.utils.modelengine.advanced

import me.nebula.orbit.utils.modelengine.math.quatRotateVec
import me.nebula.orbit.utils.modelengine.math.eulerToQuat
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec

class RootMotion(
    private val rootBoneName: String,
    private val applyX: Boolean = true,
    private val applyY: Boolean = false,
    private val applyZ: Boolean = true,
) {
    private var lastRootPosition: Vec = Vec.ZERO
    private var initialized: Boolean = false

    fun tick(modeledEntity: ModeledEntity) {
        val entity = modeledEntity.entityOrNull ?: return
        val model = modeledEntity.models.values.firstOrNull() ?: return
        val rootBone = model.bones[rootBoneName] ?: return
        val currentPos = rootBone.animatedPosition

        if (!initialized) {
            lastRootPosition = currentPos
            initialized = true
            return
        }

        val delta = currentPos.sub(lastRootPosition)
        lastRootPosition = currentPos

        val entityYaw = entity.position.yaw()
        val yawQuat = eulerToQuat(0f, -entityYaw, 0f)
        val worldDelta = quatRotateVec(yawQuat, delta)

        val dx = if (applyX) worldDelta.x() else 0.0
        val dy = if (applyY) worldDelta.y() else 0.0
        val dz = if (applyZ) worldDelta.z() else 0.0

        if (kotlin.math.abs(dx) > 1e-6 || kotlin.math.abs(dy) > 1e-6 || kotlin.math.abs(dz) > 1e-6) {
            val pos = entity.position
            entity.teleport(Pos(pos.x() + dx, pos.y() + dy, pos.z() + dz, pos.yaw(), pos.pitch()))
        }

        rootBone.animatedPosition = Vec.ZERO
    }

    fun reset() {
        initialized = false
        lastRootPosition = Vec.ZERO
    }
}
