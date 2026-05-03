package me.nebula.orbit.utils.customcontent.decoration

import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.mapgen.planet.OreClusterShape
import me.nebula.orbit.utils.mapgen.planet.OreClusterShapeRegistry

private data class OreClusterShapeJson(
    val id: String,
    val offsets: List<List<Int>> = emptyList(),
) {
    fun toShape(): OreClusterShape = OreClusterShape(
        id = id,
        offsets = offsets.mapNotNull {
            if (it.size != 3) null else Triple(it[0], it[1], it[2])
        },
    )
}

object CustomOreClusterLoader {

    private val logger = logger("CustomOreClusterLoader")
    private val gson = GsonProvider.default

    fun loadAll(resources: ResourceManager, dir: String): Int {
        val files = runCatching { resources.list(dir, "json", recursive = true) }
            .getOrDefault(emptyList())
        var count = 0
        for (path in files) {
            val bytes = resources.readBytes(path)
            val json = runCatching { gson.fromJson(bytes.toString(Charsets.UTF_8), OreClusterShapeJson::class.java) }
                .getOrElse { e ->
                    logger.warn { "Failed to parse ore cluster at $path: ${e.message}" }
                    continue
                }
            OreClusterShapeRegistry.register(json.toShape())
            count++
            logger.info { "Registered ore cluster shape: ${json.id}" }
        }
        return count
    }
}
