package me.nebula.orbit.utils.modelengine.animation

import net.minestom.server.coordinate.Vec

data class AnimationProperty(
    val position: Vec = Vec.ZERO,
    val rotationEuler: Vec = Vec.ZERO,
    val scale: Vec = Vec(1.0, 1.0, 1.0),
) {
    companion object {
        val IDENTITY = AnimationProperty()

        fun blend(a: AnimationProperty, b: AnimationProperty, weight: Float): AnimationProperty {
            val t = weight.coerceIn(0f, 1f).toDouble()
            return AnimationProperty(
                position = Vec(
                    a.position.x() + (b.position.x() - a.position.x()) * t,
                    a.position.y() + (b.position.y() - a.position.y()) * t,
                    a.position.z() + (b.position.z() - a.position.z()) * t,
                ),
                rotationEuler = Vec(
                    a.rotationEuler.x() + (b.rotationEuler.x() - a.rotationEuler.x()) * t,
                    a.rotationEuler.y() + (b.rotationEuler.y() - a.rotationEuler.y()) * t,
                    a.rotationEuler.z() + (b.rotationEuler.z() - a.rotationEuler.z()) * t,
                ),
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
            val pos = posInterp.evaluate(time)?.let {
                Vec(it.x() / 16.0, it.y() / 16.0, -(it.z() / 16.0))
            } ?: Vec.ZERO
            val rotEuler = rotInterp.evaluate(time)?.let {
                Vec(it.x(), -it.y(), -it.z())
            } ?: Vec.ZERO
            val scale = scaleInterp.evaluate(time) ?: Vec(1.0, 1.0, 1.0)
            return AnimationProperty(pos, rotEuler, scale)
        }
    }
}
