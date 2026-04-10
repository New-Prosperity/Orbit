package me.nebula.orbit.mode.game

import me.nebula.ether.utils.logging.withTrace
import me.nebula.orbit.traceId
import me.nebula.orbit.Orbit
import me.nebula.orbit.displayUsername
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.gravity.reconnection.ReconnectionData
import me.nebula.gravity.reconnection.ReconnectionStore
import net.minestom.server.entity.Player
import net.minestom.server.entity.GameMode as MinestomGameMode
import net.minestom.server.item.Material

class PlayerLifecycleHandler(private val gameMode: GameMode) {

    fun handleJoin(player: Player) = withTrace(player.traceId) {
        when (JoinPolicy.decide(player, gameMode)) {
            JoinDecision.Spectator -> applySpectatorJoin(player)
            JoinDecision.Participant -> applyParticipantJoin(player)
            JoinDecision.Reconnect -> applyReconnectJoin(player)
            JoinDecision.LateJoin -> applyLateJoin(player)
        }
    }

    fun handleDisconnect(player: Player) = withTrace(player.traceId) {
        gameMode.killFeedInternal?.removePlayer(player.uuid)
        when (val action = DisconnectPolicy.decide(player, gameMode)) {
            DisconnectAction.CleanRemove -> applyCleanRemove(player)
            DisconnectAction.SimpleRemove -> gameMode.tracker.remove(player.uuid)
            DisconnectAction.CombatLog -> applyCombatLogDisconnect(player)
            is DisconnectAction.ScheduleEliminate -> applyScheduledDisconnect(player, action.delayTicks)
            DisconnectAction.EliminateNow -> applyImmediateDisconnect(player)
        }
    }

    private fun applySpectatorJoin(player: Player) {
        player.gameMode = MinestomGameMode.SPECTATOR
        if (gameMode.phase == GamePhase.PLAYING) gameMode.spectatorToolkit?.apply(player)
    }

    private fun applyParticipantJoin(player: Player) {
        gameMode.tracker.join(player.uuid)
        gameMode.lobbyLifecycleInternal.applyHotbarTo(player)
        gameMode.onPlayerJoinWaiting(player)
        if (gameMode.phase == GamePhase.WAITING) {
            if (Orbit.hostOwner == player.uuid && gameMode.tracker.aliveCount < gameMode.settings.timing.minPlayers) {
                player.inventory.setItemStack(8, itemStack(Material.EMERALD) {
                    name(player.translate("orbit.game.force_start"))
                })
            }
            gameMode.checkMinPlayersInternal()
        }
    }

    private fun applyReconnectJoin(player: Player) {
        val previousState = gameMode.tracker.stateOf(player.uuid) as? PlayerState.Disconnected

        gameMode.reconnectionManagerInternal.cancelEliminate(player.uuid)
        ReconnectionStore.delete(player.uuid)
        gameMode.tracker.reconnect(player.uuid)

        if (previousState?.wasRespawning == true) {
            val pos = gameMode.buildRespawnPosition(player)
            player.teleport(pos)
            player.gameMode = MinestomGameMode.SURVIVAL
            if (gameMode.settings.respawn?.clearInventoryOnRespawn == true) player.inventory.clear()
            gameMode.buildRespawnKit()?.apply(player)
            gameMode.respawnManagerInternal.applyInvincibility(player)
        } else {
            player.gameMode = MinestomGameMode.SURVIVAL
        }

        gameMode.onPlayerReconnected(player)
    }

    private fun applyLateJoin(player: Player) {
        gameMode.tracker.join(player.uuid)

        if (gameMode.isTeamMode) {
            val smallestTeam = gameMode.tracker.allTeams().minByOrNull { gameMode.tracker.activeInTeam(it).size }
            if (smallestTeam != null) gameMode.tracker.assignTeam(player.uuid, smallestTeam)
        }

        val respawnConfig = gameMode.settings.respawn
        if (respawnConfig != null && respawnConfig.maxLives > 0) {
            gameMode.tracker.setLives(player.uuid, respawnConfig.maxLives)
        }

        if (gameMode.settings.lateJoin?.joinAsSpectator == true) {
            player.gameMode = MinestomGameMode.SPECTATOR
        } else {
            player.gameMode = MinestomGameMode.SURVIVAL
        }

        gameMode.onLateJoin(player)
    }

    private fun applyCleanRemove(player: Player) {
        gameMode.tracker.remove(player.uuid)
        gameMode.lobbyLifecycleInternal.removeHotbarFrom(player)
        gameMode.onPlayerLeaveWaiting(player)
        if (gameMode.phase == GamePhase.STARTING && gameMode.tracker.aliveCount < gameMode.settings.timing.minPlayers) {
            gameMode.forceBackToWaitingInternal()
        }
    }

    private fun applyCombatLogDisconnect(player: Player) {
        gameMode.respawnManagerInternal.cancelFor(player.uuid)
        player.removeTag(gameMode.spectatorTargetTagInternal)
        gameMode.tracker.eliminate(player.uuid)
        gameMode.onCombatLog(player)
        gameMode.onPlayerEliminated(player)
        gameMode.checkGameEndInternal()
    }

    private fun applyScheduledDisconnect(player: Player, delayTicks: Int) {
        gameMode.respawnManagerInternal.cancelFor(player.uuid)
        player.removeTag(gameMode.spectatorTargetTagInternal)
        gameMode.tracker.disconnect(player.uuid)
        ReconnectionStore.save(
            player.uuid,
            ReconnectionData(
                serverName = Orbit.serverName,
                gameMode = Orbit.gameMode ?: "",
                disconnectedAt = System.currentTimeMillis(),
            ),
        )
        if (delayTicks > 0) gameMode.reconnectionManagerInternal.scheduleEliminate(player.uuid, delayTicks)
        gameMode.onPlayerDisconnected(player)
        gameMode.checkGameEndInternal()
    }

    private fun applyImmediateDisconnect(player: Player) {
        gameMode.respawnManagerInternal.cancelFor(player.uuid)
        player.removeTag(gameMode.spectatorTargetTagInternal)
        gameMode.tracker.eliminate(player.uuid)
        gameMode.onPlayerEliminated(player)
        gameMode.checkGameEndInternal()
    }
}
