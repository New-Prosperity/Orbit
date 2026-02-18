package me.nebula.orbit.utils.waypoint

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import java.util.concurrent.ConcurrentHashMap

data class Waypoint(
    val name: String,
    val position: Pos,
    val instanceHash: Int,
    val icon: String = "default",
)

object WaypointManager {

    private val globalWaypoints = ConcurrentHashMap<String, Waypoint>()
    private val playerWaypoints = ConcurrentHashMap<java.util.UUID, ConcurrentHashMap<String, Waypoint>>()

    fun setGlobal(name: String, position: Pos, instance: Instance, icon: String = "default"): Waypoint {
        val wp = Waypoint(name, position, System.identityHashCode(instance), icon)
        globalWaypoints[name] = wp
        return wp
    }

    fun removeGlobal(name: String): Waypoint? = globalWaypoints.remove(name)

    fun getGlobal(name: String): Waypoint? = globalWaypoints[name]

    fun allGlobal(): Collection<Waypoint> = globalWaypoints.values

    fun set(player: Player, name: String, position: Pos, icon: String = "default"): Waypoint {
        val instance = player.instance ?: error("Player has no instance")
        val wp = Waypoint(name, position, System.identityHashCode(instance), icon)
        playerWaypoints.getOrPut(player.uuid) { ConcurrentHashMap() }[name] = wp
        return wp
    }

    fun remove(player: Player, name: String): Waypoint? =
        playerWaypoints[player.uuid]?.remove(name)

    fun get(player: Player, name: String): Waypoint? =
        playerWaypoints[player.uuid]?.get(name)

    fun all(player: Player): Collection<Waypoint> =
        playerWaypoints[player.uuid]?.values ?: emptyList()

    fun clearPlayer(player: Player) {
        playerWaypoints.remove(player.uuid)
    }
}

fun Player.setWaypoint(name: String, position: Pos = this.position, icon: String = "default"): Waypoint =
    WaypointManager.set(this, name, position, icon)

fun Player.removeWaypoint(name: String): Waypoint? =
    WaypointManager.remove(this, name)

fun Player.getWaypoint(name: String): Waypoint? =
    WaypointManager.get(this, name)

fun Player.allWaypoints(): Collection<Waypoint> =
    WaypointManager.all(this)
