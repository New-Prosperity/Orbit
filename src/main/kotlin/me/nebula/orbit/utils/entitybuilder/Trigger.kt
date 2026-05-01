package me.nebula.orbit.utils.entitybuilder

import net.minestom.server.entity.Entity
import net.minestom.server.entity.damage.Damage
import java.util.concurrent.ConcurrentHashMap

class TriggerType<T>(val name: String) {
    override fun equals(other: Any?): Boolean = other is TriggerType<*> && name == other.name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = "TriggerType($name)"
}

object Triggers {
    private val byName = ConcurrentHashMap<String, TriggerType<*>>()

    val ON_DAMAGED = register(TriggerType<Damage>("on_damaged"))
    val ON_TARGET_SPOTTED = register(TriggerType<Entity>("on_target_spotted"))
    val ON_TARGET_LOST = register(TriggerType<Unit>("on_target_lost"))
    val ON_PHASE_CHANGED = register(TriggerType<Int>("on_phase_changed"))
    val ON_LOW_HEALTH = register(TriggerType<Float>("on_low_health"))
    val ON_ALLY_DAMAGED = register(TriggerType<Entity>("on_ally_damaged"))
    val ON_SHIELD_BROKEN = register(TriggerType<Unit>("on_shield_broken"))
    val ON_COMBAT_ENTER = register(TriggerType<Unit>("on_combat_enter"))
    val ON_COMBAT_EXIT = register(TriggerType<Unit>("on_combat_exit"))
    val ON_KILLED_TARGET = register(TriggerType<Entity>("on_killed_target"))

    fun <T> register(t: TriggerType<T>): TriggerType<T> {
        byName[t.name] = t
        return t
    }

    fun byName(name: String): TriggerType<*>? = byName[name]

    fun all(): Collection<TriggerType<*>> = byName.values
}
