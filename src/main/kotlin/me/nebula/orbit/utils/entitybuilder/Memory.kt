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
    private val byName = ConcurrentHashMap<String, MemoryKey<*>>()

    val MOVE_TARGET = register(MemoryKey<Point>("move_target"))
    val LOOK_TARGET = register(MemoryKey<Point>("look_target"))
    val ATTACK_TARGET = register(MemoryKey<Entity>("attack_target"))
    val NEAREST_PLAYER = register(MemoryKey<Player>("nearest_player"))
    val PANIC_TICKS = register(MemoryKey<Int>("panic_ticks"))
    val HOME_POSITION = register(MemoryKey<Point>("home_position"))
    val SPAWN_POSITION = register(MemoryKey<Point>("spawn_position"))
    val LAST_DAMAGE_TIME = register(MemoryKey<Long>("last_damage_time"))
    val LAST_DAMAGE_DEALT_TIME = register(MemoryKey<Long>("last_damage_dealt_time"))
    val LAST_ATTACKER = register(MemoryKey<Entity>("last_attacker"))
    val STRAFE_DIRECTION = register(MemoryKey<Int>("strafe_direction"))
    val LEAP_COOLDOWN = register(MemoryKey<Int>("leap_cooldown"))
    val ABILITY_COOLDOWN = register(MemoryKey<Int>("ability_cooldown"))
    val PHASE = register(MemoryKey<Int>("phase"))
    val SUMMON_COOLDOWN = register(MemoryKey<Int>("summon_cooldown"))
    val SHIELD_ACTIVE = register(MemoryKey<Boolean>("shield_active"))
    val COMBO_COUNT = register(MemoryKey<Int>("combo_count"))
    val CHARGE_TICKS = register(MemoryKey<Int>("charge_ticks"))
    val LAST_KNOWN_POSITION = register(MemoryKey<Point>("last_known_position"))
    val NEARBY_PLAYER_COUNT = register(MemoryKey<Int>("nearby_player_count"))
    val NEARBY_ENTITY_COUNT = register(MemoryKey<Int>("nearby_entity_count"))
    val LAST_TARGET_KILLED = register(MemoryKey<Entity>("last_target_killed"))
    val OWNER = register(MemoryKey<Entity>("owner"))
    val BURST_COUNT = register(MemoryKey<Int>("burst_count"))
    val LAST_LOOK_YAW = register(MemoryKey<Float>("last_look_yaw"))
    val LAST_LOOK_PITCH = register(MemoryKey<Float>("last_look_pitch"))

    private fun <T> register(key: MemoryKey<T>): MemoryKey<T> {
        byName[key.name] = key
        return key
    }

    fun byName(name: String): MemoryKey<*>? = byName[name]
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

    fun hasName(name: String): Boolean = data.containsKey(name)

    fun clear(key: MemoryKey<*>) { data.remove(key.name) }

    fun clearAll() { data.clear() }

    fun snapshot(): Map<String, Any> = data.toMap()

    fun size(): Int = data.size
}
