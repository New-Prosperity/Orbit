package me.nebula.orbit.utils.entitybuilder

import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Entity
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import kotlin.math.sqrt

interface Sensor {
    val period: Int get() = 20
    fun sense(entity: SmartEntity)
}

internal fun hasLineOfSight(instance: Instance, from: Point, to: Point): Boolean {
    val dx = to.x() - from.x()
    val dy = to.y() - from.y()
    val dz = to.z() - from.z()
    val distSq = dx * dx + dy * dy + dz * dz
    if (distSq < 0.25) return true
    val steps = (sqrt(distSq).toInt() * 2).coerceAtLeast(2)
    for (i in 1 until steps) {
        val t = i.toDouble() / steps
        val block = instance.getBlock(
            (from.x() + dx * t).toInt(),
            (from.y() + dy * t).toInt(),
            (from.z() + dz * t).toInt(),
        )
        if (block.isSolid) return false
    }
    return true
}

class NearestPlayerSensor(
    private val range: Double = 32.0,
    private val minRange: Double = 0.0,
    override val period: Int = 20,
) : Sensor {
    override fun sense(entity: SmartEntity) {
        val instance = entity.instance ?: return
        val pos = entity.position
        val minRangeSq = minRange * minRange
        var best: Player? = null
        var bestSq = Double.MAX_VALUE
        for (other in instance.getNearbyEntities(pos, range)) {
            if (other !is Player) continue
            val dSq = other.position.distanceSquared(pos)
            if (dSq < minRangeSq) continue
            if (dSq < bestSq) {
                bestSq = dSq
                best = other
            }
        }
        entity.memory.set(MemoryKeys.NEAREST_PLAYER, best)
    }
}

class LowHealthSensor(
    private val threshold: Float = 0.3f,
    private val panicTicks: Int = 60,
    override val period: Int = 10,
) : Sensor {
    override fun sense(entity: SmartEntity) {
        val pct = entity.healthPercent()
        if (pct <= threshold && !entity.memory.has(MemoryKeys.PANIC_TICKS)) {
            entity.memory.set(MemoryKeys.PANIC_TICKS, panicTicks)
        }
    }
}

class DamageSensor(
    private val retargetOnDamage: Boolean = true,
    override val period: Int = 1,
) : Sensor {
    override fun sense(entity: SmartEntity) {
        if (!retargetOnDamage) return
        val attacker = entity.memory.get(MemoryKeys.LAST_ATTACKER) ?: return
        if (attacker.isRemoved) {
            entity.memory.clear(MemoryKeys.LAST_ATTACKER)
            return
        }
        if (!entity.memory.has(MemoryKeys.ATTACK_TARGET)) {
            entity.memory.set(MemoryKeys.ATTACK_TARGET, attacker)
        }
    }
}

class NearestEntitySensor(
    private val range: Double = 16.0,
    private val target: MemoryKey<Entity> = MemoryKeys.ATTACK_TARGET,
    private val predicate: (Entity) -> Boolean = { true },
    override val period: Int = 10,
) : Sensor {
    override fun sense(entity: SmartEntity) {
        val instance = entity.instance ?: return
        val pos = entity.position
        var best: Entity? = null
        var bestSq = Double.MAX_VALUE
        for (other in instance.getNearbyEntities(pos, range)) {
            if (other === entity || other.isRemoved) continue
            if (!predicate(other)) continue
            val dSq = other.position.distanceSquared(pos)
            if (dSq < bestSq) {
                bestSq = dSq
                best = other
            }
        }
        entity.memory.set(target, best)
    }
}

class NearestNonOwnerEntitySensor(
    private val range: Double = 24.0,
    private val target: MemoryKey<Entity> = MemoryKeys.ATTACK_TARGET,
    private val ownerKey: MemoryKey<Entity> = MemoryKeys.OWNER,
    private val playersOnly: Boolean = true,
    override val period: Int = 10,
) : Sensor {
    override fun sense(entity: SmartEntity) {
        val instance = entity.instance ?: return
        val owner = entity.memory.get(ownerKey)
        val ownerUuid = owner?.uuid
        val pos = entity.position
        var best: Entity? = null
        var bestSq = Double.MAX_VALUE
        for (other in instance.getNearbyEntities(pos, range)) {
            if (other === entity || other.isRemoved) continue
            if (other === owner || other.uuid == ownerUuid) continue
            if (playersOnly && other !is Player) continue
            val dSq = other.position.distanceSquared(pos)
            if (dSq < bestSq) {
                bestSq = dSq
                best = other
            }
        }
        entity.memory.set(target, best)
    }
}

