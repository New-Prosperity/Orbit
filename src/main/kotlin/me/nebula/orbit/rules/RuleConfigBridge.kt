package me.nebula.orbit.rules

import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.config.ConfigEntry
import me.nebula.gravity.config.ConfigScope
import me.nebula.gravity.config.ConfigSerializer
import me.nebula.gravity.config.ConfigSerializers
import me.nebula.gravity.config.ConfigStore
import me.nebula.gravity.config.ConfigValueType
import java.util.concurrent.ConcurrentHashMap

object RuleConfigBridge {

    private val logger = logger("RuleConfigBridge")
    private val cache = ConcurrentHashMap<String, ConfigEntry<*>>()

    fun <T : Any> asConfigEntry(key: RuleKey<T>): ConfigEntry<T>? {
        @Suppress("UNCHECKED_CAST")
        cache[key.id]?.let { return it as ConfigEntry<T> }

        val targetScope = when (key.scope) {
            RuleScope.INSTANCE -> return null
            RuleScope.GAMEMODE -> ConfigScope.GAME_MODE
            RuleScope.NETWORK -> ConfigScope.NETWORK
        }
        val (type, serializer) = inferTypedSerializer(key.default)
            ?: run {
                logger.warn { "Rule '${key.id}' has unsupported default type ${key.default.javaClass.simpleName}; not bridging" }
                return null
            }
        @Suppress("UNCHECKED_CAST")
        val entry = ConfigEntry(
            key = "rule.${key.id}",
            scope = targetScope,
            type = type,
            default = key.default,
            serializer = serializer as ConfigSerializer<T>,
            category = "gameplay",
            subCategory = "rules",
            tags = setOf("rule", key.id),
            displayNameKey = "orbit.rule.${key.id}.name".asTranslationKey(),
            descriptionKey = "orbit.rule.${key.id}.description".asTranslationKey(),
        )
        cache[key.id] = entry
        return entry
    }

    fun <T : Any> resolveDefault(key: RuleKey<T>, qualifier: String? = null): T {
        val entry = asConfigEntry(key) ?: return key.default
        return if (qualifier == null && entry.scope.qualifierKind != ConfigScope.QualifierKind.NONE) {
            key.default
        } else {
            ConfigStore.get(entry, qualifier)
        }
    }

    fun bridged(): Collection<ConfigEntry<*>> = cache.values

    private fun inferTypedSerializer(default: Any): Pair<ConfigValueType, ConfigSerializer<*>>? = when (default) {
        is Boolean -> ConfigValueType.BOOL to ConfigSerializers.BOOL
        is Int -> ConfigValueType.INT to ConfigSerializers.INT
        is Long -> ConfigValueType.LONG to ConfigSerializers.LONG
        is Double -> ConfigValueType.DOUBLE to ConfigSerializers.DOUBLE
        is String -> ConfigValueType.STRING to ConfigSerializers.STRING
        else -> null
    }
}
