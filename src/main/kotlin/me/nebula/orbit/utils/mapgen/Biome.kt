package me.nebula.orbit.utils.mapgen

import net.minestom.server.MinecraftServer
import net.minestom.server.color.Color
import net.minestom.server.instance.block.Block
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.biome.Biome
import net.minestom.server.world.biome.BiomeEffects
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

enum class TreeType { OAK, BIRCH, SPRUCE, DARK_OAK, ACACIA, JUNGLE }

enum class HeightCurve {
    LINEAR,
    SMOOTH,
    TERRACE,
    AMPLIFIED,
    CLIFF,
    RIDGED,
    MESA,
    ROLLING,
}

enum class GrassModifier { NONE, DARK_FOREST, SWAMP }

enum class ZoneShape { CIRCLE, SQUARE }

data class BiomeDefinition(
    val id: String,
    val surfaceBlock: Block,
    val fillerBlock: Block,
    val underwaterSurface: Block,
    val stoneBlock: Block = Block.STONE,
    val baseHeight: Double,
    val heightVariation: Double,
    val heightCurve: HeightCurve = HeightCurve.LINEAR,
    val temperature: Double = 0.5,
    val moisture: Double = 0.5,
    val treeDensity: Double = 0.0,
    val vegetationDensity: Double = 0.0,
    val treeTypes: List<TreeType> = listOf(TreeType.OAK),
    val caveFrequencyMultiplier: Double = 1.0,
    val oreMultiplier: Double = 1.0,
    val snowLine: Int = Int.MAX_VALUE,
    val frozen: Boolean = false,
    val hasPrecipitation: Boolean = true,
    val waterColor: Int = 0x3F76E4,
    val grassColor: Int? = null,
    val foliageColor: Int? = null,
    val grassModifier: GrassModifier = GrassModifier.NONE,
)

object BiomeRegistry {

    private val biomes = ConcurrentHashMap<String, BiomeDefinition>()
    private val registryKeys = ConcurrentHashMap<String, RegistryKey<Biome>>()

    fun register(biome: BiomeDefinition) {
        biomes[biome.id] = biome
    }

    operator fun get(id: String): BiomeDefinition? = biomes[id]

    fun require(id: String): BiomeDefinition =
        requireNotNull(biomes[id]) { "Biome '$id' not registered" }

    fun all(): Collection<BiomeDefinition> = biomes.values

    fun clear() = biomes.clear()

    fun registerDefaults() {
        clear()
        DEFAULTS.forEach { register(it) }
    }

    fun registerMinestomBiomes() {
        val registry = MinecraftServer.getBiomeRegistry()
        registryKeys.clear()
        for (def in biomes.values) {
            val effects = BiomeEffects.builder()
                .waterColor(Color(def.waterColor))
                .also { b ->
                    def.grassColor?.let { b.grassColor(Color(it)) }
                    def.foliageColor?.let { b.foliageColor(Color(it)) }
                }
                .grassColorModifier(when (def.grassModifier) {
                    GrassModifier.NONE -> BiomeEffects.GrassColorModifier.NONE
                    GrassModifier.DARK_FOREST -> BiomeEffects.GrassColorModifier.DARK_FOREST
                    GrassModifier.SWAMP -> BiomeEffects.GrassColorModifier.SWAMP
                })
                .build()

            val biome = Biome.builder()
                .precipitation(def.hasPrecipitation)
                .temperature(def.temperature.toFloat())
                .downfall(def.moisture.toFloat())
                .effects(effects)
                .build()

            registryKeys[def.id] = registry.register("nebula:${def.id}", biome)
        }
    }

    fun getRegistryKey(biomeId: String): RegistryKey<Biome>? = registryKeys[biomeId]

