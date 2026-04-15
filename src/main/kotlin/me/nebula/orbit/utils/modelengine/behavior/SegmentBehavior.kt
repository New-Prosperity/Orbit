package me.nebula.orbit.utils.modelengine.behavior

import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.math.eulerToQuat
import me.nebula.orbit.utils.modelengine.math.quatInverse
import me.nebula.orbit.utils.modelengine.math.quatMultiply
import me.nebula.orbit.utils.modelengine.math.quatNormalize
import me.nebula.orbit.utils.modelengine.math.quatSlerp
import me.nebula.orbit.utils.modelengine.math.quatToEuler
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2

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

        val invLen = 1.0 / len
        val dir = direction.mul(invLen)
        val targetYaw = Math.toDegrees(atan2(-dir.x(), dir.z())).toFloat()
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
        val angleDeg = Math.toDegrees(acos(dot).toDouble()).toFloat() * 2f

        bone.localRotation = if (angleDeg > angleLimit) {
            val t = angleLimit / angleDeg
            quatSlerp(bone.blueprint.rotation, localTarget, t)
        } else {
            localTarget
        }
    }
}
