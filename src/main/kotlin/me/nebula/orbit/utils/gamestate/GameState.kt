package me.nebula.orbit.utils.gamestate

import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.CopyOnWriteArrayList

typealias StateTransitionCallback<S> = (from: S, to: S) -> Unit

class GameStateMachine<S : Enum<S>>(
    initialState: S,
    private val transitions: Map<S, Set<S>>,
    private val onEnter: Map<S, () -> Unit>,
    private val onExit: Map<S, () -> Unit>,
    private val timedTransitions: Map<S, Pair<Int, S>>,
) {

    @Volatile
    var current: S = initialState
        private set

    private val listeners = CopyOnWriteArrayList<StateTransitionCallback<S>>()
    private var timerTask: Task? = null

    @Volatile
    private var ticksInState = 0

    fun transition(to: S): Boolean {
        val allowed = transitions[current] ?: return false
        if (to !in allowed) return false
        val from = current
        onExit[from]?.invoke()
        current = to
        ticksInState = 0
        onEnter[to]?.invoke()
        listeners.forEach { it(from, to) }
        scheduleTimedTransition()
        return true
    }

    fun forceTransition(to: S) {
        val from = current
        onExit[from]?.invoke()
        current = to
        ticksInState = 0
        onEnter[to]?.invoke()
        listeners.forEach { it(from, to) }
        scheduleTimedTransition()
    }

    fun onTransition(callback: StateTransitionCallback<S>) {
        listeners.add(callback)
    }

    fun removeListener(callback: StateTransitionCallback<S>) {
        listeners.remove(callback)
    }

    fun startTicking() {
        timerTask?.cancel()
        timerTask = MinecraftServer.getSchedulerManager()
            .buildTask {
                ticksInState++
            }
            .repeat(TaskSchedule.tick(1))
            .schedule()
        scheduleTimedTransition()
    }

    fun stopTicking() {
        timerTask?.cancel()
        timerTask = null
    }

    fun ticksInCurrentState(): Int = ticksInState

    fun is_(state: S): Boolean = current == state

    fun isAny(vararg states: S): Boolean = current in states

    fun destroy() {
        stopTicking()
        listeners.clear()
    }

    private fun scheduleTimedTransition() {
        val (ticks, target) = timedTransitions[current] ?: return
        timerTask?.cancel()
        timerTask = MinecraftServer.getSchedulerManager()
            .buildTask {
                ticksInState++
                if (ticksInState >= ticks) {
                    transition(target)
                }
            }
            .repeat(TaskSchedule.tick(1))
            .schedule()
    }
}

class GameStateMachineBuilder<S : Enum<S>> @PublishedApi internal constructor(
    private val initialState: S,
) {

    @PublishedApi internal val transitions = mutableMapOf<S, MutableSet<S>>()
    @PublishedApi internal val onEnter = mutableMapOf<S, () -> Unit>()
    @PublishedApi internal val onExit = mutableMapOf<S, () -> Unit>()
    @PublishedApi internal val timedTransitions = mutableMapOf<S, Pair<Int, S>>()

    fun allow(from: S, vararg to: S) {
        transitions.getOrPut(from) { mutableSetOf() }.addAll(to)
    }

    fun onEnter(state: S, action: () -> Unit) {
        onEnter[state] = action
    }

    fun onExit(state: S, action: () -> Unit) {
        onExit[state] = action
    }

    fun timedTransition(from: S, to: S, ticks: Int) {
        transitions.getOrPut(from) { mutableSetOf() }.add(to)
        timedTransitions[from] = ticks to to
    }

    @PublishedApi internal fun build(): GameStateMachine<S> = GameStateMachine(
        initialState = initialState,
        transitions = transitions.mapValues { it.value.toSet() },
        onEnter = onEnter.toMap(),
        onExit = onExit.toMap(),
        timedTransitions = timedTransitions.toMap(),
    )
}

inline fun <reified S : Enum<S>> gameStateMachine(
    initialState: S,
    block: GameStateMachineBuilder<S>.() -> Unit,
): GameStateMachine<S> = GameStateMachineBuilder(initialState).apply(block).build()
