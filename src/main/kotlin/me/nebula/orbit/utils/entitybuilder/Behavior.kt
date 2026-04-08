package me.nebula.orbit.utils.entitybuilder

fun interface BehaviorEvaluator {
    fun evaluate(entity: SmartEntity): Boolean
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
) {
    var state: BehaviorState = BehaviorState.STOPPED
        internal set
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

    fun priority(value: Int) { priority = value }
    fun weight(value: Int) { weight = value }
    fun period(ticks: Int) { period = ticks }
    fun core() { core = true }

    fun evaluateWhen(predicate: (SmartEntity) -> Boolean) {
        evaluator = BehaviorEvaluator { predicate(it) }
    }

    fun execute(block: (SmartEntity) -> Boolean) {
        executor = object : BehaviorExecutor {
            override fun execute(entity: SmartEntity): Boolean = block(entity)
        }
    }

    fun executor(exec: BehaviorExecutor) { executor = exec }

    @PublishedApi internal fun build(): Behavior {
        val exec = requireNotNull(executor) { "Behavior '$id' must have an executor" }
        return Behavior(id, priority, weight, period, core, evaluator, exec)
    }
}
