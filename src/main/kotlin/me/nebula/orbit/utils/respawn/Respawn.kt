package me.nebula.orbit.utils.respawn

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
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
        net.minestom.server.MinecraftServer.getInstanceManager().instances
            .firstOrNull { System.identityHashCode(it) == respawnPoint.instanceHash }
}

fun Player.setRespawnPoint(position: Pos = this.position) =
    RespawnManager.setPlayerRespawn(this, position)

fun Player.clearRespawnPoint() =
    RespawnManager.clearPlayerRespawn(this)

fun Player.getCustomRespawnPoint(): RespawnPoint? =
    RespawnManager.getRespawnPoint(this)
