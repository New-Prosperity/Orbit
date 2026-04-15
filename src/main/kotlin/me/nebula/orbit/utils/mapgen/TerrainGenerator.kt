package me.nebula.orbit.utils.mapgen

import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.biome.Biome
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor

data class TerrainConfig(
    val seed: Long = 0L,
    val seaLevel: Int = 62,
    val bedrockHeight: Int = 1,
    val fillerDepth: Int = 3,
    val deepslateLevel: Int = 8,
    val beachesEnabled: Boolean = true,
    val terrainScale: Double = 0.01,
    val continentalScale: Double = 0.002,
    val continentalInfluence: Double = 15.0,
    val erosionScale: Double = 0.005,
    val erosionStrength: Double = 0.4,
    val riversEnabled: Boolean = true,
    val riverScale: Double = 0.004,
    val riverThreshold: Double = 0.015,
    val riverDepth: Int = 4,
    val riverWidth: Double = 0.02,
    val overhangsEnabled: Boolean = true,
    val overhangScale: Double = 0.03,
    val overhangThreshold: Double = 0.55,
    val biomeZones: BiomeZoneConfig = BiomeZoneConfig(),
)

class TerrainGenerator(private val config: TerrainConfig) : Generator {

    private val heightNoise = OctaveNoise(PerlinNoise(config.seed), octaves = 6)
    private val detailNoise = OctaveNoise(PerlinNoise(config.seed + 100), octaves = 3, lacunarity = 3.0, persistence = 0.3)
    private val continentalNoise = OctaveNoise(PerlinNoise(config.seed + 150), octaves = 4, lacunarity = 2.0, persistence = 0.6)
    private val erosionNoise = OctaveNoise(PerlinNoise(config.seed + 250), octaves = 4, lacunarity = 2.5, persistence = 0.4)
    private val riverNoise = OctaveNoise(PerlinNoise(config.seed + 350), octaves = 4, lacunarity = 2.0, persistence = 0.5)
    private val overhangNoise = OctaveNoise(PerlinNoise(config.seed + 450), octaves = 3, lacunarity = 2.5, persistence = 0.4)
    private val ridgedNoise = RidgedNoise(PerlinNoise(config.seed + 550), octaves = 4)

    private val heightCache = ConcurrentHashMap<Long, Int>()

    val biomes = BiomeProvider(config.seed, config.biomeZones)
    val seaLevel: Int get() = config.seaLevel

    override fun generate(unit: GenerationUnit) {
        val modifier = unit.modifier()
        val startX = unit.absoluteStart().blockX()
        val startZ = unit.absoluteStart().blockZ()
        val endX = unit.absoluteEnd().blockX()
        val endZ = unit.absoluteEnd().blockZ()

        for (x in startX until endX) {
            for (z in startZ until endZ) {
                val (height, biome) = biomes.blendedHeight(x, z) { bx, bz, b -> computeRawHeight(bx, bz, b) }
                val isRiver = config.riversEnabled && isRiverAt(x, z) && height >= config.seaLevel && height < config.seaLevel + 8
                val surfaceY = if (isRiver) config.seaLevel - config.riverDepth else height

                val isBeach = config.beachesEnabled && !isRiver
                    && surfaceY in (config.seaLevel - 1)..(config.seaLevel + 2)
                    && biome.id !in BEACH_EXCLUDED_BIOMES
                val isBadlands = biome.stoneBlock.compare(Block.TERRACOTTA)

                for (y in 0 until config.bedrockHeight) {
                    modifier.setBlock(x, y, z, Block.BEDROCK)
                }

                for (y in config.bedrockHeight..surfaceY) {
                    val block = when {
                        y == surfaceY && !isRiver -> when {
                            isBeach -> Block.SAND
                            surfaceY >= config.seaLevel -> biome.surfaceBlock
                            else -> biome.underwaterSurface
                        }
                        y >= surfaceY - config.fillerDepth -> when {
                            isBeach -> Block.SANDSTONE
                            else -> biome.fillerBlock
                        }
                        isBadlands -> terracottaLayer(y)
                        y < config.deepslateLevel -> Block.DEEPSLATE
                        else -> biome.stoneBlock
                    }
                    modifier.setBlock(x, y, z, block)
                }

                val waterTop = config.seaLevel
                if (surfaceY < waterTop) {
                    val waterBlock = if (biome.frozen && !isRiver) Block.ICE else Block.WATER
                    for (y in (surfaceY + 1) until waterTop) {
                        modifier.setBlock(x, y, z, Block.WATER)
                    }
                    modifier.setBlock(x, waterTop, z, waterBlock)
                }

                if (config.overhangsEnabled && height > config.seaLevel + 10) {
                    for (y in (height + 1)..(height + 15)) {
                        val density = overhangNoise.sample3D(
                            x * config.overhangScale, y * config.overhangScale, z * config.overhangScale,
                        )
                        val heightFade = 1.0 - ((y - height).toDouble() / 15.0)
                        if (density * heightFade > config.overhangThreshold) {
                            modifier.setBlock(x, y, z, biome.stoneBlock)
                        }
                    }
                }

                if (biome.snowLine != Int.MAX_VALUE && surfaceY >= biome.snowLine && surfaceY >= config.seaLevel) {
                    modifier.setBlock(x, surfaceY + 1, z, Block.SNOW)
                }

                val biomeKey = BiomeRegistry.getRegistryKey(biome.id) ?: FALLBACK_BIOME
                val startY = unit.absoluteStart().blockY()
                val endY = unit.absoluteEnd().blockY()
                for (y in startY until endY step 4) {
                    modifier.setBiome(x, y, z, biomeKey)
                }
            }
        }
    }

