package me.nebula.orbit.utils.playerlist

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

data class PlayerListConfig(
    val header: (Player) -> Component,
    val footer: (Player) -> Component,
    val updateIntervalTicks: Int = 20,
)

object PlayerListManager {

    private val config = AtomicReference<PlayerListConfig?>(null)
    private var task: Task? = null

    fun install(newConfig: PlayerListConfig) {
        config.set(newConfig)
        task?.cancel()
        task = MinecraftServer.getSchedulerManager().buildTask {
            val cfg = config.get() ?: return@buildTask
            MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
                player.sendPlayerListHeaderAndFooter(
                    cfg.header(player),
                    cfg.footer(player),
                )
            }
        }.repeat(TaskSchedule.tick(newConfig.updateIntervalTicks)).schedule()
    }

    fun uninstall() {
        task?.cancel()
        task = null
        config.set(null)
    }
}

class PlayerListBuilder {
    private var header: (Player) -> Component = { Component.empty() }
    private var footer: (Player) -> Component = { Component.empty() }
    var updateIntervalTicks: Int = 20

    fun header(text: String) {
        val mm = MiniMessage.miniMessage()
        header = { mm.deserialize(text) }
    }

    fun header(block: (Player) -> Component) {
        header = block
    }

    fun footer(text: String) {
        val mm = MiniMessage.miniMessage()
        footer = { mm.deserialize(text) }
    }

    fun footer(block: (Player) -> Component) {
        footer = block
    }

    fun build(): PlayerListConfig = PlayerListConfig(header, footer, updateIntervalTicks)
}

fun playerList(block: PlayerListBuilder.() -> Unit) {
    val config = PlayerListBuilder().apply(block).build()
    PlayerListManager.install(config)
}
