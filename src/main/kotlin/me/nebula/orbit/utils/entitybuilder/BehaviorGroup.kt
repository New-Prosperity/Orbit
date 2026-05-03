package me.nebula.orbit.utils.entitybuilder

import net.kyori.adventure.sound.Sound
import net.minestom.server.sound.SoundEvent

class BehaviorGroup(
    val sensors: List<Sensor>,
    val coreBehaviors: List<Behavior>,
    val behaviors: List<Behavior>,
    val controllers: List<EntityController>,
    val runningBonus: Float = 50f,
) {
    private val sensorCounters = IntArray(sensors.size)
    private val coreCounters = IntArray(coreBehaviors.size)
    private val behaviorCounters = IntArray(behaviors.size)
    private val behaviorIndex: Map<Behavior, Int> =
        behaviors.withIndex().associate { (i, b) -> b to i }

    private val runningCore = LinkedHashSet<Behavior>()
    private val runningBehaviors = LinkedHashSet<Behavior>()

    private val candidateScratch = ArrayList<Behavior>(behaviors.size)

    private val triggerListeners: Map<TriggerType<*>, List<Behavior>> = run {
        val map = mutableMapOf<TriggerType<*>, MutableList<Behavior>>()
        (coreBehaviors + behaviors).forEach { b ->
            b.wakeTriggers.forEach { t -> map.getOrPut(t) { mutableListOf() }.add(b) }
        }
        map
    }

    val activeCore: List<Behavior> get() = runningCore.toList()
    val active: List<Behavior> get() = runningBehaviors.toList()

    fun tick(entity: SmartEntity) {
        tickSensors(entity)
        tickCoreBehaviors(entity)
        tickNormalBehaviors(entity)
        executeRunning(entity)
        tickControllers(entity)
    }

    private fun tickSensors(entity: SmartEntity) {
        for (i in sensors.indices) {
            val sensor = sensors[i]
            if (++sensorCounters[i] >= sensor.period) {
                sensorCounters[i] = 0
                sensor.sense(entity)
            }
        }
    }

    private fun tickCoreBehaviors(entity: SmartEntity) {
        for (i in coreBehaviors.indices) {
            val behavior = coreBehaviors[i]
            if (++coreCounters[i] < behavior.period) continue
            coreCounters[i] = 0
            if (behavior.state == BehaviorState.STOPPED
                && isPhaseEligible(entity, behavior)
                && behavior.evaluator.evaluate(entity)) {
                behavior.state = BehaviorState.ACTIVE
                runningCore.add(behavior)
                fireStart(entity, behavior)
            }
        }
    }

    private fun tickNormalBehaviors(entity: SmartEntity) {
        val candidates = candidateScratch
        candidates.clear()
        for (i in behaviors.indices) {
            val behavior = behaviors[i]
            if (behavior.state == BehaviorState.ACTIVE) continue
            if (++behaviorCounters[i] < behavior.period) continue
            behaviorCounters[i] = 0
            if (!isPhaseEligible(entity, behavior)) continue
            if (behavior.evaluator.evaluate(entity)) candidates.add(behavior)
        }
        if (candidates.isNotEmpty()) activateBest(entity, candidates)
    }

    private fun isPhaseEligible(entity: SmartEntity, behavior: Behavior): Boolean {
        val filter = behavior.phaseFilter ?: return true
        val phase = entity.memory.get(MemoryKeys.PHASE) ?: 0
        return phase in filter
    }

    fun cullPhaseInvalid(entity: SmartEntity) {
        val coreIter = runningCore.iterator()
        while (coreIter.hasNext()) {
            val b = coreIter.next()
            if (!isPhaseEligible(entity, b)) {
                fireInterrupt(entity, b)
                b.state = BehaviorState.STOPPED
                coreIter.remove()
            }
        }
        val normalIter = runningBehaviors.iterator()
        while (normalIter.hasNext()) {
            val b = normalIter.next()
            if (!isPhaseEligible(entity, b)) {
                fireInterrupt(entity, b)
                b.state = BehaviorState.STOPPED
                normalIter.remove()
            }
        }
    }

    private fun activateBest(entity: SmartEntity, candidates: List<Behavior>) {
        if (candidates.isEmpty()) return
        var best: Behavior? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in candidates.indices) {
            val c = candidates[i]
            val s = c.score(entity)
            if (s > bestScore) {
                bestScore = s
                best = c
            }
        }
        if (best == null) return
        var currentBestScore = Float.NEGATIVE_INFINITY
        for (running in runningBehaviors) {
            val s = running.score(entity) + runningBonus
            if (s > currentBestScore) currentBestScore = s
        }
        if (bestScore > currentBestScore) {
            interruptRunning(entity)
            startBehavior(entity, best)
        }
    }

    fun fire(entity: SmartEntity, trigger: TriggerType<*>) {
        fireWithPayload(entity, trigger, null)
    }

    fun <T : Any> fire(entity: SmartEntity, trigger: TriggerType<T>, payload: T) {
        fireWithPayload(entity, trigger, payload)
    }

    private fun fireWithPayload(entity: SmartEntity, trigger: TriggerType<*>, payload: Any?) {
        val listeners = triggerListeners[trigger] ?: return
        val previousPayload = entity.currentTriggerPayload
        val previousTrigger = entity.currentTrigger
        entity.currentTriggerPayload = payload
        entity.currentTrigger = trigger
        try {
            val coreCandidates = ArrayList<Behavior>(listeners.size)
            val normalCandidates = ArrayList<Behavior>(listeners.size)
            for (i in listeners.indices) {
                val behavior = listeners[i]
                if (behavior.state == BehaviorState.ACTIVE) continue
                if (!isPhaseEligible(entity, behavior)) continue
                if (!behavior.evaluator.evaluate(entity)) continue
                if (behavior.core) coreCandidates.add(behavior) else normalCandidates.add(behavior)
            }
            for (i in coreCandidates.indices) {
                val behavior = coreCandidates[i]
                behavior.state = BehaviorState.ACTIVE
                runningCore.add(behavior)
                fireStart(entity, behavior)
            }
            if (normalCandidates.isNotEmpty()) {
                for (i in normalCandidates.indices) {
                    val b = normalCandidates[i]
                    behaviorIndex[b]?.let { behaviorCounters[it] = 0 }
                }
                activateBest(entity, normalCandidates)
            }
        } finally {
            entity.currentTriggerPayload = previousPayload
            entity.currentTrigger = previousTrigger
        }
    }

    private fun executeRunning(entity: SmartEntity) {
        executeSet(entity, runningCore)
        executeSet(entity, runningBehaviors)
    }

    private fun executeSet(entity: SmartEntity, set: LinkedHashSet<Behavior>) {
        val iterator = set.iterator()
        while (iterator.hasNext()) {
            val behavior = iterator.next()
            if (!behavior.executor.execute(entity)) {
                fireStop(entity, behavior)
                behavior.state = BehaviorState.STOPPED
                iterator.remove()
            }
        }
    }

    private fun interruptRunning(entity: SmartEntity) {
        for (behavior in runningBehaviors) {
            fireInterrupt(entity, behavior)
            behavior.state = BehaviorState.STOPPED
        }
        runningBehaviors.clear()
    }

    private fun startBehavior(entity: SmartEntity, behavior: Behavior) {
        behavior.state = BehaviorState.ACTIVE
        runningBehaviors.add(behavior)
        fireStart(entity, behavior)
    }

    private fun fireStart(entity: SmartEntity, behavior: Behavior) {
        behavior.executor.onStart(entity)
        behavior.playOnStart?.let {
            entity.playAnimation(it, behavior.animationLerpIn, behavior.animationLerpOut, behavior.animationSpeed)
        }
        behavior.soundOnStart?.let { playBehaviorSound(entity, behavior, it) }
    }

    private fun fireStop(entity: SmartEntity, behavior: Behavior) {
        behavior.executor.onStop(entity)
        if (behavior.stopAnimationOnEnd) behavior.playOnStart?.let { entity.stopAnimation(it) }
        behavior.playOnStop?.let {
            entity.playAnimation(it, behavior.animationLerpIn, behavior.animationLerpOut, behavior.animationSpeed)
        }
        behavior.soundOnStop?.let { playBehaviorSound(entity, behavior, it) }
    }

    private fun fireInterrupt(entity: SmartEntity, behavior: Behavior) {
        behavior.executor.onInterrupt(entity)
        if (behavior.stopAnimationOnEnd) behavior.playOnStart?.let { entity.stopAnimation(it) }
        behavior.playOnInterrupt?.let {
            entity.playAnimation(it, behavior.animationLerpIn, behavior.animationLerpOut, behavior.animationSpeed)
        }
        behavior.soundOnInterrupt?.let { playBehaviorSound(entity, behavior, it) }
    }

    private fun playBehaviorSound(entity: SmartEntity, behavior: Behavior, event: SoundEvent) {
        val instance = entity.instance ?: return
        val pos = entity.position
        instance.playSound(
            Sound.sound(event.key(), Sound.Source.HOSTILE, behavior.soundVolume, behavior.soundPitch),
            pos.x(), pos.y(), pos.z(),
        )
    }

    private fun tickControllers(entity: SmartEntity) {
        for (i in controllers.indices) controllers[i].control(entity)
    }

    fun stopAll(entity: SmartEntity) {
        interruptRunning(entity)
        for (behavior in runningCore) {
            fireInterrupt(entity, behavior)
            behavior.state = BehaviorState.STOPPED
        }
        runningCore.clear()
    }
}
