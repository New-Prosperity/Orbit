package me.nebula.orbit.mode.game

import me.nebula.orbit.progression.ProgressionEvent
import me.nebula.orbit.progression.ProgressionEventBus
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ActivityWatchdog(private val gameMode: GameMode) {

    @Volatile private var afkTask: Task? = null
    @Volatile private var voidTask: Task? = null
    @Volatile private var positionRecordTask: Task? = null
    @Volatile private var survivalMinuteTask: Task? = null
    @Volatile private var walkDistanceTask: Task? = null
    private val lastKnownPositions = ConcurrentHashMap<UUID, Pos>()

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

    fun startGameplayLoops(positionRecorder: (Player) -> Unit) {
        positionRecordTask = repeat(4) {
            if (gameMode.phase != GamePhase.PLAYING) return@repeat
            gameMode.tracker.forEachAlive { uuid ->
                val p = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
                if (p != null) positionRecorder(p)
            }
        }

        survivalMinuteTask = repeat(1200) {
            if (gameMode.phase != GamePhase.PLAYING) return@repeat
            gameMode.tracker.forEachAlive { uuid ->
                val p = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
                if (p != null) ProgressionEventBus.publish(ProgressionEvent.SurvivalTick(p))
            }
        }

        walkDistanceTask = repeat(20) {
            if (gameMode.phase != GamePhase.PLAYING) return@repeat
            gameMode.tracker.forEachAlive { uuid ->
                val p = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: return@forEachAlive
                val last = lastKnownPositions.put(uuid, p.position) ?: return@forEachAlive
                val dx = p.position.x() - last.x()
                val dz = p.position.z() - last.z()
                val dist = kotlin.math.sqrt(dx * dx + dz * dz).toInt()
                if (dist > 0) ProgressionEventBus.publish(ProgressionEvent.DistanceWalked(p, dist))
            }
        }
    }

    fun cleanupPositionRecording() {
        positionRecordTask?.cancel()
        positionRecordTask = null
    }

    fun cleanupGameplayLoops() {
        survivalMinuteTask?.cancel()
        survivalMinuteTask = null
        walkDistanceTask?.cancel()
        walkDistanceTask = null
        positionRecordTask?.cancel()
        positionRecordTask = null
        lastKnownPositions.clear()
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
