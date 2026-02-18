package me.nebula.orbit.utils.healthdisplay

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

private val miniMessage = MiniMessage.miniMessage()

object HealthDisplayManager {

    private var task: Task? = null
    private var formatter: (Player) -> String = { player -> "<red>${player.health.toInt()}\u2764" }
    private var updateIntervalTicks: Int = 20

    fun install(format: (Player) -> String = formatter, intervalTicks: Int = 20) {
        stop()
        formatter = format
        updateIntervalTicks = intervalTicks
        task = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(updateIntervalTicks))
            .schedule()
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    fun setFormat(format: (Player) -> String) {
        formatter = format
    }

    fun updatePlayer(player: Player) {
        val suffix = miniMessage.deserialize(formatter(player))
        player.displayName = Component.text()
            .append(Component.text(player.username))
            .append(Component.space())
            .append(suffix)
            .build()
    }

    private fun tick() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach(::updatePlayer)
    }
}

class HealthDisplayBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var formatter: (Player) -> String = { player -> "<red>${player.health.toInt()}\u2764" }
    @PublishedApi internal var intervalTicks: Int = 20

    fun format(formatter: (Player) -> String) { this.formatter = formatter }
    fun interval(ticks: Int) { intervalTicks = ticks }

    @PublishedApi internal fun build() {
        HealthDisplayManager.install(formatter, intervalTicks)
    }
}

inline fun healthDisplay(block: HealthDisplayBuilder.() -> Unit) {
    HealthDisplayBuilder().apply(block).build()
}
