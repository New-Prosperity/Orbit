package me.nebula.orbit.cosmetic

import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.gravity.cosmetic.CosmeticCategory
import java.util.concurrent.ConcurrentHashMap

object CosmeticRegistry {

    private val logger = logger("CosmeticRegistry")
    private val definitions = ConcurrentHashMap<String, CosmeticDefinition>()

    fun register(definition: CosmeticDefinition) {
        definitions[definition.id] = definition
        logger.info { "Registered cosmetic: ${definition.id} (${definition.category}, ${definition.rarity})" }
    }

    operator fun get(id: String): CosmeticDefinition? = definitions[id]

    fun byCategory(category: CosmeticCategory): List<CosmeticDefinition> =
        definitions.values.filter { it.category == category }

    fun all(): Collection<CosmeticDefinition> = definitions.values

    fun loadFromResources(resources: ResourceManager) {
        val catalog = resources.loadOrCopyDefault<Array<CosmeticDefinition>>("cosmetics.json", "cosmetics.json")
        catalog.forEach { register(it) }
        logger.info { "Loaded ${catalog.size} cosmetics from catalog" }
    }
}
