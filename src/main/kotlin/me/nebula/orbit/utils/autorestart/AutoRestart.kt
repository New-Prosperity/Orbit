package me.nebula.orbit.utils.autorestart

import me.nebula.ether.utils.duration.DurationFormatter
import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.notification.Priority
import me.nebula.gravity.notification.notify
import me.nebula.orbit.notification.title
import me.nebula.orbit.translation.translate
import me.nebula.orbit.user.onlineUsers
import me.nebula.orbit.utils.scheduler.delay
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

class AutoRestartConfig @PublishedApi internal constructor() {

    @PublishedApi internal var delay: Duration = Duration.ofHours(6)
    @PublishedApi internal val warningIntervals = mutableListOf<Duration>()
    @PublishedApi internal var warningKey: TranslationKey = "orbit.autorestart.warning".asTranslationKey()
    @PublishedApi internal var kickKey: TranslationKey = "orbit.autorestart.kick".asTranslationKey()
    @PublishedApi internal var restartAction: () -> Unit = { MinecraftServer.stopCleanly() }

    fun after(duration: Duration) { delay = duration }
    fun warnings(vararg intervals: Duration) { warningIntervals.addAll(intervals) }
    fun warningKey(key: String) { warningKey = key.asTranslationKey() }
    fun kickKey(key: String) { kickKey = key.asTranslationKey() }
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
        val critical = remaining.toSeconds() <= 30
        for (user in onlineUsers()) {
            notify(user) {
                chat(
                    message = user.translate(cfg.warningKey, "time" to timeStr),
                    priority = if (critical) Priority.CRITICAL else Priority.INFO,
                )
                if (critical) {
                    title(
                        title = Component.text("Restart in $timeStr"),
                        fadeInTicks = 4,
                        stayTicks = 30,
                        fadeOutTicks = 10,
                    )
                    sound("minecraft:block.note_block.pling", pitch = 1.5f)
                } else {
                    sound("minecraft:block.note_block.bell", pitch = 0.8f)
                }
            }
        }
    }

    private fun executeRestart() {
        val cfg = config ?: return
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            player.kick(player.translate(cfg.kickKey))
        }
        cfg.restartAction()
        cancel()
    }

}

inline fun autoRestart(block: AutoRestartConfig.() -> Unit) {
    AutoRestartManager.install(AutoRestartConfig().apply(block))
}
