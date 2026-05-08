package me.nebula.orbit.utils.mapgen

import me.nebula.orbit.utils.mapgen.planet.rhexor.RhexorGenerator
import net.kyori.adventure.key.Key
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.Generator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

object GeneratorRegistry {

    fun interface GeneratorFactory {
        fun create(params: Map<String, String>): Generator
    }

    private val factories = ConcurrentHashMap<String, GeneratorFactory>()

    fun register(name: String, factory: GeneratorFactory) {
        factories[name.lowercase()] = factory
    }

    fun create(name: String, params: Map<String, String> = emptyMap()): Generator? =
        factories[name.lowercase()]?.create(params)

    fun createOrThrow(name: String, params: Map<String, String> = emptyMap()): Generator =
        create(name, params) ?: error(
            "Unknown generator '$name'. Available: ${factories.keys.sorted().joinToString(", ")}"
        )

    fun names(): Set<String> = factories.keys.toSet()

    fun has(name: String): Boolean = factories.containsKey(name.lowercase())

    init {
        register("void") { VoidGenerator }

        register("superflat") { params ->
            SuperflatGenerator(
                bedrockHeight = params.intOr("bedrock_height", 1),
                dirtHeight = params.intOr("dirt_height", 3),
                topBlock = blockOrError(params["top_block"] ?: "grass_block"),
                fillerBlock = blockOrError(params["filler_block"] ?: "dirt"),
                bedrockBlock = blockOrError(params["bedrock_block"] ?: "bedrock"),
            )
        }

        register("br_rhexor") { params ->
            RhexorGenerator(seed = params.seedOrRandom())
        }

        register("terrain") { params ->
            TerrainGenerator(
                TerrainConfig(
                    seed = params.seedOrRandom(),
                    seaLevel = params.intOr("sea_level", 62),
                    bedrockHeight = params.intOr("bedrock_height", 1),
                    fillerDepth = params.intOr("filler_depth", 3),
                    deepslateLevel = params.intOr("deepslate_level", 8),
                    beachesEnabled = params.boolOr("beaches_enabled", true),
                    terrainScale = params.doubleOr("terrain_scale", 0.01),
                    continentalScale = params.doubleOr("continental_scale", 0.002),
                    continentalInfluence = params.doubleOr("continental_influence", 15.0),
                    erosionScale = params.doubleOr("erosion_scale", 0.005),
                    erosionStrength = params.doubleOr("erosion_strength", 0.4),
                    riversEnabled = params.boolOr("rivers_enabled", true),
                    riverScale = params.doubleOr("river_scale", 0.004),
                    riverThreshold = params.doubleOr("river_threshold", 0.015),
                    riverDepth = params.intOr("river_depth", 4),
                    riverWidth = params.doubleOr("river_width", 0.02),
                    overhangsEnabled = params.boolOr("overhangs_enabled", true),
                    overhangScale = params.doubleOr("overhang_scale", 0.03),
                    overhangThreshold = params.doubleOr("overhang_threshold", 0.55),
                ),
            )
        }
    }

    private fun blockOrError(name: String): Block {
        val key = if (':' in name) name else "minecraft:$name"
        return Block.fromKey(Key.key(key)) ?: error("Unknown block: $name")
    }

    private fun Map<String, String>.intOr(key: String, default: Int): Int =
        get(key)?.toIntOrNull() ?: default

    private fun Map<String, String>.longOr(key: String, default: Long): Long =
        get(key)?.toLongOrNull() ?: default

    private fun Map<String, String>.doubleOr(key: String, default: Double): Double =
        get(key)?.toDoubleOrNull() ?: default

    private fun Map<String, String>.boolOr(key: String, default: Boolean): Boolean =
        get(key)?.toBooleanStrictOrNull() ?: default

    private fun Map<String, String>.seedOrRandom(key: String = "seed"): Long =
        get(key)?.toLongOrNull() ?: ThreadLocalRandom.current().nextLong()
}
