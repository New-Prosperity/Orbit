package me.nebula.orbit.mode.game

import me.nebula.orbit.utils.vanish.VanishManager
import net.minestom.server.entity.Player

sealed interface JoinDecision {
    data object Spectator : JoinDecision
    data object Participant : JoinDecision
    data object Reconnect : JoinDecision
    data object LateJoin : JoinDecision
}

object JoinPolicy {

    fun decide(player: Player, gameMode: GameMode): JoinDecision {
        val phase = gameMode.phase
        val tracker = gameMode.tracker
        val settings = gameMode.settings
        return when (phase) {
            GamePhase.WAITING, GamePhase.STARTING -> {
                if (VanishManager.isVanished(player) || tracker.size >= settings.timing.maxPlayers) {
                    JoinDecision.Spectator
                } else {
                    JoinDecision.Participant
                }
            }
            GamePhase.PLAYING -> {
                if (settings.timing.allowReconnect && player.uuid in tracker && tracker.isDisconnected(player.uuid)) {
                    JoinDecision.Reconnect
                } else if (gameMode.tryClaimLateJoinSlotInternal() && !VanishManager.isVanished(player)) {
                    JoinDecision.LateJoin
                } else {
                    JoinDecision.Spectator
                }
            }
            GamePhase.ENDING -> JoinDecision.Spectator
        }
    }
}
