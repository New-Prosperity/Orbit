package me.nebula.orbit.mode.game

import me.nebula.orbit.utils.scheduler.delay
import net.minestom.server.timer.Task
import java.util.concurrent.atomic.AtomicInteger

class LateJoinManager {

    @Volatile private var windowTask: Task? = null
    @Volatile private var windowExpired = false
    private val joinCount = AtomicInteger(0)

    val isWindowExpired: Boolean get() = windowExpired

    fun startWindow(windowSeconds: Int) {
        windowExpired = false
        joinCount.set(0)
        if (windowSeconds <= 0) return
        windowTask = delay(windowSeconds * 20) {
            windowExpired = true
        }
    }

    fun tryClaimSlot(maxLateJoiners: Int): Boolean {
        if (windowExpired) return false
        if (maxLateJoiners <= 0) return true
        val previous = joinCount.getAndUpdate { current ->
            if (current >= maxLateJoiners) current else current + 1
        }
        return previous < maxLateJoiners
    }

    fun cleanup() {
        windowTask?.cancel()
        windowTask = null
        windowExpired = false
        joinCount.set(0)
    }
}