    fun computeRawHeight(x: Int, z: Int, biome: BiomeDefinition): Int {
        val baseNoise = heightNoise.sample2D(x * config.terrainScale, z * config.terrainScale)
        val detail = detailNoise.sample2D(x * config.terrainScale * 2, z * config.terrainScale * 2) * 0.3
        val continental = continentalNoise.sample2D(x * config.continentalScale, z * config.continentalScale) * config.continentalInfluence
        val erosion = erosionNoise.sample2D(x * config.erosionScale, z * config.erosionScale)
        val erosionFactor = 1.0 - erosion.coerceIn(0.0, 1.0) * config.erosionStrength

        val combinedNoise = when (biome.heightCurve) {
            HeightCurve.LINEAR -> (baseNoise + detail) * erosionFactor
            HeightCurve.SMOOTH -> smoothstep(baseNoise + detail) * erosionFactor
            HeightCurve.TERRACE -> terrace(baseNoise + detail, 6) * erosionFactor
            HeightCurve.AMPLIFIED -> {
                val ridged = ridgedNoise.sample2D(x * config.terrainScale, z * config.terrainScale)
                ((baseNoise * 0.5 + ridged * 0.5) + detail) * erosionFactor
            }
            HeightCurve.CLIFF -> {
                val raw = baseNoise + detail
                if (raw > 0) raw * raw * erosionFactor else raw * erosionFactor
            }
            HeightCurve.RIDGED -> {
                val ridged = ridgedNoise.sample2D(x * config.terrainScale, z * config.terrainScale)
                (ridged + detail * 0.5) * erosionFactor
            }
            HeightCurve.MESA -> {
                val raw = (baseNoise + detail).coerceIn(-1.0, 1.0)
                val flat = when {
                    raw > 0.15 -> 1.0
                    raw < -0.15 -> -1.0
                    else -> raw / 0.15
                }
                flat * erosionFactor
            }
            HeightCurve.ROLLING -> smoothstep(smoothstep(baseNoise + detail)) * erosionFactor
        }

        return (biome.baseHeight + continental + combinedNoise * biome.heightVariation).toInt().coerceIn(2, 250)
    }

    fun isRiverAt(x: Int, z: Int): Boolean {
        val noise = riverNoise.sample2D(x * config.riverScale, z * config.riverScale)
        return abs(noise) < config.riverThreshold
    }

    fun surfaceHeight(x: Int, z: Int): Int =
        heightCache.computeIfAbsent(packCoord(x, z)) {
            val biome = biomes.biomeAt(x, z)
            computeRawHeight(x, z, biome)
        }

    private fun packCoord(x: Int, z: Int): Long =
        (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    private fun terracottaLayer(y: Int): Block = when ((y + 3) % 12) {
        0, 1 -> Block.ORANGE_TERRACOTTA
        2 -> Block.YELLOW_TERRACOTTA
        3, 4 -> Block.TERRACOTTA
        5 -> Block.BROWN_TERRACOTTA
        6, 7 -> Block.RED_TERRACOTTA
        8 -> Block.WHITE_TERRACOTTA
        9, 10 -> Block.LIGHT_GRAY_TERRACOTTA
        else -> Block.ORANGE_TERRACOTTA
    }

    private fun smoothstep(t: Double): Double {
        val clamped = t.coerceIn(-1.0, 1.0)
        val n = (clamped + 1.0) / 2.0
        return (3 * n * n - 2 * n * n * n) * 2.0 - 1.0
    }

    private fun terrace(value: Double, steps: Int): Double {
        val scaled = (value + 1.0) / 2.0 * steps
        return (floor(scaled) / steps) * 2.0 - 1.0
    }

    companion object {
        private val BEACH_EXCLUDED_BIOMES = setOf("desert", "badlands", "snowy_plains", "ice_spikes", "swamp", "jungle")
        private val FALLBACK_BIOME: RegistryKey<Biome> = RegistryKey.unsafeOf("plains")
    }
}
