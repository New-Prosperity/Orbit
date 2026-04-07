package me.nebula.orbit.mutator

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModules
import net.minestom.server.instance.Instance
import java.util.concurrent.ConcurrentHashMap

object MutatorEngine {

    private val logger = logger("MutatorEngine")
    private val activeByInstance = ConcurrentHashMap<Long, List<Mutator>>()

    fun resolveForGame(): List<Mutator> {
        val mutatorIds = Orbit.activeMutatorIds
        val explicit = MutatorRegistry.resolve(mutatorIds)

        val randomCount = Orbit.randomMutatorCount
        val random = if (randomCount > 0) {
            val explicitIds = explicit.map { it.id }.toSet()
            MutatorRegistry.selectRandom(randomCount).filter { it.id !in explicitIds }
        } else emptyList()

        val combined = explicit + random
        if (combined.isNotEmpty()) {
            logger.info { "Resolved mutators: ${combined.map { it.id }}" }
        }
        return combined
    }

    fun apply(instance: Instance, mutators: List<Mutator>) {
        if (mutators.isEmpty()) return

        val key = instanceKey(instance)
        activeByInstance[key] = mutators

        val allDisables = mutableSetOf<String>()
        val mergedOverrides = mutableMapOf<String, MutableMap<String, Any>>()

        for (mutator in mutators) {
            allDisables += mutator.disables
            for ((moduleId, params) in mutator.overrides) {
                if (moduleId == "__gamemode__") continue
                mergedOverrides.getOrPut(moduleId) { mutableMapOf() }.putAll(params)
            }
        }

        for (moduleId in allDisables) {
            VanillaModules.disable(instance, moduleId)
            logger.info { "Mutators disabled module '$moduleId'" }
        }

        for ((moduleId, params) in mergedOverrides) {
            if (moduleId in allDisables) continue

            val existing = VanillaModules.getActive(instance, moduleId)
            if (existing != null) {
                for ((paramKey, value) in params) {
                    existing.config.set(paramKey, value)
                }
                VanillaModules.disable(instance, moduleId)
                VanillaModules.enable(instance, moduleId, existing.config)
            } else {
                val config = ModuleConfig.of(*params.map { it.key to it.value }.toTypedArray())
                VanillaModules.enable(instance, moduleId, config)
            }
            logger.info { "Applied mutator overrides to '$moduleId': $params" }
        }
    }

    fun activeFor(instance: Instance): List<Mutator> =
        activeByInstance[instanceKey(instance)] ?: emptyList()

    fun cleanup(instance: Instance) {
        activeByInstance.remove(instanceKey(instance))
    }

    fun gameModeOverrides(mutators: List<Mutator>): Map<String, Any> =
        mutators.flatMap { it.overrides["__gamemode__"]?.entries ?: emptySet() }
            .associate { it.key to it.value }

    fun hasOverride(mutators: List<Mutator>, key: String): Boolean =
        gameModeOverrides(mutators).containsKey(key)

    fun <T> getOverride(mutators: List<Mutator>, key: String, default: T): T {
        @Suppress("UNCHECKED_CAST")
        return gameModeOverrides(mutators)[key] as? T ?: default
    }

    private fun instanceKey(instance: Instance): Long =
        System.identityHashCode(instance).toLong()
}
