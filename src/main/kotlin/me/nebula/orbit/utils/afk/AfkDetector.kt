package me.nebula.orbit.utils.afk

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AfkDetector {

    private val lastActivity = ConcurrentHashMap<UUID, Long>()
    private var afkThresholdMs = 300_000L
    private var eventNode: EventNode<*>? = null
    private var checkTask: Task? = null
    private var onAfkCallback: ((Player) -> Unit)? = null
    private var onReturnCallback: ((Player) -> Unit)? = null
    private val afkPlayers = ConcurrentHashMap.newKeySet<UUID>()

    fun start(thresholdMs: Long = 300_000L, onAfk: ((Player) -> Unit)? = null, onReturn: ((Player) -> Unit)? = null) {
        afkThresholdMs = thresholdMs
        onAfkCallback = onAfk
        onReturnCallback = onReturn

        val node = EventNode.all("afk-detector")
        node.addListener(PlayerMoveEvent::class.java) { event ->
            markActive(event.player)
        }
        node.addListener(PlayerChatEvent::class.java) { event ->
            markActive(event.player)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node

        checkTask = MinecraftServer.getSchedulerManager()
            .buildTask(::check)
            .repeat(TaskSchedule.seconds(10))
            .schedule()
    }

    fun stop() {
        checkTask?.cancel()
        checkTask = null
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        lastActivity.clear()
        afkPlayers.clear()
    }

    fun isAfk(player: Player): Boolean = afkPlayers.contains(player.uuid)

    fun markActive(player: Player) {
        lastActivity[player.uuid] = System.currentTimeMillis()
        if (afkPlayers.remove(player.uuid)) {
            onReturnCallback?.invoke(player)
        }
    }

    private fun check() {
        val now = System.currentTimeMillis()
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val last = lastActivity[player.uuid] ?: run {
                lastActivity[player.uuid] = now
                return@forEach
            }
            if (now - last >= afkThresholdMs && afkPlayers.add(player.uuid)) {
                onAfkCallback?.invoke(player)
            }
        }
        lastActivity.keys.removeIf { uuid ->
            MinecraftServer.getConnectionManager().onlinePlayers.none { it.uuid == uuid }
        }
    }
}

val Player.isAfk: Boolean get() = AfkDetector.isAfk(this)
