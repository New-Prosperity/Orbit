package me.nebula.orbit.utils.compass

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.item.Material
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.sqrt
import me.nebula.gravity.translation.Keys

sealed interface CompassTarget {
    data class PlayerTarget(val uuid: UUID) : CompassTarget
    data class PositionTarget(val pos: Pos) : CompassTarget
}

object CompassTracker {

    private val targets = ConcurrentHashMap<UUID, CompassTarget>()
    private var tickTask: Task? = null
    private var eventNode: EventNode<*>? = null
    private var tickCounter = 0L
    private var updateInterval = 10L

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
        updateInterval = updateIntervalTicks.toLong().coerceAtLeast(1L)
        val node = EventNode.all("compass-tracker")
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            targets.remove(event.player.uuid)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
        tickTask = repeat(1) { tick() }
    }

    fun stop() {
        tickTask?.cancel()
        tickTask = null
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
    }

    fun clear() {
        targets.clear()
        stop()
    }

    private fun tick() {
        tickCounter++
        val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
        val playerMap = onlinePlayers.associateBy { it.uuid }

        targets.forEach { (trackerUuid, target) ->
            if ((tickCounter + trackerUuid.hashCode()) % updateInterval != 0L) return@forEach
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
            val distance = sqrt(dx * dx + dz * dz)

            val direction = when {
                distance < 5 -> "HERE"
                else -> {
                    val angle = Math.toDegrees(atan2(-dx, dz)).let { if (it < 0) it + 360 else it }
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
                tracker.translate(Keys.Orbit.Util.Compass.Display, "direction" to direction, "distance" to "%.0f".format(distance)),
            )
        }
    }
}

fun Player.trackPlayer(target: Player) = CompassTracker.track(this, target)
fun Player.trackPosition(pos: Pos) = CompassTracker.track(this, pos)
fun Player.untrackCompass() = CompassTracker.untrack(this)
