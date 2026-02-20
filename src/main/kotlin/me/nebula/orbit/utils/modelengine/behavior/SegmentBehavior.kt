package me.nebula.orbit.utils.modelengine.behavior

import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.math.*
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.coordinate.Vec
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

class SegmentBehavior(
    override val bone: ModelBone,
    private val angleLimit: Float = 45f,
    private val rollLock: Boolean = true,
) : BoneBehavior {

    override fun tick(modeledEntity: ModeledEntity) {
        val parent = bone.parent ?: return
        val parentPos = parent.globalTransform.position
        val bonePos = bone.globalTransform.position
        val direction = bonePos.sub(parentPos)
        val len = direction.length()
        if (len < 1e-6) return

        val dir = direction.normalize()
        val targetYaw = Math.toDegrees(atan2(-dir.x(), dir.z())).toFloat()
        val horizontal = sqrt(dir.x() * dir.x() + dir.z() * dir.z())
        val targetPitch = Math.toDegrees(-asin(dir.y().coerceIn(-1.0, 1.0))).toFloat()

        val targetRotation = if (rollLock) {
            eulerToQuat(targetPitch, targetYaw, 0f)
        } else {
            eulerToQuat(targetPitch, targetYaw, quatToEuler(bone.localRotation).third)
        }

        val parentInv = quatInverse(parent.globalTransform.leftRotation)
        val localTarget = quatNormalize(quatMultiply(parentInv, targetRotation))

        val dot = (bone.blueprint.rotation.x * localTarget.x +
                bone.blueprint.rotation.y * localTarget.y +
                bone.blueprint.rotation.z * localTarget.z +
                bone.blueprint.rotation.w * localTarget.w)
            .coerceIn(-1f, 1f)
        val angleDeg = Math.toDegrees(kotlin.math.acos(dot).toDouble()).toFloat() * 2f

        bone.localRotation = if (angleDeg > angleLimit) {
            val t = angleLimit / angleDeg
            quatSlerp(bone.blueprint.rotation, localTarget, t)
        } else {
            localTarget
        }
    }
}
