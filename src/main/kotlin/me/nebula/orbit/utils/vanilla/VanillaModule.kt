package me.nebula.orbit.utils.vanilla

import me.nebula.ether.utils.logging.logger
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Instance
import java.util.concurrent.ConcurrentHashMap

sealed interface ConfigParam<T> {
    val key: String
    val description: String
    val default: T

    fun parse(input: String): T?
    fun format(value: T): String

    data class BoolParam(override val key: String, override val description: String, override val default: Boolean) : ConfigParam<Boolean> {
        override fun parse(input: String): Boolean? = input.toBooleanStrictOrNull()
        override fun format(value: Boolean): String = value.toString()
    }

    data class IntParam(override val key: String, override val description: String, override val default: Int, val min: Int = Int.MIN_VALUE, val max: Int = Int.MAX_VALUE) : ConfigParam<Int> {
        override fun parse(input: String): Int? = input.toIntOrNull()?.takeIf { it in min..max }
        override fun format(value: Int): String = value.toString()
    }

    data class DoubleParam(override val key: String, override val description: String, override val default: Double, val min: Double = Double.MIN_VALUE, val max: Double = Double.MAX_VALUE) : ConfigParam<Double> {
        override fun parse(input: String): Double? = input.toDoubleOrNull()?.takeIf { it in min..max }
        override fun format(value: Double): String = "%.2f".format(value)
    }
}

interface VanillaModule {
    val id: String
    val description: String
    val configParams: List<ConfigParam<*>> get() = emptyList()
    fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event>
}

class ModuleConfig(private val values: MutableMap<String, Any> = mutableMapOf()) {

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, default: T): T =
        (values[key] as? T) ?: default

    fun getDouble(key: String, default: Double): Double =
        (values[key] as? Number)?.toDouble() ?: default

    fun getInt(key: String, default: Int): Int =
        (values[key] as? Number)?.toInt() ?: default

    fun getBoolean(key: String, default: Boolean): Boolean =
        values[key] as? Boolean ?: default

    fun set(key: String, value: Any) {
        values[key] = value
    }

    fun toMap(): Map<String, Any> = values.toMap()

    companion object {
        val EMPTY = ModuleConfig()

        fun of(vararg pairs: Pair<String, Any>): ModuleConfig =
            ModuleConfig(pairs.toMap().toMutableMap())

        fun fromDefaults(module: VanillaModule): ModuleConfig {
            val map = mutableMapOf<String, Any>()
            for (param in module.configParams) {
                map[param.key] = param.default as Any
            }
            return ModuleConfig(map)
        }
    }
}

data class ActiveModule(
    val module: VanillaModule,
    val config: ModuleConfig,
    val node: EventNode<Event>,
)

private val logger = logger("VanillaModules")

object VanillaModules {

    private val registry = LinkedHashMap<String, VanillaModule>()
    private val active = ConcurrentHashMap<Long, ConcurrentHashMap<String, ActiveModule>>()

    fun register(module: VanillaModule) {
        registry[module.id] = module
    }

    fun registerAll() {
        for (module in ALL_VANILLA_MODULES) register(module)
    }

    fun get(id: String): VanillaModule? = registry[id]

    fun all(): Collection<VanillaModule> = registry.values

    fun enable(instance: Instance, id: String, config: ModuleConfig = ModuleConfig.EMPTY) {
        val module = registry[id] ?: run {
            logger.warn { "Unknown vanilla module: $id" }
            return
        }
        val key = instanceKey(instance)
        val modules = active.getOrPut(key) { ConcurrentHashMap() }
        if (modules.containsKey(id)) {
            disable(instance, id)
        }
        val effectiveConfig = if (config === ModuleConfig.EMPTY) ModuleConfig.fromDefaults(module) else config
        val node = module.createNode(instance, effectiveConfig)
        MinecraftServer.getGlobalEventHandler().addChild(node)
        modules[id] = ActiveModule(module, effectiveConfig, node)
        logger.info { "Enabled vanilla module '$id'" }
    }

    fun disable(instance: Instance, id: String) {
        val modules = active[instanceKey(instance)] ?: return
        val am = modules.remove(id) ?: return
        MinecraftServer.getGlobalEventHandler().removeChild(am.node)
        logger.info { "Disabled vanilla module '$id'" }
    }

    fun disableAll(instance: Instance) {
        val modules = active.remove(instanceKey(instance)) ?: return
        for ((_, am) in modules) {
            MinecraftServer.getGlobalEventHandler().removeChild(am.node)
        }
    }

    fun isEnabled(instance: Instance, id: String): Boolean =
        active[instanceKey(instance)]?.containsKey(id) == true

    fun enabledFor(instance: Instance): Map<String, ActiveModule> =
        active[instanceKey(instance)]?.toMap() ?: emptyMap()

    fun getActive(instance: Instance, id: String): ActiveModule? =
        active[instanceKey(instance)]?.get(id)

    fun reconfigure(instance: Instance, id: String, key: String, value: Any) {
        val am = getActive(instance, id) ?: return
        am.config.set(key, value)
        disable(instance, id)
        enable(instance, id, am.config)
    }

    fun registeredIds(): Set<String> = registry.keys.toSet()

    private fun instanceKey(instance: Instance): Long =
        System.identityHashCode(instance).toLong()
}
