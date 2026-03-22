package me.nebula.orbit.utils.entitybuilder

import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import java.util.concurrent.ConcurrentHashMap

class MemoryKey<T>(val name: String) {
    override fun equals(other: Any?): Boolean = other is MemoryKey<*> && name == other.name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = "MemoryKey($name)"
}

object MemoryKeys {
    val MOVE_TARGET = MemoryKey<Point>("move_target")
    val LOOK_TARGET = MemoryKey<Point>("look_target")
    val ATTACK_TARGET = MemoryKey<Entity>("attack_target")
    val NEAREST_PLAYER = MemoryKey<Player>("nearest_player")
    val PANIC_TICKS = MemoryKey<Int>("panic_ticks")
    val HOME_POSITION = MemoryKey<Point>("home_position")
    val SPAWN_POSITION = MemoryKey<Point>("spawn_position")
    val LAST_DAMAGE_TIME = MemoryKey<Long>("last_damage_time")
    val LAST_ATTACKER = MemoryKey<Entity>("last_attacker")
    val STRAFE_DIRECTION = MemoryKey<Int>("strafe_direction")
    val LEAP_COOLDOWN = MemoryKey<Int>("leap_cooldown")
    val ABILITY_COOLDOWN = MemoryKey<Int>("ability_cooldown")
    val PHASE = MemoryKey<Int>("phase")
    val SUMMON_COOLDOWN = MemoryKey<Int>("summon_cooldown")
    val SHIELD_ACTIVE = MemoryKey<Boolean>("shield_active")
    val COMBO_COUNT = MemoryKey<Int>("combo_count")
    val CHARGE_TICKS = MemoryKey<Int>("charge_ticks")
}

class MemoryStorage {

    private companion object {
        val EMPTY = Any()
    }

    private val data = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: MemoryKey<T>): T? {
        val value = data[key.name] ?: return null
        return if (value === EMPTY) null else value as T
    }

    fun <T : Any> set(key: MemoryKey<T>, value: T?) {
        if (value == null) data.remove(key.name) else data[key.name] = value
    }

    fun <T> has(key: MemoryKey<T>): Boolean = data.containsKey(key.name)

    fun clear(key: MemoryKey<*>) { data.remove(key.name) }

    fun clearAll() { data.clear() }
}
