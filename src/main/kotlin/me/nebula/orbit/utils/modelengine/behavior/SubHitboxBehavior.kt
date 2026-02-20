package me.nebula.orbit.utils.modelengine.behavior

import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.math.OrientedBoundingBox
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.coordinate.Vec

class SubHitboxBehavior(
    override val bone: ModelBone,
    val halfExtents: Vec = Vec(0.5, 0.5, 0.5),
    val damageMultiplier: Float = 1.0f,
) : BoneBehavior {

    var obb: OrientedBoundingBox = OrientedBoundingBox(Vec.ZERO, halfExtents, me.nebula.orbit.utils.modelengine.math.QUAT_IDENTITY)
        private set

    override fun tick(modeledEntity: ModeledEntity) {
        val transform = bone.globalTransform
        val worldPos = transform.toWorldPosition(modeledEntity.owner.position)
        val worldRot = transform.toWorldRotation(modeledEntity.owner.position.yaw())

        val scaledHalf = Vec(
            halfExtents.x() * transform.scale.x(),
            halfExtents.y() * transform.scale.y(),
            halfExtents.z() * transform.scale.z(),
        )

        obb = OrientedBoundingBox(worldPos, scaledHalf, worldRot)
    }
}
