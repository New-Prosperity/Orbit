package me.nebula.orbit.utils.gui

import me.nebula.ether.utils.logging.logger
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object GuiRegistry {

    private val logger = logger("GuiRegistry")
    private val open = ConcurrentHashMap<UUID, Gui>()
    private val pagination = ConcurrentHashMap<String, ConcurrentHashMap<UUID, Int>>()
    private var eventNode: EventNode<Event>? = null

    fun install() {
        if (eventNode != null) return
        val node = EventNode.all("gui-registry")
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            cleanup(event.player)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun uninstall() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        open.clear()
        pagination.clear()
    }

    internal fun track(player: Player, gui: Gui) {
        val previous = open.put(player.uuid, gui)
        if (previous != null && previous !== gui) {
            runCatching { previous.forceClose(player, notify = false) } // noqa: dangling runCatching
        }
    }

    internal fun untrack(player: Player, gui: Gui) {
        open.remove(player.uuid, gui)
    }

    fun currentlyOpen(player: Player): Gui? = open[player.uuid]

    fun openCount(): Int = open.size

    fun getPage(key: String, player: Player): Int = pagination[key]?.get(player.uuid) ?: 0

    fun setPage(key: String, player: Player, page: Int) {
        pagination.getOrPut(key) { ConcurrentHashMap() }[player.uuid] = page
    }

    fun clearPage(key: String, player: Player) {
        pagination[key]?.remove(player.uuid)
    }

    internal fun cleanup(player: Player) {
        val gui = open.remove(player.uuid)
        runCatching { gui?.forceClose(player, notify = false) }
            .onFailure { logger.warn(it) { "Failed to cleanup GUI on disconnect for ${player.uuid}" } }
        for (map in pagination.values) map.remove(player.uuid)
    }

    internal fun clearForTest() {
        open.clear()
        pagination.clear()
    }
}
