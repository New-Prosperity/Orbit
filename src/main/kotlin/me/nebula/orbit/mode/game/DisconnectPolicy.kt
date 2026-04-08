package me.nebula.orbit.mode.game

import net.minestom.server.entity.Player

sealed interface DisconnectAction {
    data object CleanRemove : DisconnectAction
    data object SimpleRemove : DisconnectAction
    data object CombatLog : DisconnectAction
    data class ScheduleEliminate(val delayTicks: Int) : DisconnectAction
    data object EliminateNow : DisconnectAction
}

object DisconnectPolicy {

    fun decide(player: Player, gameMode: GameMode): DisconnectAction {
        val phase = gameMode.phase
        val tracker = gameMode.tracker
        val settings = gameMode.settings
        return when (phase) {
            GamePhase.WAITING -> DisconnectAction.CleanRemove
            GamePhase.STARTING -> DisconnectAction.CleanRemove
            GamePhase.ENDING -> DisconnectAction.SimpleRemove
            GamePhase.PLAYING -> {
                if (!tracker.isAlive(player.uuid) && !tracker.isRespawning(player.uuid)) {
                    return DisconnectAction.SimpleRemove
                }
                val combatLogSeconds = settings.timing.combatLogSeconds
                if (combatLogSeconds > 0 && tracker.isInCombat(player.uuid, combatLogSeconds * 1000L)) {
                    return DisconnectAction.CombatLog
                }
                val canReconnect = settings.timing.allowReconnect && !gameMode.reconnectWindowExpiredInternal
                if (canReconnect) {
                    val eliminationSeconds = settings.timing.disconnectEliminationSeconds
                    return DisconnectAction.ScheduleEliminate(if (eliminationSeconds > 0) eliminationSeconds * 20 else 0)
                }
                DisconnectAction.EliminateNow
            }
        }
    }
}
