package me.nebula.orbit.utils.vanish

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object VanishManager {

    private val vanished = ConcurrentHashMap.newKeySet<UUID>()
    private var eventNode: EventNode<*>? = null

    fun start() {
        val node = EventNode.all("vanish-manager")
        node.addListener(PlayerSpawnEvent::class.java) { event ->
            vanished.forEach { uuid ->
                val vanishedPlayer = MinecraftServer.getConnectionManager().onlinePlayers
                    .firstOrNull { it.uuid == uuid }
                if (vanishedPlayer != null && vanishedPlayer != event.player) {
                    event.player.sendPacket(
                        net.minestom.server.network.packet.server.play.DestroyEntitiesPacket(
                            listOf(vanishedPlayer.entityId)
                        )
                    )
                }
            }
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun stop() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        vanished.clear()
    }

    fun vanish(player: Player) {
        vanished.add(player.uuid)
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { other ->
            if (other != player && !vanished.contains(other.uuid)) {
                other.sendPacket(
                    net.minestom.server.network.packet.server.play.DestroyEntitiesPacket(
                        listOf(player.entityId)
                    )
                )
            }
        }
    }

    fun unvanish(player: Player) {
        vanished.remove(player.uuid)
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { other ->
            if (other != player) {
                player.updateNewViewer(other)
            }
        }
    }

    fun isVanished(player: Player): Boolean = vanished.contains(player.uuid)
    fun isVanished(uuid: UUID): Boolean = vanished.contains(uuid)
    fun toggle(player: Player): Boolean {
        return if (vanished.contains(player.uuid)) {
            unvanish(player)
            false
        } else {
            vanish(player)
            true
        }
    }
    fun vanishedPlayers(): Set<UUID> = vanished.toSet()
}

val Player.isVanished: Boolean get() = VanishManager.isVanished(this)
fun Player.vanish() = VanishManager.vanish(this)
fun Player.unvanish() = VanishManager.unvanish(this)
