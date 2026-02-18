package me.nebula.orbit.utils.raytrace

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import kotlin.math.cos
import kotlin.math.sin

data class BlockRayResult(val position: Vec, val block: Block, val distance: Double)
data class EntityRayResult(val entity: Entity, val distance: Double)

data class RaycastResult(
    val hitEntity: Entity?,
    val hitBlock: Point?,
    val hitPos: Pos,
    val distance: Double,
)

fun rayTraceBlock(
    instance: Instance,
    origin: Point,
    direction: Vec,
    maxDistance: Double = 5.0,
    step: Double = 0.1,
    ignoreAir: Boolean = true,
): BlockRayResult? {
    val dir = direction.normalize()
    var distance = 0.0
    while (distance <= maxDistance) {
        val pos = Vec(
            origin.x() + dir.x() * distance,
            origin.y() + dir.y() * distance,
            origin.z() + dir.z() * distance,
        )
        val block = instance.getBlock(pos)
        if (!block.isAir || !ignoreAir) {
            if (!block.isAir) return BlockRayResult(pos, block, distance)
        }
        distance += step
    }
    return null
}

fun rayTraceEntity(
    instance: Instance,
    origin: Point,
    direction: Vec,
    maxDistance: Double = 5.0,
    step: Double = 0.2,
    exclude: Entity? = null,
    radius: Double = 0.5,
): EntityRayResult? {
    val dir = direction.normalize()
    var distance = 0.0
    while (distance <= maxDistance) {
        val pos = Vec(
            origin.x() + dir.x() * distance,
            origin.y() + dir.y() * distance,
            origin.z() + dir.z() * distance,
        )
        val nearby = instance.getNearbyEntities(pos, radius)
        for (entity in nearby) {
            if (entity == exclude) continue
            return EntityRayResult(entity, distance)
        }
        distance += step
    }
    return null
}

fun raycast(
    instance: Instance,
    origin: Pos,
    direction: Vec,
    maxDistance: Double,
    stepSize: Double = 0.5,
): RaycastResult {
    val norm = direction.normalize()
    val steps = (maxDistance / stepSize).toInt()
    var closestEntity: Entity? = null
    var closestBlock: Point? = null
    var closestPos = origin
    var closestDist = maxDistance

    for (i in 1..steps) {
        val dist = i * stepSize
        val x = origin.x() + norm.x() * dist
        val y = origin.y() + norm.y() * dist
        val z = origin.z() + norm.z() * dist
        val current = Pos(x, y, z)

        if (closestBlock == null) {
            val block = instance.getBlock(current)
            if (!block.isAir && !block.isLiquid) {
                closestBlock = Vec(current.blockX().toDouble(), current.blockY().toDouble(), current.blockZ().toDouble())
                if (dist < closestDist) {
                    closestDist = dist
                    closestPos = current
                }
            }
        }

        if (closestEntity == null) {
            for (entity in instance.entities) {
                val bb = entity.boundingBox
                val ePos = entity.position
                val minX = ePos.x() - bb.width() / 2
                val minY = ePos.y()
                val minZ = ePos.z() - bb.depth() / 2
                val maxX = ePos.x() + bb.width() / 2
                val maxY = ePos.y() + bb.height()
                val maxZ = ePos.z() + bb.depth() / 2
                if (x in minX..maxX && y in minY..maxY && z in minZ..maxZ) {
                    closestEntity = entity
                    if (dist < closestDist) {
                        closestDist = dist
                        closestPos = current
                    }
                    break
                }
            }
        }

        if (closestEntity != null && closestBlock != null) break
    }

    return RaycastResult(closestEntity, closestBlock, closestPos, closestDist)
}

fun Player.lookDirection(): Vec {
    val pitch = Math.toRadians(position.pitch().toDouble())
    val yaw = Math.toRadians(position.yaw().toDouble())
    return Vec(
        -sin(yaw) * cos(pitch),
        -sin(pitch),
        cos(yaw) * cos(pitch),
    )
}

fun Player.rayTraceBlock(maxDistance: Double = 5.0): BlockRayResult? {
    val instance = instance ?: return null
    val eyePos = Vec(position.x(), position.y() + 1.62, position.z())
    return rayTraceBlock(instance, eyePos, lookDirection(), maxDistance)
}

fun Player.rayTraceEntity(maxDistance: Double = 5.0): EntityRayResult? {
    val instance = instance ?: return null
    val eyePos = Vec(position.x(), position.y() + 1.62, position.z())
    return rayTraceEntity(instance, eyePos, lookDirection(), maxDistance, exclude = this)
}

fun Player.lookingAt(maxDistance: Double = 5.0): RaycastResult {
    val inst = requireNotNull(instance) { "Player must be in an instance" }
    val eyePos = position.add(0.0, eyeHeight, 0.0) as Pos
    val direction = eyePos.direction()
    return raycast(inst, eyePos, direction, maxDistance)
}

fun Player.getLookedAtEntity(
    maxDistance: Double = 5.0,
    entityFilter: (Entity) -> Boolean = { true },
): Entity? {
    val inst = instance ?: return null
    val eyePos = position.add(0.0, eyeHeight, 0.0) as Pos
    val dir = eyePos.direction().normalize()
    val steps = (maxDistance / 0.25).toInt()
    var closest: Entity? = null
    var closestDistSq = Double.MAX_VALUE

    for (i in 1..steps) {
        val dist = i * 0.25
        val x = eyePos.x() + dir.x() * dist
        val y = eyePos.y() + dir.y() * dist
        val z = eyePos.z() + dir.z() * dist

        for (entity in inst.entities) {
            if (entity === this) continue
            if (!entityFilter(entity)) continue
            val bb = entity.boundingBox
            val ePos = entity.position
            val minX = ePos.x() - bb.width() / 2
            val minY = ePos.y()
            val minZ = ePos.z() - bb.depth() / 2
            val maxX = ePos.x() + bb.width() / 2
            val maxY = ePos.y() + bb.height()
            val maxZ = ePos.z() + bb.depth() / 2
            if (x in minX..maxX && y in minY..maxY && z in minZ..maxZ) {
                val dx = ePos.x() - eyePos.x()
                val dy = ePos.y() - eyePos.y()
                val dz = ePos.z() - eyePos.z()
                val dSq = dx * dx + dy * dy + dz * dz
                if (dSq < closestDistSq) {
                    closestDistSq = dSq
                    closest = entity
                }
            }
        }

        if (closest != null) return closest
    }

    return null
}

fun Player.getLookedAtBlock(maxDistance: Double = 5.0): Point? {
    val inst = instance ?: return null
    val eyePos = position.add(0.0, eyeHeight, 0.0) as Pos
    val dir = eyePos.direction().normalize()
    val steps = (maxDistance / 0.25).toInt()

    for (i in 1..steps) {
        val dist = i * 0.25
        val x = eyePos.x() + dir.x() * dist
        val y = eyePos.y() + dir.y() * dist
        val z = eyePos.z() + dir.z() * dist
        val pos = Pos(x, y, z)
        val block = inst.getBlock(pos)
        if (!block.isAir && !block.isLiquid) {
            return Vec(pos.blockX().toDouble(), pos.blockY().toDouble(), pos.blockZ().toDouble())
        }
    }

    return null
}
