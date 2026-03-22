package me.nebula.orbit.utils.entitybuilder

import kotlin.random.Random

class BehaviorGroup(
    val sensors: List<Sensor>,
    val coreBehaviors: List<Behavior>,
    val behaviors: List<Behavior>,
    val controllers: List<EntityController>,
) {
    private val sensorCounters = IntArray(sensors.size)
    private val coreCounters = IntArray(coreBehaviors.size)
    private val behaviorCounters = IntArray(behaviors.size)

    private val runningCore = LinkedHashSet<Behavior>()
    private val runningBehaviors = LinkedHashSet<Behavior>()

    fun tick(entity: SmartEntity) {
        tickSensors(entity)
        tickCoreBehaviors(entity)
        tickNormalBehaviors(entity)
        executeRunning(entity)
        tickControllers(entity)
    }

    private fun tickSensors(entity: SmartEntity) {
        sensors.forEachIndexed { i, sensor ->
            if (++sensorCounters[i] >= sensor.period) {
                sensorCounters[i] = 0
                sensor.sense(entity)
            }
        }
    }

    private fun tickCoreBehaviors(entity: SmartEntity) {
        coreBehaviors.forEachIndexed { i, behavior ->
            if (++coreCounters[i] < behavior.period) return@forEachIndexed
            coreCounters[i] = 0
            if (behavior.state == BehaviorState.STOPPED && behavior.evaluator.evaluate(entity)) {
                behavior.state = BehaviorState.ACTIVE
                runningCore.add(behavior)
                behavior.executor.onStart(entity)
            }
        }
    }

    private fun tickNormalBehaviors(entity: SmartEntity) {
        val candidates = mutableListOf<Behavior>()
        behaviors.forEachIndexed { i, behavior ->
            if (behavior.state == BehaviorState.ACTIVE) return@forEachIndexed
            if (++behaviorCounters[i] < behavior.period) return@forEachIndexed
            behaviorCounters[i] = 0
            if (behavior.evaluator.evaluate(entity)) candidates.add(behavior)
        }
        if (candidates.isEmpty()) return

        val highestPriority = candidates.maxOf { it.priority }
        val topCandidates = candidates.filter { it.priority == highestPriority }
        val currentMaxPriority = runningBehaviors.maxOfOrNull { it.priority } ?: -1

        if (highestPriority > currentMaxPriority) {
            interruptRunning(entity)
            startBehavior(entity, selectWeighted(topCandidates))
        } else if (highestPriority == currentMaxPriority) {
            startBehavior(entity, selectWeighted(topCandidates))
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
                behavior.executor.onStop(entity)
                behavior.state = BehaviorState.STOPPED
                iterator.remove()
            }
        }
    }

    private fun interruptRunning(entity: SmartEntity) {
        for (behavior in runningBehaviors) {
            behavior.executor.onInterrupt(entity)
            behavior.state = BehaviorState.STOPPED
        }
        runningBehaviors.clear()
    }

    private fun startBehavior(entity: SmartEntity, behavior: Behavior) {
        behavior.state = BehaviorState.ACTIVE
        runningBehaviors.add(behavior)
        behavior.executor.onStart(entity)
    }

    private fun selectWeighted(candidates: List<Behavior>): Behavior {
        if (candidates.size == 1) return candidates.first()
        val totalWeight = candidates.sumOf { it.weight }
        var roll = Random.nextInt(totalWeight)
        for (candidate in candidates) {
            roll -= candidate.weight
            if (roll < 0) return candidate
        }
        return candidates.last()
    }

    private fun tickControllers(entity: SmartEntity) {
        controllers.forEach { it.control(entity) }
    }

    fun stopAll(entity: SmartEntity) {
        interruptRunning(entity)
        for (behavior in runningCore) {
            behavior.executor.onInterrupt(entity)
            behavior.state = BehaviorState.STOPPED
        }
        runningCore.clear()
    }
}
