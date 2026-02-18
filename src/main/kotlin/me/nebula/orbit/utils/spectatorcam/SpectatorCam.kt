package me.nebula.orbit.utils.spectatorcam

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

enum class CamMode { FIRST_PERSON, FREE_CAM, ORBIT }

data class CamSession(
    val spectator: UUID,
    val target: UUID?,
    val mode: CamMode,
    val orbitRadius: Double,
    val orbitSpeed: Double,
    val previousGameMode: GameMode,
    @Volatile var angle: Double = 0.0,
    @Volatile var task: Task? = null,
)

object SpectatorCamManager {

    private val sessions = ConcurrentHashMap<UUID, CamSession>()
    private val alivePlayers = ConcurrentHashMap.newKeySet<UUID>()

    fun markAlive(player: Player) { alivePlayers.add(player.uuid) }
    fun markDead(player: Player) { alivePlayers.remove(player.uuid) }
    fun clearAlive() { alivePlayers.clear() }
    fun alivePlayerUUIDs(): Set<UUID> = alivePlayers.toSet()

    fun startSession(config: SpectatorCamConfig) {
        val spectator = requireNotNull(
            MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == config.spectatorUuid }
        ) { "Spectator not online" }

        stopSession(spectator)

        val session = CamSession(
            spectator = config.spectatorUuid,
            target = config.targetUuid,
            mode = config.mode,
            orbitRadius = config.orbitRadius,
            orbitSpeed = config.orbitSpeed,
            previousGameMode = spectator.gameMode,
        )

        sessions[config.spectatorUuid] = session
        spectator.gameMode = GameMode.SPECTATOR

        when (config.mode) {
            CamMode.FIRST_PERSON -> {
                val target = config.targetUuid?.let { uuid ->
                    MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == uuid }
                }
                target?.let { spectator.spectate(it) }
            }
            CamMode.FREE_CAM -> {}
            CamMode.ORBIT -> startOrbitTask(session, spectator)
        }
    }

    fun stopSession(player: Player) {
        val session = sessions.remove(player.uuid) ?: return
        session.task?.cancel()
        player.stopSpectating()
        player.gameMode = session.previousGameMode
    }

    fun isSpectating(player: Player): Boolean = sessions.containsKey(player.uuid)

    fun getSession(player: Player): CamSession? = sessions[player.uuid]

    fun cycleTarget(player: Player, forward: Boolean = true) {
        val session = sessions[player.uuid] ?: return
        if (session.mode != CamMode.FIRST_PERSON && session.mode != CamMode.ORBIT) return

        val alive = alivePlayers.toList()
        if (alive.isEmpty()) return

        val currentIndex = session.target?.let { alive.indexOf(it) } ?: -1
        val nextIndex = if (forward) {
            (currentIndex + 1) % alive.size
        } else {
            (currentIndex - 1 + alive.size) % alive.size
        }
        val nextUuid = alive[nextIndex]

        val newSession = session.copy(target = nextUuid)
        session.task?.cancel()
        sessions[player.uuid] = newSession

        val target = MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == nextUuid }

        when (newSession.mode) {
            CamMode.FIRST_PERSON -> target?.let { player.spectate(it) }
            CamMode.ORBIT -> startOrbitTask(newSession, player)
            CamMode.FREE_CAM -> {}
        }
    }

    fun switchMode(player: Player, mode: CamMode) {
        val session = sessions[player.uuid] ?: return
        session.task?.cancel()

        val newSession = session.copy(mode = mode)
        sessions[player.uuid] = newSession

        player.stopSpectating()

        when (mode) {
            CamMode.FIRST_PERSON -> {
                val target = newSession.target?.let { uuid ->
                    MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == uuid }
                }
                target?.let { player.spectate(it) }
            }
            CamMode.FREE_CAM -> {}
            CamMode.ORBIT -> startOrbitTask(newSession, player)
        }
    }

    fun clear() {
        sessions.values.forEach { session ->
            val player = MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == session.spectator }
            player?.let {
                session.task?.cancel()
                it.stopSpectating()
                it.gameMode = session.previousGameMode
            }
        }
        sessions.clear()
    }

    private fun startOrbitTask(session: CamSession, spectator: Player) {
        val target = session.target?.let { uuid ->
            MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == uuid }
        } ?: return

        val task = MinecraftServer.getSchedulerManager()
            .buildTask {
                val currentSession = sessions[spectator.uuid] ?: return@buildTask
                val t = MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == currentSession.target }
                    ?: return@buildTask

                val newAngle = currentSession.angle + currentSession.orbitSpeed
                sessions[spectator.uuid] = currentSession.copy(angle = newAngle)

                val targetPos = t.position
                val orbitX = targetPos.x() + cos(newAngle) * currentSession.orbitRadius
                val orbitZ = targetPos.z() + sin(newAngle) * currentSession.orbitRadius
                val orbitY = targetPos.y() + 2.0

                val dx = targetPos.x() - orbitX
                val dz = targetPos.z() - orbitZ
                val yaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
                val pitch = -15f

                spectator.teleport(Pos(orbitX, orbitY, orbitZ, yaw, pitch))
            }
            .repeat(TaskSchedule.tick(1))
            .schedule()

        session.task = task
    }
}

data class SpectatorCamConfig(
    val spectatorUuid: UUID,
    val targetUuid: UUID?,
    val mode: CamMode,
    val orbitRadius: Double,
    val orbitSpeed: Double,
)

class SpectatorCamBuilder @PublishedApi internal constructor(private val player: Player) {

    @PublishedApi internal var targetPlayer: Player? = null
    @PublishedApi internal var mode: CamMode = CamMode.FIRST_PERSON
    @PublishedApi internal var orbitRadius: Double = 5.0
    @PublishedApi internal var orbitSpeed: Double = 0.05

    fun target(player: Player) { targetPlayer = player }
    fun mode(mode: CamMode) { this.mode = mode }
    fun orbitRadius(radius: Double) { orbitRadius = radius }
    fun orbitSpeed(speed: Double) { orbitSpeed = speed }

    @PublishedApi internal fun build(): SpectatorCamConfig = SpectatorCamConfig(
        spectatorUuid = player.uuid,
        targetUuid = targetPlayer?.uuid,
        mode = mode,
        orbitRadius = orbitRadius,
        orbitSpeed = orbitSpeed,
    )
}

inline fun spectatorCam(player: Player, block: SpectatorCamBuilder.() -> Unit): SpectatorCamConfig {
    val config = SpectatorCamBuilder(player).apply(block).build()
    SpectatorCamManager.startSession(config)
    return config
}

fun Player.startSpectatorCam(target: Player, mode: CamMode = CamMode.FIRST_PERSON) {
    spectatorCam(this) {
        target(target)
        mode(mode)
    }
}

fun Player.stopSpectatorCam() = SpectatorCamManager.stopSession(this)
val Player.isInSpectatorCam: Boolean get() = SpectatorCamManager.isSpectating(this)