class LineOfSightSensor(
    private val targetKey: MemoryKey<Entity> = MemoryKeys.ATTACK_TARGET,
    private val lastPositionKey: MemoryKey<Point> = MemoryKeys.LAST_KNOWN_POSITION,
    private val range: Double = 32.0,
    private val gracePeriodTicks: Int = 60,
    override val period: Int = 5,
) : Sensor {

    private var ticksOutOfSight = 0

    override fun sense(entity: SmartEntity) {
        val target = entity.memory.get(targetKey)
        if (target == null || target.isRemoved || (target is LivingEntity && target.isDead)) {
            entity.memory.clear(targetKey)
            ticksOutOfSight = 0
            return
        }
        val instance = entity.instance ?: return
        val eye = entity.position.add(0.0, entity.eyeHeight, 0.0)
        val targetEye = target.position.add(0.0, if (target is LivingEntity) target.eyeHeight else 0.0, 0.0)
        if (eye.distance(targetEye) > range) {
            entity.memory.clear(targetKey)
            ticksOutOfSight = 0
            return
        }
        if (hasLineOfSight(instance, eye, targetEye)) {
            entity.memory.set(lastPositionKey, target.position)
            ticksOutOfSight = 0
        } else {
            ticksOutOfSight += period
            if (ticksOutOfSight >= gracePeriodTicks) {
                entity.memory.clear(targetKey)
                ticksOutOfSight = 0
            }
        }
    }
}

class LastKnownPositionSensor(
    private val targetKey: MemoryKey<Entity> = MemoryKeys.ATTACK_TARGET,
    private val outputKey: MemoryKey<Point> = MemoryKeys.LAST_KNOWN_POSITION,
    override val period: Int = 5,
) : Sensor {
    override fun sense(entity: SmartEntity) {
        val target = entity.memory.get(targetKey) ?: return
        if (target.isRemoved) return
        entity.memory.set(outputKey, target.position)
    }
}

class VisibleNearestPlayerSensor(
    private val range: Double = 32.0,
    private val ignoreSneaking: Boolean = true,
    private val requireLineOfSight: Boolean = true,
    override val period: Int = 20,
) : Sensor {
    override fun sense(entity: SmartEntity) {
        val instance = entity.instance ?: return
        val pos = entity.position
        val eye = pos.add(0.0, entity.eyeHeight, 0.0)
        var best: Player? = null
        var bestSq = Double.MAX_VALUE
        for (other in instance.getNearbyEntities(pos, range)) {
            if (other !is Player || other.isRemoved) continue
            if (ignoreSneaking && other.isSneaking) continue
            val dSq = other.position.distanceSquared(pos)
            if (dSq >= bestSq) continue
            if (requireLineOfSight && !hasLineOfSight(instance, eye, other.position.add(0.0, other.eyeHeight, 0.0))) continue
            bestSq = dSq
            best = other
        }
        entity.memory.set(MemoryKeys.NEAREST_PLAYER, best)
    }
}

class ClusterCountSensor(
    private val outputKey: MemoryKey<Int> = MemoryKeys.NEARBY_PLAYER_COUNT,
    private val range: Double = 24.0,
    private val playersOnly: Boolean = true,
    override val period: Int = 40,
) : Sensor {
    override fun sense(entity: SmartEntity) {
        val instance = entity.instance ?: return
        var count = 0
        for (other in instance.getNearbyEntities(entity.position, range)) {
            if (other === entity || other.isRemoved) continue
            if (playersOnly && other !is Player) continue
            count++
        }
        entity.memory.set(outputKey, count)
    }
}

class ThreatTargetSensor(
    private val targetKey: MemoryKey<Entity> = MemoryKeys.ATTACK_TARGET,
    private val fallbackRange: Double = 32.0,
    private val playersOnlyFallback: Boolean = true,
    override val period: Int = 10,
) : Sensor {
    override fun sense(entity: SmartEntity) {
        val table = entity.threatTable
        if (table != null) {
            table.pruneInvalid()
            val current = entity.memory.get(targetKey)
            if (current != null && current.isRemoved) entity.memory.clear(targetKey)
            val top = table.highest()
            if (top != null && !top.isRemoved) {
                entity.memory.set(targetKey, top)
                return
            }
        }
        if (fallbackRange <= 0.0) return
        val instance = entity.instance ?: return
        val pos = entity.position
        var best: Entity? = null
        var bestSq = Double.MAX_VALUE
        for (other in instance.getNearbyEntities(pos, fallbackRange)) {
            if (other === entity || other.isRemoved) continue
            if (playersOnlyFallback && other !is Player) continue
            val dSq = other.position.distanceSquared(pos)
            if (dSq < bestSq) {
                bestSq = dSq
                best = other
            }
        }
        if (best != null) entity.memory.set(targetKey, best)
    }
}
