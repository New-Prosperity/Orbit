package me.nebula.orbit.rules

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class GameRules {

    private val values = ConcurrentHashMap<String, Any>()
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(Any, Any) -> Unit>>()

    operator fun <T : Any> get(key: RuleKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        return (values[key.id] as? T) ?: key.default
    }

    operator fun <T : Any> set(key: RuleKey<T>, value: T) {
        val old: Any = values.put(key.id, value) ?: key.default
        if (old != value) fire(key.id, old, value)
    }

    fun setAll(entries: Map<RuleKey<*>, Any>) {
        for ((key, value) in entries) {
            @Suppress("UNCHECKED_CAST")
            this[key as RuleKey<Any>] = value
        }
    }

    fun setById(id: String, value: Any) {
        val key = RuleRegistry.resolve(id) ?: return
        if (!key.default::class.java.isInstance(value)) return
        @Suppress("UNCHECKED_CAST")
        this[key as RuleKey<Any>] = value
    }

    fun reset() {
        val snapshot = values.toMap()
        values.clear()
        for ((id, old) in snapshot) {
            val key = RuleRegistry.resolve(id) ?: continue
            if (old != key.default) fire(id, old, key.default)
        }
    }

    fun <T : Any> onChange(key: RuleKey<T>, listener: (old: T, new: T) -> Unit) {
        val bucket = listeners.computeIfAbsent(key.id) { CopyOnWriteArrayList() }
        @Suppress("UNCHECKED_CAST")
        bucket += listener as (Any, Any) -> Unit
    }

    fun onAnyChange(listener: (id: String, old: Any, new: Any) -> Unit) {
        val bucket = listeners.computeIfAbsent(WILDCARD) { CopyOnWriteArrayList() }
        bucket += { old, new -> listener(WILDCARD, old, new) }
    }

    private fun fire(id: String, old: Any, new: Any) {
        listeners[id]?.forEach { it(old, new) }
        listeners[WILDCARD]?.forEach { it(old, new) }
    }

    private companion object {
        const val WILDCARD = "__any__"
    }
}
