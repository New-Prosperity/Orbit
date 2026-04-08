package me.nebula.orbit.mode.game

import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task

class ActivityWatchdog(private val gameMode: GameMode) {

    @Volatile private var afkTask: Task? = null
    @Volatile private var voidTask: Task? = null

    fun startAfkCheck() {
        val thresholdSeconds = gameMode.settings.timing.afkEliminationSeconds
        if (thresholdSeconds <= 0) return
        val thresholdMillis = thresholdSeconds * 1000L
        afkTask = repeat(100) {
            if (gameMode.phase != GamePhase.PLAYING) return@repeat
            gameMode.tracker.forEachAlive { uuid ->
                if (gameMode.tracker.isAfk(uuid, thresholdMillis)) {
                    val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
                    if (player != null) {
                        gameMode.onAfkEliminated(player)
                        gameMode.eliminate(player)
                    }
                }
            }
        }
    }

    fun startVoidCheck() {
        val voidY = gameMode.settings.timing.voidDeathY
        if (voidY == Double.NEGATIVE_INFINITY) return
        voidTask = repeat(10) {
            if (gameMode.phase != GamePhase.PLAYING) return@repeat
            gameMode.tracker.forEachAlive { uuid ->
                if (gameMode.tracker.isRespawning(uuid)) return@forEachAlive
                val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: return@forEachAlive
                if (player.position.y() < voidY) {
                    gameMode.handleDeath(player)
                }
            }
        }
    }

    fun cleanupAfkCheck() {
        afkTask?.cancel()
        afkTask = null
    }

    fun cleanupVoidCheck() {
        voidTask?.cancel()
        voidTask = null
    }
}