    val DEFAULTS: List<BiomeDefinition> = listOf(
        BiomeDefinition("plains", Block.GRASS_BLOCK, Block.DIRT, Block.SAND,
            baseHeight = 64.0, heightVariation = 6.0, temperature = 0.5, moisture = 0.5,
            treeDensity = 0.01, vegetationDensity = 0.3, treeTypes = listOf(TreeType.OAK)),

        BiomeDefinition("forest", Block.GRASS_BLOCK, Block.DIRT, Block.SAND,
            baseHeight = 66.0, heightVariation = 8.0, temperature = 0.5, moisture = 0.7,
            treeDensity = 0.15, vegetationDensity = 0.4, treeTypes = listOf(TreeType.OAK, TreeType.BIRCH)),

        BiomeDefinition("birch_forest", Block.GRASS_BLOCK, Block.DIRT, Block.SAND,
            baseHeight = 66.0, heightVariation = 7.0, temperature = 0.4, moisture = 0.6,
            treeDensity = 0.14, vegetationDensity = 0.35, treeTypes = listOf(TreeType.BIRCH)),

        BiomeDefinition("dark_forest", Block.GRASS_BLOCK, Block.DIRT, Block.DIRT,
            baseHeight = 67.0, heightVariation = 6.0, temperature = 0.5, moisture = 0.8,
            treeDensity = 0.25, vegetationDensity = 0.3, treeTypes = listOf(TreeType.DARK_OAK, TreeType.OAK),
            grassModifier = GrassModifier.DARK_FOREST),

        BiomeDefinition("desert", Block.SAND, Block.SANDSTONE, Block.SAND,
            baseHeight = 63.0, heightVariation = 4.0, temperature = 0.9, moisture = 0.1,
            treeDensity = 0.002, vegetationDensity = 0.05,
            hasPrecipitation = false, foliageColor = 0xAEA42A),

        BiomeDefinition("taiga", Block.GRASS_BLOCK, Block.PODZOL, Block.GRAVEL,
            baseHeight = 68.0, heightVariation = 10.0, temperature = 0.2, moisture = 0.5,
            treeDensity = 0.12, vegetationDensity = 0.2, treeTypes = listOf(TreeType.SPRUCE),
            waterColor = 0x287082, grassColor = 0x86B783, foliageColor = 0x68A55F),

        BiomeDefinition("swamp", Block.GRASS_BLOCK, Block.DIRT, Block.CLAY,
            baseHeight = 62.0, heightVariation = 3.0, temperature = 0.6, moisture = 0.9,
            treeDensity = 0.05, vegetationDensity = 0.5, treeTypes = listOf(TreeType.OAK),
            caveFrequencyMultiplier = 0.7,
            waterColor = 0x617B64, grassColor = 0x6A7039, foliageColor = 0x6A7039,
            grassModifier = GrassModifier.SWAMP),

        BiomeDefinition("mountains", Block.STONE, Block.STONE, Block.GRAVEL,
            baseHeight = 80.0, heightVariation = 30.0, heightCurve = HeightCurve.AMPLIFIED,
            temperature = 0.2, moisture = 0.3,
            treeDensity = 0.02, vegetationDensity = 0.05, treeTypes = listOf(TreeType.SPRUCE),
            snowLine = 95),

        BiomeDefinition("savanna", Block.GRASS_BLOCK, Block.DIRT, Block.SAND,
            baseHeight = 65.0, heightVariation = 5.0, temperature = 0.8, moisture = 0.3,
            treeDensity = 0.03, vegetationDensity = 0.15, treeTypes = listOf(TreeType.ACACIA)),

        BiomeDefinition("jungle", Block.GRASS_BLOCK, Block.DIRT, Block.SAND,
            baseHeight = 65.0, heightVariation = 8.0, temperature = 0.8, moisture = 0.9,
            treeDensity = 0.3, vegetationDensity = 0.6, treeTypes = listOf(TreeType.JUNGLE, TreeType.OAK),
            grassColor = 0x59C93C, foliageColor = 0x30BB0B),

        BiomeDefinition("snowy_plains", Block.SNOW_BLOCK, Block.DIRT, Block.GRAVEL,
            baseHeight = 64.0, heightVariation = 5.0, temperature = 0.0, moisture = 0.4,
            treeDensity = 0.005, vegetationDensity = 0.05, treeTypes = listOf(TreeType.SPRUCE),
            frozen = true, snowLine = 0,
            waterColor = 0x3D57D6),

        BiomeDefinition("badlands", Block.ORANGE_TERRACOTTA, Block.ORANGE_TERRACOTTA, Block.RED_SAND,
            stoneBlock = Block.TERRACOTTA,
            baseHeight = 70.0, heightVariation = 15.0, heightCurve = HeightCurve.TERRACE,
            temperature = 0.9, moisture = 0.0,
            treeDensity = 0.0, vegetationDensity = 0.02, caveFrequencyMultiplier = 1.3,
            hasPrecipitation = false, grassColor = 0x90814D, foliageColor = 0x9E814D),

        BiomeDefinition("flower_plains", Block.GRASS_BLOCK, Block.DIRT, Block.SAND,
            baseHeight = 64.0, heightVariation = 4.0, temperature = 0.5, moisture = 0.6,
            treeDensity = 0.005, vegetationDensity = 0.7, treeTypes = listOf(TreeType.OAK, TreeType.BIRCH)),

        BiomeDefinition("ice_spikes", Block.SNOW_BLOCK, Block.PACKED_ICE, Block.GRAVEL,
            baseHeight = 64.0, heightVariation = 4.0, temperature = 0.0, moisture = 0.5,
            treeDensity = 0.0, vegetationDensity = 0.0, frozen = true, snowLine = 0,
            waterColor = 0x3D57D6),

        BiomeDefinition("stony_peaks", Block.STONE, Block.STONE, Block.GRAVEL,
            baseHeight = 90.0, heightVariation = 35.0, heightCurve = HeightCurve.CLIFF,
            temperature = 0.3, moisture = 0.2,
            treeDensity = 0.0, vegetationDensity = 0.02, snowLine = 100,
            caveFrequencyMultiplier = 1.5, oreMultiplier = 1.3),

        BiomeDefinition("meadow", Block.GRASS_BLOCK, Block.DIRT, Block.SAND,
            baseHeight = 72.0, heightVariation = 6.0, heightCurve = HeightCurve.SMOOTH,
            temperature = 0.4, moisture = 0.6,
            treeDensity = 0.01, vegetationDensity = 0.5, treeTypes = listOf(TreeType.OAK),
            waterColor = 0x0E4ECF),
    )
}

