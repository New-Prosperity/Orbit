package me.nebula.orbit.utils.entitybuilder

import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player

interface Sensor {
    val period: Int get() = 20
    fun sense(entity: SmartEntity)
}

class NearestPlayerSensor(
    private val range: Double = 32.0,
    private val minRange: Double = 0.0,
    override val period: Int = 20,
) : Sensor {
    override fun sense(entity: SmartEntity) {
        val instance = entity.instance ?: return
        val pos = entity.position
        val nearest = instance.getNearbyEntities(pos, range)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.position.distance(pos) >= minRange }
            .minByOrNull { it.position.distanceSquared(pos) }
        entity.memory.set(MemoryKeys.NEAREST_PLAYER, nearest)
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
        val nearest = instance.getNearbyEntities(pos, range)
            .asSequence()
            .filter { it !== entity && !it.isRemoved && predicate(it) }
            .minByOrNull { it.position.distanceSquared(pos) }
        entity.memory.set(target, nearest)
    }
}
