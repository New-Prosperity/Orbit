package me.nebula.orbit.utils.freeze

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object FreezeManager {

    private val frozen = ConcurrentHashMap.newKeySet<UUID>()
    private var eventNode: EventNode<*>? = null

    fun start() {
        val node = EventNode.all("freeze-manager")
        node.addListener(PlayerMoveEvent::class.java) { event ->
            if (frozen.contains(event.player.uuid)) {
                event.isCancelled = true
            }
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun stop() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        frozen.clear()
    }

    fun freeze(player: Player) { frozen.add(player.uuid) }
    fun unfreeze(player: Player) { frozen.remove(player.uuid) }
    fun isFrozen(player: Player): Boolean = frozen.contains(player.uuid)
    fun isFrozen(uuid: UUID): Boolean = frozen.contains(uuid)
    fun toggle(player: Player): Boolean {
        return if (frozen.contains(player.uuid)) {
            frozen.remove(player.uuid)
            false
        } else {
            frozen.add(player.uuid)
            true
        }
    }
    fun unfreezeAll() = frozen.clear()
    fun frozenPlayers(): Set<UUID> = frozen.toSet()
}

val Player.isFrozen: Boolean get() = FreezeManager.isFrozen(this)
fun Player.freeze() = FreezeManager.freeze(this)
fun Player.unfreeze() = FreezeManager.unfreeze(this)
