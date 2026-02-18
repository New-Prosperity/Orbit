package me.nebula.orbit.utils.scheduler

import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration

fun delay(ticks: Int, action: () -> Unit): Task =
    MinecraftServer.getSchedulerManager()
        .buildTask(action)
        .delay(TaskSchedule.tick(ticks))
        .schedule()

fun delay(duration: Duration, action: () -> Unit): Task =
    MinecraftServer.getSchedulerManager()
        .buildTask(action)
        .delay(TaskSchedule.duration(duration))
        .schedule()

fun repeat(intervalTicks: Int, action: () -> Unit): Task =
    MinecraftServer.getSchedulerManager()
        .buildTask(action)
        .repeat(TaskSchedule.tick(intervalTicks))
        .schedule()

fun repeat(interval: Duration, action: () -> Unit): Task =
    MinecraftServer.getSchedulerManager()
        .buildTask(action)
        .repeat(TaskSchedule.duration(interval))
        .schedule()

fun delayedRepeat(delayTicks: Int, intervalTicks: Int, action: () -> Unit): Task =
    MinecraftServer.getSchedulerManager()
        .buildTask(action)
        .delay(TaskSchedule.tick(delayTicks))
        .repeat(TaskSchedule.tick(intervalTicks))
        .schedule()

fun runAsync(action: () -> Unit): Task =
    MinecraftServer.getSchedulerManager()
        .buildTask(action)
        .schedule()

class RepeatBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var intervalTicks = 20
    @PublishedApi internal var delayTicks = 0
    @PublishedApi internal var action: (() -> Unit)? = null
    @PublishedApi internal var times = -1

    fun interval(ticks: Int) { intervalTicks = ticks }
    fun interval(duration: Duration) { intervalTicks = (duration.toMillis() / 50).toInt() }
    fun delay(ticks: Int) { delayTicks = ticks }
    fun delay(duration: Duration) { delayTicks = (duration.toMillis() / 50).toInt() }
    fun times(count: Int) { times = count }
    fun execute(action: () -> Unit) { this.action = action }

    @PublishedApi internal fun build(): Task {
        val exec = requireNotNull(action) { "RepeatBuilder requires an execute block" }
        val taskHolder = arrayOfNulls<Task>(1)
        if (times > 0) {
            var remaining = times
            val task = MinecraftServer.getSchedulerManager()
                .buildTask {
                    exec()
                    remaining--
                    if (remaining <= 0) taskHolder[0]?.cancel()
                }
                .let { if (delayTicks > 0) it.delay(TaskSchedule.tick(delayTicks)) else it }
                .repeat(TaskSchedule.tick(intervalTicks))
                .schedule()
            taskHolder[0] = task
            return task
        }
        return MinecraftServer.getSchedulerManager()
            .buildTask(exec)
            .let { if (delayTicks > 0) it.delay(TaskSchedule.tick(delayTicks)) else it }
            .repeat(TaskSchedule.tick(intervalTicks))
            .schedule()
    }
}

inline fun repeatingTask(block: RepeatBuilder.() -> Unit): Task =
    RepeatBuilder().apply(block).build()
