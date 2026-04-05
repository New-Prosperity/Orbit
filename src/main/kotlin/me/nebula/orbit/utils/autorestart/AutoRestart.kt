package me.nebula.orbit.utils.autorestart

import me.nebula.ether.utils.duration.DurationFormatter
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.scheduler.delay
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

class AutoRestartConfig @PublishedApi internal constructor() {

    @PublishedApi internal var delay: Duration = Duration.ofHours(6)
    @PublishedApi internal val warningIntervals = mutableListOf<Duration>()
    @PublishedApi internal var warningTemplate: String = "<red>Server restarting in {time}!"
    @PublishedApi internal var kickMessage: String = "<red>Server is restarting."
    @PublishedApi internal var restartAction: () -> Unit = { MinecraftServer.stopCleanly() }

    fun after(duration: Duration) { delay = duration }
    fun warnings(vararg intervals: Duration) { warningIntervals.addAll(intervals) }
    fun warningMessage(template: String) { warningTemplate = template }
    fun kickMessage(message: String) { kickMessage = message }
    fun onRestart(action: () -> Unit) { restartAction = action }
}

object AutoRestartManager {

    @Volatile private var task: Task? = null
    @Volatile private var startTimeMs: Long = 0L
    @Volatile private var delayMs: Long = 0L
    @Volatile private var config: AutoRestartConfig? = null
    private val warningTasks = CopyOnWriteArrayList<Task>()

    val isScheduled: Boolean get() = task != null

    fun getTimeRemaining(): Duration {
        if (!isScheduled) return Duration.ZERO
        val elapsed = System.currentTimeMillis() - startTimeMs
        val remaining = delayMs - elapsed
        return if (remaining > 0) Duration.ofMillis(remaining) else Duration.ZERO
    }

    fun install(config: AutoRestartConfig) {
        cancel()
        this.config = config
        delayMs = config.delay.toMillis()
        startTimeMs = System.currentTimeMillis()

        config.warningIntervals
            .sortedDescending()
            .filter { it < config.delay }
            .forEach { interval ->
                val fireAt = config.delay.minus(interval)
                val warningTask = delay(fireAt) { broadcastWarning(interval) }
                warningTasks.add(warningTask)
            }

        task = delay(config.delay) { executeRestart() }
    }

    fun scheduleRestart(delay: Duration) {
        val cfg = config ?: AutoRestartConfig().apply { after(delay) }
        cfg.delay = delay
        install(cfg)
    }

    fun cancel() {
        task?.cancel()
        task = null
        warningTasks.forEach { it.cancel() }
        warningTasks.clear()
        startTimeMs = 0L
        delayMs = 0L
    }

    private fun broadcastWarning(remaining: Duration) {
        val cfg = config ?: return
        val timeStr = DurationFormatter.format(remaining.toMillis())
        val message = miniMessage.deserialize(cfg.warningTemplate.replace("{time}", timeStr))
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { it.sendMessage(message) }
    }

    private fun executeRestart() {
        val cfg = config ?: return
        val kickComponent = miniMessage.deserialize(cfg.kickMessage)
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { it.kick(kickComponent) }
        cfg.restartAction()
        cancel()
    }

}

inline fun autoRestart(block: AutoRestartConfig.() -> Unit) {
    AutoRestartManager.install(AutoRestartConfig().apply(block))
}
