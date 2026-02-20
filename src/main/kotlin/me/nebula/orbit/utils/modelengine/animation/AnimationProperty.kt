package me.nebula.orbit.utils.modelengine.animation

import me.nebula.orbit.utils.modelengine.math.Quat
import me.nebula.orbit.utils.modelengine.math.QUAT_IDENTITY
import me.nebula.orbit.utils.modelengine.math.eulerToQuat
import me.nebula.orbit.utils.modelengine.math.quatSlerp
import net.minestom.server.coordinate.Vec

data class AnimationProperty(
    val position: Vec = Vec.ZERO,
    val rotation: Quat = QUAT_IDENTITY,
    val scale: Vec = Vec(1.0, 1.0, 1.0),
) {
    companion object {
        val IDENTITY = AnimationProperty()

        fun blend(a: AnimationProperty, b: AnimationProperty, weight: Float): AnimationProperty {
            val t = weight.coerceIn(0f, 1f)
            return AnimationProperty(
                position = Vec(
                    a.position.x() + (b.position.x() - a.position.x()) * t,
                    a.position.y() + (b.position.y() - a.position.y()) * t,
                    a.position.z() + (b.position.z() - a.position.z()) * t,
                ),
                rotation = quatSlerp(a.rotation, b.rotation, t),
                scale = Vec(
                    a.scale.x() + (b.scale.x() - a.scale.x()) * t,
                    a.scale.y() + (b.scale.y() - a.scale.y()) * t,
                    a.scale.z() + (b.scale.z() - a.scale.z()) * t,
                ),
            )
        }

        fun fromKeyframes(
            posInterp: KeyframeInterpolator,
            rotInterp: KeyframeInterpolator,
            scaleInterp: KeyframeInterpolator,
            time: Float,
        ): AnimationProperty {
            val pos = posInterp.evaluate(time) ?: Vec.ZERO
            val rotVec = rotInterp.evaluate(time)
            val rot = if (rotVec != null) eulerToQuat(rotVec.x().toFloat(), rotVec.y().toFloat(), rotVec.z().toFloat()) else QUAT_IDENTITY
            val scale = scaleInterp.evaluate(time) ?: Vec(1.0, 1.0, 1.0)
            return AnimationProperty(pos, rot, scale)
        }
    }
}
