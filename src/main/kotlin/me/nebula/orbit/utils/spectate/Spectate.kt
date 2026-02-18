package me.nebula.orbit.utils.spectate

import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SpectateManager {

    private val spectating = ConcurrentHashMap<UUID, UUID>()
    private val previousGameMode = ConcurrentHashMap<UUID, GameMode>()

    fun startSpectating(spectator: Player, target: Player) {
        previousGameMode.putIfAbsent(spectator.uuid, spectator.gameMode)
        spectating[spectator.uuid] = target.uuid
        spectator.gameMode = GameMode.SPECTATOR
        spectator.spectate(target)
    }

    fun stopSpectating(spectator: Player) {
        spectating.remove(spectator.uuid)
        spectator.stopSpectating()
        val previous = previousGameMode.remove(spectator.uuid) ?: GameMode.SURVIVAL
        spectator.gameMode = previous
    }

    fun isSpectating(player: Player): Boolean = spectating.containsKey(player.uuid)

    fun getTarget(spectator: Player): UUID? = spectating[spectator.uuid]

    fun spectatorsOf(target: Player): List<UUID> =
        spectating.entries.filter { it.value == target.uuid }.map { it.key }

    fun clear() {
        spectating.clear()
        previousGameMode.clear()
    }
}

fun Player.spectatePlayer(target: Player) = SpectateManager.startSpectating(this, target)
fun Player.stopSpectatingPlayer() = SpectateManager.stopSpectating(this)
val Player.isSpectatingPlayer: Boolean get() = SpectateManager.isSpectating(this)
