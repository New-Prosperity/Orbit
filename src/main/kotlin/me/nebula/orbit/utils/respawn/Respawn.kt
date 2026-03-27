package me.nebula.orbit.utils.respawn

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.instance.Instance
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class RespawnPoint(
    val position: Pos,
    val instanceHash: Int,
)

object RespawnManager {

    private val playerRespawnPoints = ConcurrentHashMap<UUID, RespawnPoint>()
    private var defaultRespawn: RespawnPoint? = null
    private var eventNode: EventNode<*>? = null

    fun start() {
        val node = EventNode.all("respawn-manager")
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            playerRespawnPoints.remove(event.player.uuid)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun stop() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        playerRespawnPoints.clear()
    }

    fun setDefault(position: Pos, instance: Instance) {
        defaultRespawn = RespawnPoint(position, System.identityHashCode(instance))
    }

    fun setPlayerRespawn(player: Player, position: Pos) {
        val instance = player.instance ?: return
        playerRespawnPoints[player.uuid] = RespawnPoint(position, System.identityHashCode(instance))
    }

    fun clearPlayerRespawn(player: Player) {
        playerRespawnPoints.remove(player.uuid)
    }

    fun getRespawnPoint(player: Player): RespawnPoint? =
        playerRespawnPoints[player.uuid] ?: defaultRespawn

    fun resolveInstance(respawnPoint: RespawnPoint): Instance? =
        MinecraftServer.getInstanceManager().instances
            .firstOrNull { System.identityHashCode(it) == respawnPoint.instanceHash }
}

fun Player.setRespawnPoint(position: Pos = this.position) =
    RespawnManager.setPlayerRespawn(this, position)

fun Player.clearRespawnPoint() =
    RespawnManager.clearPlayerRespawn(this)

fun Player.getCustomRespawnPoint(): RespawnPoint? =
    RespawnManager.getRespawnPoint(this)
