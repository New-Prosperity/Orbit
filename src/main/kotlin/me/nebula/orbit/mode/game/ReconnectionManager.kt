package me.nebula.orbit.mode.game

import me.nebula.gravity.reconnection.ReconnectionStore
import me.nebula.orbit.utils.scheduler.delay
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ReconnectionManager(private val gameMode: GameMode) {

    private val disconnectTimers = ConcurrentHashMap<UUID, Task>()
    @Volatile private var windowTask: Task? = null
    @Volatile private var windowExpired = false

    val isWindowExpired: Boolean get() = windowExpired

    fun startWindow(reconnectWindowSeconds: Int) {
        windowExpired = false
        if (reconnectWindowSeconds <= 0) return
        windowTask = delay(reconnectWindowSeconds * 20) {
            windowExpired = true
            for (uuid in gameMode.tracker.disconnected) autoEliminate(uuid)
        }
    }

    fun scheduleEliminate(uuid: UUID, delayTicks: Int) {
        disconnectTimers[uuid] = delay(delayTicks) { autoEliminate(uuid) }
    }

    fun cancelEliminate(uuid: UUID) {
        disconnectTimers.remove(uuid)?.cancel()
    }

    fun autoEliminate(uuid: UUID) {
        disconnectTimers.remove(uuid)
        if (!gameMode.tracker.isDisconnected(uuid)) return
        gameMode.tracker.eliminate(uuid)
        ReconnectionStore.delete(uuid)
        gameMode.checkGameEndInternal()
    }

    fun cleanup() {
        windowTask?.cancel()
        windowTask = null
        windowExpired = false
        disconnectTimers.values.forEach { it.cancel() }
        disconnectTimers.clear()
    }
}
