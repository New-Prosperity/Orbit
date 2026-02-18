package me.nebula.orbit.utils.timer

import me.nebula.orbit.translation.translateDefault
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

class GameTimer(
    val name: String,
    private val durationTicks: Int,
    private val onTick: (GameTimer, Int) -> Unit,
    private val onComplete: (GameTimer) -> Unit,
    private val display: (Int) -> Component,
) {
    private var task: Task? = null
    private var remainingTicks: Int = durationTicks
    private val viewers = ConcurrentHashMap.newKeySet<java.util.UUID>()

    val remaining: Int get() = remainingTicks
    val elapsed: Int get() = durationTicks - remainingTicks
    val isRunning: Boolean get() = task != null

    fun start() {
        require(task == null) { "Timer already running" }
        remainingTicks = durationTicks
        task = MinecraftServer.getSchedulerManager().buildTask {
            if (remainingTicks <= 0) {
                stop()
                onComplete(this)
                return@buildTask
            }
            onTick(this, remainingTicks)
            broadcastDisplay()
            remainingTicks--
        }.repeat(TaskSchedule.tick(1)).schedule()
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    fun reset() {
        stop()
        remainingTicks = durationTicks
    }

    fun addViewer(player: Player) {
        viewers.add(player.uuid)
    }

    fun removeViewer(player: Player) {
        viewers.remove(player.uuid)
    }

    private fun broadcastDisplay() {
        val component = display(remainingTicks)
        viewers.forEach { uuid ->
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
                ?.sendActionBar(component)
        }
    }
}

class GameTimerBuilder {
    var name: String = "timer"
    var durationTicks: Int = 0
    private var onTick: (GameTimer, Int) -> Unit = { _, _ -> }
    private var onComplete: (GameTimer) -> Unit = {}
    private var display: (Int) -> Component = { ticks ->
        translateDefault("orbit.util.timer.display", "seconds" to "${ticks / 20}")
    }

    fun durationSeconds(seconds: Int) {
        durationTicks = seconds * 20
    }

    fun onTick(handler: (GameTimer, Int) -> Unit) {
        onTick = handler
    }

    fun onComplete(handler: (GameTimer) -> Unit) {
        onComplete = handler
    }

    fun display(renderer: (Int) -> Component) {
        display = renderer
    }

    fun build(): GameTimer = GameTimer(name, durationTicks, onTick, onComplete, display)
}

fun gameTimer(name: String, block: GameTimerBuilder.() -> Unit): GameTimer =
    GameTimerBuilder().apply { this.name = name }.apply(block).build()
