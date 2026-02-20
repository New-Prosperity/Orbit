package me.nebula.orbit.utils.modelengine.animation

import me.nebula.orbit.utils.modelengine.model.ActiveModel
import java.util.TreeMap

class StateMachineHandler : AnimationHandler {

    private val layers = TreeMap<Int, AnimationStateMachine>()

    fun addLayer(priority: Int, stateMachine: AnimationStateMachine) {
        layers[priority] = stateMachine
    }

    fun removeLayer(priority: Int) {
        layers.remove(priority)
    }

    fun layer(priority: Int): AnimationStateMachine? = layers[priority]

    override fun play(animationName: String, lerpIn: Float, lerpOut: Float, speed: Float) {}

    override fun stop(animationName: String) {}

    override fun stopAll() {
        layers.clear()
    }

    override fun isPlaying(animationName: String): Boolean =
        layers.values.any { it.isPlayingAnimation(animationName) }

    override fun tick(model: ActiveModel, deltaSeconds: Float) {
        model.bones.values.forEach { it.resetAnimation() }
        layers.values.forEach { it.tick(model, deltaSeconds) }
    }
}
