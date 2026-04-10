package me.nebula.orbit.mode.game

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player

class SpectatorManager(private val gameMode: GameMode) {

    fun nextTarget(player: Player): Player? {
        if (!gameMode.tracker.isSpectating(player.uuid)) return null
        val targets = resolveTargets(player)
        if (targets.isEmpty()) return null
        val tag = gameMode.spectatorTargetTagInternal
        val currentTargetUuid = player.getTag(tag)
        val currentIndex = if (currentTargetUuid != null) targets.indexOfFirst { it.uuid == currentTargetUuid } else -1
        val next = targets[(currentIndex + 1) % targets.size]
        player.setTag(tag, next.uuid)
        player.spectate(next)
        return next
    }

    fun previousTarget(player: Player): Player? {
        if (!gameMode.tracker.isSpectating(player.uuid)) return null
        val targets = resolveTargets(player)
        if (targets.isEmpty()) return null
        val tag = gameMode.spectatorTargetTagInternal
        val currentTargetUuid = player.getTag(tag)
        val currentIndex = if (currentTargetUuid != null) targets.indexOfFirst { it.uuid == currentTargetUuid } else targets.size
        val prev = targets[(currentIndex - 1 + targets.size) % targets.size]
        player.setTag(tag, prev.uuid)
        player.spectate(prev)
        return prev
    }

    fun autoSpectateOnEliminate(eliminated: Player) {
        val lastAttackerUuid = gameMode.tracker.recentDamagersOf(eliminated.uuid, GameMode.ASSIST_WINDOW_MILLIS).firstOrNull()
        val target = if (lastAttackerUuid != null && gameMode.tracker.isAlive(lastAttackerUuid)) {
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(lastAttackerUuid)
        } else {
            resolveTargets(eliminated).firstOrNull()
        }
        if (target != null) {
            eliminated.setTag(gameMode.spectatorTargetTagInternal, target.uuid)
            eliminated.spectate(target)
        }
    }

    fun resolveTargets(spectator: Player): List<Player> {
        val team = gameMode.tracker.teamOf(spectator.uuid)
        val candidateUuids = if (team != null && gameMode.isTeamMode) {
            val teammates = gameMode.tracker.aliveInTeam(team)
            if (teammates.isNotEmpty()) teammates else gameMode.tracker.alive
        } else {
            gameMode.tracker.alive
        }
        return candidateUuids.mapNotNull { MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it) }
    }
}
