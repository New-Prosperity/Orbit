package me.nebula.orbit.utils.entitytracker

import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import kotlin.math.pow

fun Instance.nearbyEntities(center: Point, radius: Double): List<Entity> {
    val radiusSq = radius * radius
    return entities.filter { entity ->
        val dx = entity.position.x() - center.x()
        val dy = entity.position.y() - center.y()
        val dz = entity.position.z() - center.z()
        dx * dx + dy * dy + dz * dz <= radiusSq
    }
}

fun Instance.nearbyPlayers(center: Point, radius: Double): List<Player> {
    val radiusSq = radius * radius
    return players.filter { player ->
        val dx = player.position.x() - center.x()
        val dy = player.position.y() - center.y()
        val dz = player.position.z() - center.z()
        dx * dx + dy * dy + dz * dz <= radiusSq
    }
}

fun Player.nearestPlayer(maxRadius: Double = 100.0): Player? {
    val radiusSq = maxRadius * maxRadius
    var nearest: Player? = null
    var nearestDistSq = radiusSq
    instance?.players?.forEach { other ->
        if (other == this) return@forEach
        val distSq = position.distanceSquared(other.position)
        if (distSq < nearestDistSq) {
            nearestDistSq = distSq
            nearest = other
        }
    }
    return nearest
}

fun Player.nearestEntity(maxRadius: Double = 100.0): Entity? {
    val radiusSq = maxRadius * maxRadius
    var nearest: Entity? = null
    var nearestDistSq = radiusSq
    instance?.entities?.forEach { entity ->
        if (entity == this) return@forEach
        val distSq = position.distanceSquared(entity.position)
        if (distSq < nearestDistSq) {
            nearestDistSq = distSq
            nearest = entity
        }
    }
    return nearest
}

fun Instance.entitiesInLine(start: Point, direction: Point, length: Double, width: Double = 0.5): List<Entity> {
    val widthSq = width * width
    return entities.filter { entity ->
        val toEntity = entity.position.sub(start)
        val dot = toEntity.x() * direction.x() + toEntity.y() * direction.y() + toEntity.z() * direction.z()
        if (dot < 0 || dot > length) return@filter false
        val projX = start.x() + direction.x() * dot
        val projY = start.y() + direction.y() * dot
        val projZ = start.z() + direction.z() * dot
        val dx = entity.position.x() - projX
        val dy = entity.position.y() - projY
        val dz = entity.position.z() - projZ
        dx * dx + dy * dy + dz * dz <= widthSq
    }
}
