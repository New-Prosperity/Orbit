package me.nebula.orbit.rules

import me.nebula.gravity.config.ConfigCatalog
import java.util.concurrent.ConcurrentHashMap

object RuleRegistry {

    private val keys = ConcurrentHashMap<String, RuleKey<*>>()

    fun <T : Any> register(id: String, default: T, scope: RuleScope = RuleScope.INSTANCE): RuleKey<T> {
        val existing = keys[id]
        if (existing != null) {
            @Suppress("UNCHECKED_CAST")
            return existing as RuleKey<T>
        }
        val key = RuleKey(id, default, scope)
        keys[id] = key
        val entry = RuleConfigBridge.asConfigEntry(key)
        if (entry != null) ConfigCatalog.register(entry)
        return key
    }

    fun resolve(id: String): RuleKey<*>? = keys[id]

    fun all(): Collection<RuleKey<*>> = keys.values
}
