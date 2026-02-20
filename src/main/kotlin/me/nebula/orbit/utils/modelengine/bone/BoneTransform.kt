package me.nebula.orbit.utils.modelengine.bone

import me.nebula.orbit.utils.modelengine.math.*
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.math.cos
import kotlin.math.sin

data class BoneTransform(
    val position: Vec = Vec.ZERO,
    val leftRotation: Quat = QUAT_IDENTITY,
    val rightRotation: Quat = QUAT_IDENTITY,
    val scale: Vec = Vec(1.0, 1.0, 1.0),
) {
    fun combine(parent: BoneTransform): BoneTransform {
        val combinedScale = Vec(
            parent.scale.x() * scale.x(),
            parent.scale.y() * scale.y(),
            parent.scale.z() * scale.z(),
        )
        val scaledPos = Vec(
            position.x() * parent.scale.x(),
            position.y() * parent.scale.y(),
            position.z() * parent.scale.z(),
        )
        val rotatedPos = quatRotateVec(parent.leftRotation, scaledPos)
        val combinedPos = parent.position.add(rotatedPos)
        val combinedLeftRot = quatNormalize(quatMultiply(parent.leftRotation, leftRotation))
        val combinedRightRot = quatNormalize(quatMultiply(parent.rightRotation, rightRotation))

        return BoneTransform(
            position = combinedPos,
            leftRotation = combinedLeftRot,
            rightRotation = combinedRightRot,
            scale = combinedScale,
        )
    }

    fun toWorldPosition(modelPosition: Pos): Vec {
        val yawRad = Math.toRadians(-modelPosition.yaw().toDouble())
        val cosYaw = cos(yawRad)
        val sinYaw = sin(yawRad)
        val rx = position.x() * cosYaw - position.z() * sinYaw
        val rz = position.x() * sinYaw + position.z() * cosYaw
        return Vec(
            modelPosition.x() + rx,
            modelPosition.y() + position.y(),
            modelPosition.z() + rz,
        )
    }

    fun toWorldRotation(modelYaw: Float): Quat {
        val yawQuat = eulerToQuat(0f, -modelYaw, 0f)
        return quatNormalize(quatMultiply(yawQuat, leftRotation))
    }

    companion object {
        val IDENTITY = BoneTransform()

        fun lerp(a: BoneTransform, b: BoneTransform, t: Float): BoneTransform = BoneTransform(
            position = lerpVec(a.position, b.position, t.toDouble()),
            leftRotation = quatSlerp(a.leftRotation, b.leftRotation, t),
            rightRotation = quatSlerp(a.rightRotation, b.rightRotation, t),
            scale = lerpVec(a.scale, b.scale, t.toDouble()),
        )
    }
}
