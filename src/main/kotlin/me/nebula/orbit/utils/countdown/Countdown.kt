package me.nebula.orbit.utils.countdown

import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration

class Countdown(
    private val duration: Duration,
    private val onTick: (remaining: Duration) -> Unit,
    private val onComplete: () -> Unit,
    private val tickInterval: Duration = Duration.ofSeconds(1),
) {
    private var task: Task? = null
    private var startTime: Long = 0
    private val durationMs = duration.toMillis()
    private val intervalMs = tickInterval.toMillis()

    val isRunning: Boolean get() = task != null

    val remaining: Duration
        get() {
            if (!isRunning) return Duration.ZERO
            val elapsed = System.currentTimeMillis() - startTime
            val remaining = durationMs - elapsed
            return if (remaining > 0) Duration.ofMillis(remaining) else Duration.ZERO
        }

    fun start() {
        require(!isRunning) { "Countdown already running" }
        startTime = System.currentTimeMillis()
        onTick(duration)
        task = MinecraftServer.getSchedulerManager()
            .buildTask {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = durationMs - elapsed
                if (remaining <= 0) {
                    stop()
                    onComplete()
                } else {
                    onTick(Duration.ofMillis(remaining))
                }
            }
            .repeat(TaskSchedule.millis(intervalMs))
            .schedule()
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    fun restart() {
        stop()
        start()
    }
}

class CountdownBuilder @PublishedApi internal constructor(private val duration: Duration) {

    @PublishedApi internal var tickHandler: (Duration) -> Unit = {}
    @PublishedApi internal var completeHandler: () -> Unit = {}
    @PublishedApi internal var interval: Duration = Duration.ofSeconds(1)

    fun onTick(handler: (remaining: Duration) -> Unit) { tickHandler = handler }
    fun onComplete(handler: () -> Unit) { completeHandler = handler }
    fun interval(duration: Duration) { interval = duration }

    @PublishedApi internal fun build(): Countdown = Countdown(duration, tickHandler, completeHandler, interval)
}

inline fun countdown(duration: Duration, block: CountdownBuilder.() -> Unit): Countdown =
    CountdownBuilder(duration).apply(block).build()
