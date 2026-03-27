package me.nebula.orbit.utils.countdown

import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import java.time.Duration as JavaDuration
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class MilestoneAction(
    val handler: () -> Unit,
    val sound: SoundEvent? = null,
)

class Countdown(
    private val duration: Duration,
    private val onTick: (remaining: Duration) -> Unit,
    private val onComplete: () -> Unit,
    private val tickInterval: Duration = 1.seconds,
    private val milestones: Map<Long, MilestoneAction> = emptyMap(),
) {
    private var task: Task? = null
    private var startTime: Long = 0
    private val durationMs = duration.inWholeMilliseconds
    private val intervalMs = tickInterval.inWholeMilliseconds
    private val firedMilestones = mutableSetOf<Long>()
    private var pausedRemainingMs: Long? = null
    private var cancelled = false

    val isRunning: Boolean get() = task != null

    val isPaused: Boolean get() = pausedRemainingMs != null

    val remaining: Duration
        get() {
            pausedRemainingMs?.let { return it.milliseconds }
            if (!isRunning) return Duration.ZERO
            val elapsed = System.currentTimeMillis() - startTime
            val rem = durationMs - elapsed
            return if (rem > 0) rem.milliseconds else Duration.ZERO
        }

    fun start() {
        require(!isRunning) { "Countdown already running" }
        cancelled = false
        firedMilestones.clear()
        startTime = System.currentTimeMillis()
        pausedRemainingMs = null
        onTick(duration)
        checkMilestones(durationMs, durationMs)
        task = repeat(JavaDuration.ofMillis(intervalMs)) {
            val elapsed = System.currentTimeMillis() - startTime
            val rem = durationMs - elapsed
            if (rem <= 0) {
                stop()
                if (!cancelled) onComplete()
            } else {
                onTick(rem.milliseconds)
                checkMilestones(rem, rem + intervalMs)
            }
        }
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    fun cancel() {
        cancelled = true
        stop()
        pausedRemainingMs = null
    }

    fun pause() {
        require(isRunning) { "Countdown is not running" }
        val elapsed = System.currentTimeMillis() - startTime
        val rem = (durationMs - elapsed).coerceAtLeast(0)
        pausedRemainingMs = rem
        stop()
    }

    fun resume() {
        val savedRemaining = requireNotNull(pausedRemainingMs) { "Countdown is not paused" }
        require(!isRunning) { "Countdown already running" }
        cancelled = false
        pausedRemainingMs = null
        val newDurationMs = savedRemaining
        startTime = System.currentTimeMillis() - (durationMs - newDurationMs)
        onTick(savedRemaining.milliseconds)
        task = repeat(JavaDuration.ofMillis(intervalMs)) {
            val elapsed = System.currentTimeMillis() - startTime
            val rem = durationMs - elapsed
            if (rem <= 0) {
                stop()
                if (!cancelled) onComplete()
            } else {
                onTick(rem.milliseconds)
                checkMilestones(rem, rem + intervalMs)
            }
        }
    }

    fun restart() {
        stop()
        pausedRemainingMs = null
        cancelled = false
        start()
    }

    private fun checkMilestones(currentRemainingMs: Long, previousRemainingMs: Long) {
        for ((thresholdMs, action) in milestones) {
            if (thresholdMs in currentRemainingMs..< previousRemainingMs && thresholdMs !in firedMilestones) {
                firedMilestones += thresholdMs
                action.handler()
            }
        }
    }
}

class CountdownBuilder @PublishedApi internal constructor(private val duration: Duration) {

    @PublishedApi internal var tickHandler: (Duration) -> Unit = {}
    @PublishedApi internal var completeHandler: () -> Unit = {}
    @PublishedApi internal var interval: Duration = 1.seconds
    @PublishedApi internal val milestones = mutableMapOf<Long, MilestoneAction>()

    fun onTick(handler: (remaining: Duration) -> Unit) { tickHandler = handler }
    fun onComplete(handler: () -> Unit) { completeHandler = handler }
    fun interval(duration: Duration) { interval = duration }

    fun milestone(remaining: Duration, handler: () -> Unit) {
        milestones[remaining.inWholeMilliseconds] = MilestoneAction(handler)
    }

    fun milestone(remaining: Duration, sound: SoundEvent, handler: () -> Unit) {
        milestones[remaining.inWholeMilliseconds] = MilestoneAction(handler, sound)
    }

    @PublishedApi internal fun build(): Countdown = Countdown(
        duration = duration,
        onTick = tickHandler,
        onComplete = completeHandler,
        tickInterval = interval,
        milestones = milestones.toMap(),
    )
}

inline fun countdown(duration: Duration, block: CountdownBuilder.() -> Unit): Countdown =
    CountdownBuilder(duration).apply(block).build()
