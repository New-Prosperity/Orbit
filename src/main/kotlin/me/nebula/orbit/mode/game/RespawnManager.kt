package me.nebula.orbit.mode.game

import me.nebula.gravity.reconnection.ReconnectionData
import me.nebula.gravity.reconnection.ReconnectionStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.graceperiod.GracePeriodManager
import me.nebula.orbit.utils.scheduler.delay
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RespawnManager(private val gameMode: GameMode) {

    private val timers = ConcurrentHashMap<UUID, Task>()

    fun schedule(player: Player, config: RespawnConfig) {
        gameMode.tracker.markRespawning(player.uuid)
        player.gameMode = net.minestom.server.entity.GameMode.SPECTATOR
        timers[player.uuid] = delay(config.respawnDelayTicks) {
            execute(player.uuid, config)
        }
    }

    fun cancelFor(uuid: UUID) {
        timers.remove(uuid)?.cancel()
    }

    fun applyInvincibility(player: Player) {
        val ticks = gameMode.settings.respawn?.invincibilityTicks ?: return
        if (ticks <= 0) return
        GracePeriodManager.apply(player, GameMode.RESPAWN_GRACE_NAME)
    }

    fun cleanup() {
        timers.values.forEach { it.cancel() }
        timers.clear()
    }

    private fun execute(uuid: UUID, config: RespawnConfig) {
        timers.remove(uuid)
        if (!gameMode.tracker.isRespawning(uuid)) return
        if (gameMode.phase != GamePhase.PLAYING) return

        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
        if (player == null) {
            gameMode.tracker.disconnect(uuid)
            val canReconnect = gameMode.settings.timing.allowReconnect && !gameMode.reconnectWindowExpiredInternal
            if (!canReconnect) {
                gameMode.tracker.eliminate(uuid)
                gameMode.checkGameEndInternal()
            } else {
                ReconnectionStore.save(
                    uuid,
                    ReconnectionData(
                        serverName = Orbit.serverName,
                        gameMode = Orbit.gameMode ?: "",
                        disconnectedAt = System.currentTimeMillis(),
                    ),
                )
            }
            return
        }

        gameMode.spectatorToolkit?.remove(player)
        player.removeTag(gameMode.spectatorTargetTagInternal)
        player.stopSpectating()
        gameMode.tracker.revive(uuid)
        val pos = gameMode.buildRespawnPosition(player)
        player.teleport(pos)
        player.gameMode = net.minestom.server.entity.GameMode.SURVIVAL
        if (config.clearInventoryOnRespawn) player.inventory.clear()
        gameMode.buildRespawnKit()?.apply(player)
        applyInvincibility(player)
        gameMode.onPlayerRespawn(player)
    }
}
