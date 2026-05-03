package me.nebula.orbit.utils.entitybuilder

import net.minestom.server.entity.Entity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ThreatTable {

    private val threats = ConcurrentHashMap<UUID, Float>()
    private val attackers = ConcurrentHashMap<UUID, Entity>()

    fun add(attacker: Entity, amount: Float) {
        if (amount <= 0f) return
        threats.merge(attacker.uuid, amount) { a, b -> a + b }
        attackers[attacker.uuid] = attacker
    }

    fun set(attacker: Entity, amount: Float) {
        threats[attacker.uuid] = amount
        attackers[attacker.uuid] = attacker
    }

    fun remove(uuid: UUID) {
        threats.remove(uuid)
        attackers.remove(uuid)
    }

    fun get(uuid: UUID): Float = threats[uuid] ?: 0f

    fun decay(delta: Float) {
        if (delta <= 0f) return
        val iter = threats.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val next = entry.value - delta
            if (next <= 0f) {
                attackers.remove(entry.key)
                iter.remove()
            } else {
                entry.setValue(next)
            }
        }
    }

    fun highest(): Entity? {
        var bestUuid: UUID? = null
        var bestValue = Float.NEGATIVE_INFINITY
        for ((uuid, value) in threats) {
            if (value > bestValue) {
                bestValue = value
                bestUuid = uuid
            }
        }
        return bestUuid?.let { attackers[it] }
    }

    fun pruneInvalid() {
        val iter = attackers.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.isRemoved) {
                threats.remove(entry.key)
                iter.remove()
            }
        }
    }

    fun snapshot(): Map<UUID, Float> = threats.toMap()

    fun size(): Int = threats.size

    fun isEmpty(): Boolean = threats.isEmpty()

    fun clear() {
        threats.clear()
        attackers.clear()
    }
}
