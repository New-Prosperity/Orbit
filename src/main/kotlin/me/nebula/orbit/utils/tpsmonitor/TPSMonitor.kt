package me.nebula.orbit.utils.tpsmonitor

import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

object TPSMonitor {

    private const val BUFFER_SIZE = 1200
    private const val NANOS_PER_TICK = 50_000_000L
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val MILLIS_PER_NANO = 1_000_000.0

    private val tickTimes = LongArray(BUFFER_SIZE)
    private var writeIndex = 0
    private var sampleCount = 0
    private var lastTickNanos = 0L
    private var task: Task? = null

    val currentTPS: Double
        get() {
            if (sampleCount < 2) return 20.0
            val lastDelta = tickTimes[((writeIndex - 1 + BUFFER_SIZE) % BUFFER_SIZE)]
            return if (lastDelta > 0) (NANOS_PER_SECOND / lastDelta).coerceAtMost(20.0) else 20.0
        }

    val averageTPS: Double
        get() {
            val count = sampleCount.coerceAtMost(BUFFER_SIZE)
            if (count < 2) return 20.0
            var totalNanos = 0L
            for (i in 0 until count) {
                totalNanos += tickTimes[i]
            }
            val avgNanos = totalNanos.toDouble() / count
            return if (avgNanos > 0) (NANOS_PER_SECOND / avgNanos).coerceAtMost(20.0) else 20.0
        }

    val mspt: Double
        get() {
            if (sampleCount < 2) return 50.0
            val lastDelta = tickTimes[((writeIndex - 1 + BUFFER_SIZE) % BUFFER_SIZE)]
            return lastDelta / MILLIS_PER_NANO
        }

    val averageMspt: Double
        get() {
            val count = sampleCount.coerceAtMost(BUFFER_SIZE)
            if (count < 2) return 50.0
            var totalNanos = 0L
            for (i in 0 until count) {
                totalNanos += tickTimes[i]
            }
            return (totalNanos.toDouble() / count) / MILLIS_PER_NANO
        }

    fun install() {
        stop()
        lastTickNanos = System.nanoTime()
        sampleCount = 0
        writeIndex = 0
        task = MinecraftServer.getSchedulerManager()
            .buildTask(::recordTick)
            .repeat(TaskSchedule.tick(1))
            .schedule()
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    fun reset() {
        sampleCount = 0
        writeIndex = 0
        tickTimes.fill(0)
        lastTickNanos = System.nanoTime()
    }

    private fun recordTick() {
        val now = System.nanoTime()
        val delta = now - lastTickNanos
        lastTickNanos = now

        tickTimes[writeIndex] = delta
        writeIndex = (writeIndex + 1) % BUFFER_SIZE
        sampleCount++
    }
}
