package me.nebula.orbit.utils.customcontent.decoration

import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.mapgen.planet.decoration.TreeBlockKind
import me.nebula.orbit.utils.mapgen.planet.decoration.TreeShape
import me.nebula.orbit.utils.mapgen.planet.decoration.TreeShapeBlock
import me.nebula.orbit.utils.mapgen.planet.decoration.TreeShapeRegistry

private data class TreeShapeBlockJson(
    val dx: Int = 0,
    val dy: Int = 0,
    val dz: Int = 0,
    val kind: TreeBlockKind = TreeBlockKind.LEAVES,
    val block: String? = null,
)

private data class TreeShapeJson(
    val id: String,
    val trunkBlock: String = "minecraft:oak_log",
    val leavesBlock: String = "minecraft:oak_leaves",
    val blocks: List<TreeShapeBlockJson> = emptyList(),
    val variants: List<List<TreeShapeBlockJson>>? = null,
) {
    fun toShape(): TreeShape = TreeShape(
        id = id,
        trunkBlock = trunkBlock,
        leavesBlock = leavesBlock,
        blocks = blocks.map { it.toEntry() },
        variants = variants?.map { v -> v.map { it.toEntry() } } ?: emptyList(),
    )
}

private fun TreeShapeBlockJson.toEntry(): TreeShapeBlock =
    TreeShapeBlock(dx, dy, dz, kind, block)

object CustomTreeShapeLoader {

    private val logger = logger("CustomTreeShapeLoader")
    private val gson = GsonProvider.default

    fun loadAll(resources: ResourceManager, dir: String): Int {
        val files = runCatching { resources.list(dir, "json", recursive = true) }
            .getOrDefault(emptyList())
        var count = 0
        for (path in files) {
            val bytes = resources.readBytes(path)
            val json = runCatching { gson.fromJson(bytes.toString(Charsets.UTF_8), TreeShapeJson::class.java) }
                .getOrElse { e ->
                    logger.warn { "Failed to parse tree shape at $path: ${e.message}" }
                    continue
                }
            TreeShapeRegistry.register(json.toShape())
            count++
            logger.info { "Registered tree shape: ${json.id}" }
        }
        return count
    }
}