data class BiomeZoneConfig(
    val centerBiomeId: String? = null,
    val centerRadius: Int = 0,
    val ringBiomeId: String? = null,
    val ringRadius: Int = 0,
    val zoneShape: ZoneShape = ZoneShape.CIRCLE,
    val excludedBiomeIds: Set<String> = emptySet(),
    val fallbackBiomeId: String = "plains",
    val biomeScale: Double = 0.003,
    val blendRadius: Int = 8,
)

class BiomeProvider(
    seed: Long,
    val config: BiomeZoneConfig = BiomeZoneConfig(),
) {

    private val temperatureNoise = OctaveNoise(PerlinNoise(seed + 1000), octaves = 4)
    private val moistureNoise = OctaveNoise(PerlinNoise(seed + 2000), octaves = 4)
    private val weirdnessNoise = OctaveNoise(PerlinNoise(seed + 3000), octaves = 3, lacunarity = 3.0, persistence = 0.3)

    private val centerBiome = config.centerBiomeId?.let { BiomeRegistry[it] }
    private val ringBiome = config.ringBiomeId?.let { BiomeRegistry[it] }
    private val fallback = BiomeRegistry.require(config.fallbackBiomeId)

    fun biomeAt(x: Int, z: Int): BiomeDefinition {
        if (centerBiome != null && config.centerRadius > 0) {
            if (distanceFromCenter(x, z) <= config.centerRadius) return centerBiome
        }
        if (ringBiome != null && config.ringRadius > 0) {
            val dist = distanceFromCenter(x, z)
            if (dist > config.centerRadius && dist <= config.ringRadius) return ringBiome
        }

        val scale = config.biomeScale
        val temp = temperatureNoise.sample2D(x * scale, z * scale)
        val moist = moistureNoise.sample2D(x * scale, z * scale)
        val weird = weirdnessNoise.sample2D(x * scale * 2, z * scale * 2)

        val biome = selectBiome(temp, moist, weird)
        if (config.excludedBiomeIds.contains(biome.id)) return fallback
        return biome
    }

    fun blendedHeight(
        x: Int,
        z: Int,
        heightFn: (Int, Int, BiomeDefinition) -> Int,
    ): Pair<Int, BiomeDefinition> {
        val center = biomeAt(x, z)
        if (config.blendRadius <= 0) return heightFn(x, z, center) to center

        var totalHeight = 0.0
        var totalWeight = 0.0
        val r = config.blendRadius
        val step = (r / 2).coerceAtLeast(2)

        for (dx in -r..r step step) {
            for (dz in -r..r step step) {
                val b = biomeAt(x + dx, z + dz)
                val h = heightFn(x, z, b)
                val dist = sqrt((dx * dx + dz * dz).toDouble())
                val t = (dist / r).coerceIn(0.0, 1.0)
                val it = 1.0 - t
                val w = it * it * (1.0 + 2.0 * t)
                totalHeight += h * w
                totalWeight += w
            }
        }

        return (totalHeight / totalWeight).toInt() to center
    }

    private fun distanceFromCenter(x: Int, z: Int): Double = when (config.zoneShape) {
        ZoneShape.CIRCLE -> sqrt((x.toDouble() * x + z.toDouble() * z))
        ZoneShape.SQUARE -> max(abs(x), abs(z)).toDouble()
    }

    private fun selectBiome(temperature: Double, moisture: Double, weirdness: Double): BiomeDefinition {
        val all = BiomeRegistry.all()
        if (all.isEmpty()) return fallback

        var best: BiomeDefinition = fallback
        var bestDist = Double.MAX_VALUE

        for (biome in all) {
            val dt = temperature - (biome.temperature * 2 - 1)
            val dm = moisture - (biome.moisture * 2 - 1)
            val dw = weirdness * 0.3
            val dist = dt * dt + dm * dm + dw * dw
            if (dist < bestDist) {
                bestDist = dist
                best = biome
            }
        }
        return best
    }
}
