package me.nebula.orbit.utils.modelengine.animation

import me.nebula.orbit.utils.modelengine.model.ActiveModel

sealed interface AnimationHandler {
    fun play(animationName: String, lerpIn: Float = 0f, lerpOut: Float = 0f, speed: Float = 1f)
    fun stop(animationName: String)
    fun stopAll()
    fun isPlaying(animationName: String): Boolean
    fun tick(model: ActiveModel, deltaSeconds: Float)
}
