package me.nebula.orbit.utils.customcontent.biome

import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.mapgen.BiomeRegistry

object CustomBiomeLoader {

    private val logger = logger("CustomBiomeLoader")
    private val gson = GsonProvider.default

    fun loadAll(resources: ResourceManager, dir: String): Int {
        val files = runCatching { resources.list(dir, "json", recursive = true) }
            .getOrDefault(emptyList())

        var count = 0
        for (path in files) {
            val bytes = resources.readBytes(path)
            val json = runCatching { gson.fromJson(bytes.toString(Charsets.UTF_8), CustomBiomeJson::class.java) }
                .getOrElse { e ->
                    logger.warn { "Failed to parse biome at $path: ${e.message}" }
                    continue
                }

            val def = runCatching { json.toDefinition() }
                .getOrElse { e ->
                    logger.warn { "Failed to materialize biome '${json.id}' from $path: ${e.message}" }
                    continue
                }

            BiomeRegistry.register(def)
            count++
            logger.info { "Registered custom biome: ${def.id}" }
        }
        return count
    }
}
