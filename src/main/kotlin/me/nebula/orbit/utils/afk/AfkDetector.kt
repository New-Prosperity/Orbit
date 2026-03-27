package me.nebula.orbit.utils.afk

import me.nebula.orbit.utils.scheduler.repeat
import java.time.Duration
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task

object AfkDetector {

    private val LAST_ACTIVITY_TAG = Tag.Long("nebula:afk_last_activity")
    private val AFK_TAG = Tag.Boolean("nebula:afk")
    private var afkThresholdMs = 300_000L
    private var eventNode: EventNode<*>? = null
    private var checkTask: Task? = null
    private var onAfkCallback: ((Player) -> Unit)? = null
    private var onReturnCallback: ((Player) -> Unit)? = null

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

        checkTask = repeat(Duration.ofSeconds(10)) { check() }
    }

    fun stop() {
        checkTask?.cancel()
        checkTask = null
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        MinecraftServer.getConnectionManager().onlinePlayers.forEach {
            it.removeTag(LAST_ACTIVITY_TAG)
            it.removeTag(AFK_TAG)
        }
    }

    fun isAfk(player: Player): Boolean = player.getTag(AFK_TAG) == true

    fun markActive(player: Player) {
        player.setTag(LAST_ACTIVITY_TAG, System.currentTimeMillis())
        if (player.getTag(AFK_TAG) == true) {
            player.removeTag(AFK_TAG)
            onReturnCallback?.invoke(player)
        }
    }

    private fun check() {
        val now = System.currentTimeMillis()
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val last = player.getTag(LAST_ACTIVITY_TAG) ?: run {
                player.setTag(LAST_ACTIVITY_TAG, now)
                return@forEach
            }
            if (now - last >= afkThresholdMs && player.getTag(AFK_TAG) != true) {
                player.setTag(AFK_TAG, true)
                onAfkCallback?.invoke(player)
            }
        }
    }
}

val Player.isAfk: Boolean get() = AfkDetector.isAfk(this)
