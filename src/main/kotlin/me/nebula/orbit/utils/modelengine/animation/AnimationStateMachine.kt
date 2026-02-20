package me.nebula.orbit.utils.modelengine.animation

import me.nebula.orbit.utils.modelengine.model.ActiveModel

class AnimationState(
    val name: String,
    val animationName: String,
    val speed: Float = 1f,
    val lerpIn: Float = 0f,
    val lerpOut: Float = 0f,
)

class AnimationTransition(
    val from: String,
    val to: String,
    val condition: (ActiveModel) -> Boolean,
    val priority: Int = 0,
)

class AnimationStateMachine(
    private val states: Map<String, AnimationState>,
    private val transitions: List<AnimationTransition>,
    initialStateName: String,
) {
    var currentStateName: String = initialStateName
        private set

    private val handler = PriorityHandler()

    fun isPlayingAnimation(animationName: String): Boolean = handler.isPlaying(animationName)

    fun tick(model: ActiveModel, deltaSeconds: Float) {
        handler.boundModel = model

        val applicable = transitions
            .filter { it.from == currentStateName && it.condition(model) }
            .maxByOrNull { it.priority }

        if (applicable != null) {
            val currentState = states[currentStateName]
            val nextState = states[applicable.to]
            if (nextState != null && nextState.name != currentStateName) {
                currentState?.let { handler.stop(it.animationName) }
                handler.play(nextState.animationName, nextState.lerpIn, nextState.lerpOut, nextState.speed)
                currentStateName = nextState.name
            }
        }

        val activeState = states[currentStateName]
        if (activeState != null && !handler.isPlaying(activeState.animationName)) {
            handler.play(activeState.animationName, activeState.lerpIn, activeState.lerpOut, activeState.speed)
        }

        handler.tick(model, deltaSeconds)
    }

    fun forceState(stateName: String) {
        val state = states[stateName] ?: return
        val current = states[currentStateName]
        current?.let { handler.stop(it.animationName) }
        handler.play(state.animationName, state.lerpIn, state.lerpOut, state.speed)
        currentStateName = stateName
    }
}

class AnimationStateMachineBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val states = mutableMapOf<String, AnimationState>()
    @PublishedApi internal val transitions = mutableListOf<AnimationTransition>()
    @PublishedApi internal var initialState: String? = null

    fun state(name: String, animationName: String, speed: Float = 1f, lerpIn: Float = 0f, lerpOut: Float = 0f) {
        states[name] = AnimationState(name, animationName, speed, lerpIn, lerpOut)
        if (initialState == null) initialState = name
    }

    fun transition(from: String, to: String, priority: Int = 0, condition: (ActiveModel) -> Boolean) {
        transitions += AnimationTransition(from, to, condition, priority)
    }

    fun initial(stateName: String) { initialState = stateName }

    @PublishedApi internal fun build(): AnimationStateMachine {
        val init = requireNotNull(initialState) { "AnimationStateMachine requires at least one state" }
        require(init in states) { "Initial state '$init' not found in states" }
        return AnimationStateMachine(states.toMap(), transitions.toList(), init)
    }
}

inline fun animationStateMachine(block: AnimationStateMachineBuilder.() -> Unit): AnimationStateMachine =
    AnimationStateMachineBuilder().apply(block).build()
