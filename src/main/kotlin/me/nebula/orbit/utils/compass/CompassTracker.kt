package me.nebula.orbit.utils.compass

import me.nebula.orbit.translation.translate
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface CompassTarget {
    data class PlayerTarget(val uuid: UUID) : CompassTarget
    data class PositionTarget(val pos: Pos) : CompassTarget
}

object CompassTracker {

    private val targets = ConcurrentHashMap<UUID, CompassTarget>()
    private var tickTask: Task? = null

    fun track(player: Player, target: Player) {
        targets[player.uuid] = CompassTarget.PlayerTarget(target.uuid)
    }

    fun track(player: Player, position: Pos) {
        targets[player.uuid] = CompassTarget.PositionTarget(position)
    }

    fun untrack(player: Player) {
        targets.remove(player.uuid)
    }

    fun getTarget(player: Player): CompassTarget? = targets[player.uuid]

    fun isTracking(player: Player): Boolean = targets.containsKey(player.uuid)

    fun start(updateIntervalTicks: Int = 10) {
        stop()
        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(updateIntervalTicks))
            .schedule()
    }

    fun stop() {
        tickTask?.cancel()
        tickTask = null
    }

    fun clear() {
        targets.clear()
        stop()
    }

    private fun tick() {
        val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
        val playerMap = onlinePlayers.associateBy { it.uuid }

        targets.forEach { (trackerUuid, target) ->
            val tracker = playerMap[trackerUuid] ?: return@forEach

            val hasCompass = (0 until tracker.inventory.size).any {
                tracker.inventory.getItemStack(it).material() == Material.COMPASS
            }
            if (!hasCompass) return@forEach

            val targetPos = when (target) {
                is CompassTarget.PlayerTarget -> playerMap[target.uuid]?.position
                is CompassTarget.PositionTarget -> target.pos
            } ?: return@forEach

            val dx = targetPos.x() - tracker.position.x()
            val dz = targetPos.z() - tracker.position.z()
            val distance = kotlin.math.sqrt(dx * dx + dz * dz)

            val direction = when {
                distance < 5 -> "HERE"
                else -> {
                    val angle = Math.toDegrees(kotlin.math.atan2(-dx, dz)).let { if (it < 0) it + 360 else it }
                    when {
                        angle < 22.5 || angle >= 337.5 -> "N"
                        angle < 67.5 -> "NE"
                        angle < 112.5 -> "E"
                        angle < 157.5 -> "SE"
                        angle < 202.5 -> "S"
                        angle < 247.5 -> "SW"
                        angle < 292.5 -> "W"
                        else -> "NW"
                    }
                }
            }

            tracker.sendActionBar(
                tracker.translate("orbit.util.compass.display", "direction" to direction, "distance" to "%.0f".format(distance)),
            )
        }
    }
}

fun Player.trackPlayer(target: Player) = CompassTracker.track(this, target)
fun Player.trackPosition(pos: Pos) = CompassTracker.track(this, pos)
fun Player.untrackCompass() = CompassTracker.untrack(this)
