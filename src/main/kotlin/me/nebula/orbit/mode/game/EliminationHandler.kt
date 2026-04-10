package me.nebula.orbit.mode.game

import me.nebula.orbit.displayUsername
import me.nebula.orbit.progression.ProgressionEvent
import me.nebula.orbit.progression.ProgressionEventBus
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.orbit.utils.killfeed.KillEvent
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.GameMode as MinestomGameMode
import java.util.UUID

class EliminationHandler(private val gameMode: GameMode) {

    fun eliminate(player: Player) {
        if (gameMode.phase != GamePhase.PLAYING) return
        if (!gameMode.tracker.isAlive(player.uuid) && !gameMode.tracker.isRespawning(player.uuid)) return

        gameMode.respawnManagerInternal.cancelFor(player.uuid)
        gameMode.tracker.eliminate(player.uuid)
        player.gameMode = MinestomGameMode.SPECTATOR
        player.teleport(gameMode.spawnPoint)

        gameMode.eliminationOrderInternal++
        val placement = gameMode.initialPlayerCount - gameMode.eliminationOrderInternal + 1
        gameMode.placementsInternal[player.uuid] = placement
        if (placement in 1..3) ProgressionEventBus.publish(ProgressionEvent.TopPlacement(player))
        player.sendMessage(player.translate("orbit.game.placement",
            "place" to placement.toString(),
            "total" to gameMode.initialPlayerCount.toString()))

        gameMode.autoSpectateInternal(player)
        gameMode.spectatorToolkit?.apply(player)
        gameMode.onPlayerEliminated(player)
        gameMode.checkGameEndInternal()
    }

    fun revive(player: Player, position: Pos) {
        if (gameMode.phase != GamePhase.PLAYING) return
        if (!gameMode.tracker.isSpectating(player.uuid)) return

        gameMode.spectatorToolkit?.remove(player)
        player.removeTag(gameMode.spectatorTargetTagInternal)
        player.stopSpectating()
        gameMode.tracker.revive(player.uuid)
        player.gameMode = MinestomGameMode.SURVIVAL
        player.teleport(position)
    }

    fun handleDeath(player: Player, killer: Player?) {
        if (gameMode.phase != GamePhase.PLAYING) return
        if (!gameMode.tracker.isAlive(player.uuid)) return

        gameMode.tracker.recordDeath(player.uuid)
        gameMode.semanticRecorderInternal.recordDeath(player, killer)
        runCatching { AchievementRegistry.progress(player, "deaths", 1) } // noqa: dangling runCatching

        if (killer != null && killer.uuid != player.uuid) {
            gameMode.tracker.recordKill(killer.uuid)
            runCatching { AchievementRegistry.progress(killer, "kills", 1) } // noqa: dangling runCatching
            gameMode.totalKillCountInternal++
            if (gameMode.totalKillCountInternal == 1) {
                gameMode.broadcastAll { p ->
                    p.sendMessage(p.translate("orbit.game.first_blood", "player" to killer.displayUsername))
                }
            }
            val streak = gameMode.tracker.streakOf(killer.uuid)
            if (streak > 1) gameMode.onKillStreak(killer, streak)
            if (streak >= 3) ProgressionEventBus.publish(ProgressionEvent.KillStreak(killer))
        }

        creditAssists(player.uuid, killer?.uuid)

        gameMode.killFeedInternal?.reportKill(KillEvent(killer = killer, victim = player))
        gameMode.deathRecapTracker?.sendRecap(player)

        gameMode.onPlayerDeath(player, killer)

        if (gameMode.overtimeControllerInternal.isSuddenDeath) {
            eliminate(player)
            return
        }

        val respawnConfig = gameMode.settings.respawn
        if (respawnConfig != null) {
            val livesRemaining = if (respawnConfig.maxLives > 0) {
                gameMode.tracker.decrementLives(player.uuid)
            } else {
                1
            }

            if (livesRemaining > 0) {
                gameMode.respawnManagerInternal.schedule(player, respawnConfig)
                return
            }
        }

        eliminate(player)
    }

    private fun creditAssists(victimUuid: UUID, killerUuid: UUID?) {
        val damagers = gameMode.tracker.recentDamagersOf(victimUuid, GameMode.ASSIST_WINDOW_MILLIS)
        for (damager in damagers) {
            if (damager != killerUuid && damager != victimUuid) {
                gameMode.tracker.recordAssist(damager)
                val assister = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(damager)
                if (assister != null) ProgressionEventBus.publish(ProgressionEvent.Assist(assister))
            }
        }
    }
}
