package me.nebula.orbit.utils.freeze

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.tag.Tag

object FreezeManager {

    private val TAG = Tag.Boolean("nebula:frozen")
    private var eventNode: EventNode<*>? = null

    fun start() {
        val node = EventNode.all("freeze-manager")
        node.addListener(PlayerMoveEvent::class.java) { event ->
            if (event.player.getTag(TAG) == true) {
                event.isCancelled = true
            }
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun stop() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        unfreezeAll()
    }

    fun freeze(player: Player) {
        player.setTag(TAG, true)
        player.velocity = Vec.ZERO
    }
    fun unfreeze(player: Player) { player.removeTag(TAG) }
    fun isFrozen(player: Player): Boolean = player.getTag(TAG) == true
    fun toggle(player: Player): Boolean {
        return if (isFrozen(player)) {
            unfreeze(player)
            false
        } else {
            freeze(player)
            true
        }
    }

    fun unfreezeAll() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { it.removeTag(TAG) }
    }

    fun frozenPlayers(): Set<Player> =
        MinecraftServer.getConnectionManager().onlinePlayers
            .filter { it.getTag(TAG) == true }
            .toSet()
}

val Player.isFrozen: Boolean get() = FreezeManager.isFrozen(this)
fun Player.freeze() = FreezeManager.freeze(this)
fun Player.unfreeze() = FreezeManager.unfreeze(this)
