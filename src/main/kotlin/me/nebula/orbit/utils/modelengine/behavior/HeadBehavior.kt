package me.nebula.orbit.utils.modelengine.behavior

import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.math.*
import me.nebula.orbit.utils.modelengine.model.ModeledEntity

class HeadBehavior(
    override val bone: ModelBone,
    private val smoothFactor: Float = 0.3f,
    private val maxPitch: Float = 45f,
    private val maxYaw: Float = 70f,
) : BoneBehavior {

    private var currentYaw: Float = 0f
    private var currentPitch: Float = 0f

    override fun tick(modeledEntity: ModeledEntity) {
        val entityYaw = modeledEntity.owner.position.yaw()
        val targetYaw = wrapDegrees(modeledEntity.headYaw - entityYaw).coerceIn(-maxYaw, maxYaw)
        val targetPitch = modeledEntity.headPitch.coerceIn(-maxPitch, maxPitch)

        currentYaw = lerp(currentYaw, targetYaw, smoothFactor)
        currentPitch = lerp(currentPitch, targetPitch, smoothFactor)

        val headRotation = eulerToQuat(currentPitch, currentYaw, 0f)
        bone.localRotation = quatNormalize(quatMultiply(bone.blueprint.rotation, headRotation))
    }
}
