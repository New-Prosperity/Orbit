package me.nebula.orbit.utils.trail

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TrailConfig(
    val particle: Particle,
    val count: Int = 1,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
    val speed: Float = 0f,
    val heightOffset: Double = 0.1,
    val visibilityRadius: Double = 32.0,
)

object TrailManager {

    private val trails = ConcurrentHashMap<UUID, TrailConfig>()
    private var tickTask: Task? = null

    fun setTrail(player: Player, config: TrailConfig) {
        trails[player.uuid] = config
    }

    fun removeTrail(player: Player) {
        trails.remove(player.uuid)
    }

    fun hasTrail(player: Player): Boolean = trails.containsKey(player.uuid)

    fun getTrail(player: Player): TrailConfig? = trails[player.uuid]

    fun start(intervalTicks: Int = 2) {
        stop()
        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(intervalTicks))
            .schedule()
    }

    fun stop() {
        tickTask?.cancel()
        tickTask = null
    }

    fun clear() {
        trails.clear()
        stop()
    }

    private fun tick() {
        val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
        val playerMap = onlinePlayers.associateBy { it.uuid }

        trails.forEach { (uuid, config) ->
            val player = playerMap[uuid] ?: return@forEach
            if (!player.isOnGround && player.velocity.length() < 0.1) return@forEach

            val pos = player.position
            val packet = ParticlePacket(
                config.particle,
                pos.x(), pos.y() + config.heightOffset, pos.z(),
                config.offsetX, config.offsetY, config.offsetZ,
                config.speed,
                config.count,
            )

            player.instance?.players?.forEach { viewer ->
                if (viewer == player) return@forEach
                if (viewer.position.distanceSquared(pos) > config.visibilityRadius * config.visibilityRadius) return@forEach
                viewer.sendPacket(packet)
            }
        }
    }
}

fun Player.setTrail(config: TrailConfig) = TrailManager.setTrail(this, config)
fun Player.removeTrail() = TrailManager.removeTrail(this)
val Player.hasTrail: Boolean get() = TrailManager.hasTrail(this)
