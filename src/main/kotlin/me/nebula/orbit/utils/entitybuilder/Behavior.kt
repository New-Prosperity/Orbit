package me.nebula.orbit.utils.entitybuilder

import net.minestom.server.sound.SoundEvent
import kotlin.random.Random

fun interface BehaviorEvaluator {
    fun evaluate(entity: SmartEntity): Boolean
}

fun interface BehaviorScorer {
    fun score(entity: SmartEntity): Float
}

interface BehaviorExecutor {
    fun onStart(entity: SmartEntity) {}
    fun execute(entity: SmartEntity): Boolean
    fun onStop(entity: SmartEntity) {}
    fun onInterrupt(entity: SmartEntity) { onStop(entity) }
}

enum class BehaviorState { ACTIVE, STOPPED }

class Behavior(
    val id: String,
    val priority: Int,
    val weight: Int = 1,
    val period: Int = 1,
    val core: Boolean = false,
    val evaluator: BehaviorEvaluator,
    val executor: BehaviorExecutor,
    val scorer: BehaviorScorer? = null,
    val playOnStart: String? = null,
    val playOnStop: String? = null,
    val playOnInterrupt: String? = null,
    val stopAnimationOnEnd: Boolean = true,
    val animationLerpIn: Float = 0.2f,
    val animationLerpOut: Float = 0.2f,
    val animationSpeed: Float = 1f,
    val wakeTriggers: Set<TriggerType<*>> = emptySet(),
    val phaseFilter: Set<Int>? = null,
    val soundOnStart: SoundEvent? = null,
    val soundOnStop: SoundEvent? = null,
    val soundOnInterrupt: SoundEvent? = null,
    val soundVolume: Float = 1f,
    val soundPitch: Float = 1f,
) {
    var state: BehaviorState = BehaviorState.STOPPED
        internal set

    fun score(entity: SmartEntity): Float {
        val custom = scorer
        if (custom != null) return custom.score(entity)
        return priority * 100f + Random.nextFloat() * weight.toFloat()
    }
}

class BehaviorBuilder @PublishedApi internal constructor(
    @PublishedApi internal val id: String,
) {
    @PublishedApi internal var priority: Int = 0
    @PublishedApi internal var weight: Int = 1
    @PublishedApi internal var period: Int = 1
    @PublishedApi internal var core: Boolean = false
    @PublishedApi internal var evaluator: BehaviorEvaluator = BehaviorEvaluator { true }
    @PublishedApi internal var executor: BehaviorExecutor? = null
    @PublishedApi internal var scorer: BehaviorScorer? = null
    @PublishedApi internal var playOnStart: String? = null
    @PublishedApi internal var playOnStop: String? = null
    @PublishedApi internal var playOnInterrupt: String? = null
    @PublishedApi internal var stopAnimationOnEnd: Boolean = true
    @PublishedApi internal var animationLerpIn: Float = 0.2f
    @PublishedApi internal var animationLerpOut: Float = 0.2f
    @PublishedApi internal var animationSpeed: Float = 1f
    @PublishedApi internal val wakeTriggers: MutableSet<TriggerType<*>> = mutableSetOf()
    @PublishedApi internal var phaseFilter: Set<Int>? = null
    @PublishedApi internal var soundOnStart: SoundEvent? = null
    @PublishedApi internal var soundOnStop: SoundEvent? = null
    @PublishedApi internal var soundOnInterrupt: SoundEvent? = null
    @PublishedApi internal var soundVolume: Float = 1f
    @PublishedApi internal var soundPitch: Float = 1f

    fun priority(value: Int) { priority = value }
    fun weight(value: Int) { weight = value }
    fun period(ticks: Int) { period = ticks }
    fun core() { core = true }

    fun playOnStart(animation: String) { playOnStart = animation }
    fun playOnStop(animation: String) { playOnStop = animation }
    fun playOnInterrupt(animation: String) { playOnInterrupt = animation }
    fun keepAnimationOnEnd() { stopAnimationOnEnd = false }
    fun animationLerp(lerpIn: Float, lerpOut: Float = lerpIn) {
        animationLerpIn = lerpIn
        animationLerpOut = lerpOut
    }
    fun animationSpeed(speed: Float) { animationSpeed = speed }

    fun wakeOn(trigger: TriggerType<*>) { wakeTriggers += trigger }
    fun wakeOn(vararg triggers: TriggerType<*>) { wakeTriggers += triggers }

    fun availableInPhases(vararg phases: Int) { phaseFilter = phases.toSet() }
    fun availableInPhase(phase: Int) { phaseFilter = setOf(phase) }

    fun soundOnStart(sound: SoundEvent) { soundOnStart = sound }
    fun soundOnStop(sound: SoundEvent) { soundOnStop = sound }
    fun soundOnInterrupt(sound: SoundEvent) { soundOnInterrupt = sound }
    fun soundVolume(value: Float) { soundVolume = value }
    fun soundPitch(value: Float) { soundPitch = value }

    fun evaluateWhen(predicate: (SmartEntity) -> Boolean) {
        evaluator = BehaviorEvaluator { predicate(it) }
    }

    fun evaluator(eval: BehaviorEvaluator) { evaluator = eval }

    fun execute(block: (SmartEntity) -> Boolean) {
        executor = object : BehaviorExecutor {
            override fun execute(entity: SmartEntity): Boolean = block(entity)
        }
    }

    fun executor(exec: BehaviorExecutor) { executor = exec }

    fun scoreWith(block: (SmartEntity) -> Float) {
        scorer = BehaviorScorer { block(it) }
    }

    fun scorer(s: BehaviorScorer) { scorer = s }

    @PublishedApi internal fun build(): Behavior {
        val exec = requireNotNull(executor) { "Behavior '$id' must have an executor" }
        return Behavior(
            id, priority, weight, period, core, evaluator, exec, scorer,
            playOnStart, playOnStop, playOnInterrupt,
            stopAnimationOnEnd, animationLerpIn, animationLerpOut, animationSpeed,
            wakeTriggers.toSet(),
            phaseFilter,
            soundOnStart, soundOnStop, soundOnInterrupt,
            soundVolume, soundPitch,
        )
    }
}
