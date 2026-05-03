package me.nebula.orbit.utils.mapgen.planet.rhexor

import me.nebula.orbit.utils.mapgen.BiomeDefinition
import me.nebula.orbit.utils.mapgen.BiomeRegistry
import me.nebula.orbit.utils.mapgen.BiomeZoneConfig
import me.nebula.orbit.utils.mapgen.HeightCurve
import me.nebula.orbit.utils.mapgen.planet.BlockResolver
import me.nebula.orbit.utils.mapgen.planet.CaveProfile
import me.nebula.orbit.utils.mapgen.planet.DecorationConfig
import me.nebula.orbit.utils.mapgen.planet.HeightProfile
import me.nebula.orbit.utils.mapgen.planet.IslandConfig
import me.nebula.orbit.utils.mapgen.planet.OreEntry
import me.nebula.orbit.utils.mapgen.planet.PlanetGenerator
import me.nebula.orbit.utils.mapgen.planet.PlanetPalette
import me.nebula.orbit.utils.mapgen.planet.PlanetSpec
import me.nebula.orbit.utils.mapgen.planet.RiverConfig
import me.nebula.orbit.utils.mapgen.planet.StructureEntry
import net.minestom.server.instance.block.Block

class RhexorGenerator(
    seed: Long,
    structurePool: List<StructureEntry> = emptyList(),
) : PlanetGenerator(buildSpec(seed, structurePool)) {

    companion object {
        const val ID = "rhexor"

        const val BIOME_DUNES = "rhexor_dunes"
        const val BIOME_CANYON = "rhexor_canyon"
        const val BIOME_PLATEAU = "rhexor_plateau"

        fun registerBiomes() {
            val surface = BlockResolver.resolveOrNull("rhexor_surface") ?: Block.RED_SAND
            val filler = BlockResolver.resolveOrNull("rhexor_dust") ?: Block.RED_SANDSTONE
            val stone = BlockResolver.resolveOrNull("rhexor_stone") ?: Block.TERRACOTTA

            BiomeRegistry.register(BiomeDefinition(
                id = BIOME_DUNES,
                surfaceBlock = surface,
                fillerBlock = filler,
                underwaterSurface = filler,
                stoneBlock = stone,
                baseHeight = 56.0,
                heightVariation = 6.0,
                heightCurve = HeightCurve.SMOOTH,
                temperature = 0.95,
                moisture = 0.05,
                hasPrecipitation = false,
                grassColor = 0x8C5E3C,
                foliageColor = 0x8C5E3C,
                waterColor = 0x6B4030,
                caveFrequencyMultiplier = 0.6,
                oreMultiplier = 0.8,
                surfaceDepth = 2,
                subsurfaceBlock = Block.RED_SANDSTONE,
                subsurfaceDepth = 3,
                vegetationDensity = 0.005,
                grassBlock = Block.DEAD_BUSH,
            ))
            BiomeRegistry.register(BiomeDefinition(
                id = BIOME_CANYON,
                surfaceBlock = stone,
                fillerBlock = stone,
                underwaterSurface = filler,
                stoneBlock = stone,
                baseHeight = 64.0,
                heightVariation = 28.0,
                heightCurve = HeightCurve.RIDGED,
                temperature = 0.9,
                moisture = 0.1,
                hasPrecipitation = false,
                grassColor = 0x7A3A29,
                foliageColor = 0x7A3A29,
                waterColor = 0x6B4030,
                caveFrequencyMultiplier = 1.4,
                oreMultiplier = 1.3,
                surfaceDepth = 1,
                subsurfaceBlock = Block.TERRACOTTA,
                subsurfaceDepth = 4,
                vegetationDensity = 0.0,
            ))
            BiomeRegistry.register(BiomeDefinition(
                id = BIOME_PLATEAU,
                surfaceBlock = stone,
                fillerBlock = filler,
                underwaterSurface = filler,
                stoneBlock = stone,
                baseHeight = 88.0,
                heightVariation = 8.0,
                heightCurve = HeightCurve.MESA,
                temperature = 0.7,
                moisture = 0.2,
                hasPrecipitation = false,
                grassColor = 0x9E5E3F,
                foliageColor = 0x9E5E3F,
                waterColor = 0x6B4030,
                caveFrequencyMultiplier = 1.0,
                oreMultiplier = 1.1,
                surfaceDepth = 1,
                subsurfaceBlock = Block.ORANGE_TERRACOTTA,
                subsurfaceDepth = 6,
                vegetationDensity = 0.002,
                grassBlock = Block.DEAD_BUSH,
            ))
        }

        private fun buildSpec(seed: Long, structurePool: List<StructureEntry>): PlanetSpec {
            registerBiomes()

            val surface = BlockResolver.resolveOrNull("rhexor_surface") ?: Block.RED_SAND
            val filler = BlockResolver.resolveOrNull("rhexor_dust") ?: Block.RED_SANDSTONE
            val stone = BlockResolver.resolveOrNull("rhexor_stone") ?: Block.TERRACOTTA
            val deepStone = BlockResolver.resolveOrNull("rhexor_deepstone") ?: Block.RED_SANDSTONE

            val ironOre = BlockResolver.resolveOrNull("rhexor_iron") ?: Block.IRON_ORE
            val rareOre = BlockResolver.resolveOrNull("rhexor_crystal") ?: Block.AMETHYST_BLOCK

            return PlanetSpec(
                id = ID,
                seed = seed,
                palette = PlanetPalette(
                    surfaceBlock = surface,
                    fillerBlock = filler,
                    underwaterSurface = filler,
                    stoneBlock = stone,
                    deepslateBlock = deepStone,
                    bedrockBlock = Block.BEDROCK,
                    foundationBlock = stone,
                ),
                heightProfile = HeightProfile(
                    baseHeight = 70,
                    heightVariation = 28,
                    terrainScale = 0.012,
                    continentalScale = 0.0025,
                    continentalInfluence = 18.0,
                    erosionScale = 0.006,
                    erosionStrength = 0.5,
                    heightCurve = HeightCurve.RIDGED,
                    ridgedMix = 0.35,
                    overhangAmplitude = 5.0,
                    overhangScaleXZ = 0.04,
                    overhangScaleY = 0.08,
                ),
                caveProfile = CaveProfile(
                    enabled = true,
                    noiseScale = 0.045,
                    threshold = 0.55,
                    minY = 4,
                    maxY = 60,
                    noodleEnabled = true,
                    noodleScale = 0.07,
                    noodleThreshold = 0.78,
                    ravinesEnabled = true,
                    ravineChancePerChunk = 0.008,
                ),
                rivers = RiverConfig(
                    enabled = false,
                    noiseScale = 0.005,
                    widthThreshold = 0.018,
                    depth = 3,
                    bankBlock = "minecraft:red_sandstone",
                ),
                island = IslandConfig(
                    enabled = true,
                    centerX = 0,
                    centerZ = 0,
                    radius = 320.0,
                    falloffWidth = 80.0,
                    coastlineNoiseScale = 0.012,
                    coastlineNoiseAmplitude = 28.0,
                    oceanDepth = 14,
                    oceanFloorBlock = "minecraft:gravel",
                    beachBlock = "minecraft:red_sand",
                    beachWidth = 3,
                ),
                decoration = DecorationConfig(
                    enabled = true,
                    maxTreesPerChunk = 0,
                    maxVegetationPerChunk = 8,
                ),
                seaLevel = 50,
                bedrockHeight = 1,
                fillerDepth = 4,
                deepslateLevel = 12,
                ores = listOf(
                    OreEntry(ironOre, minY = 6, maxY = 64, veinSize = 7, veinsPerChunk = 10),
                    OreEntry(rareOre, minY = 8, maxY = 32, veinSize = 4, veinsPerChunk = 2),
                ),
                structures = structurePool,
                biomes = listOf(BIOME_DUNES, BIOME_CANYON, BIOME_PLATEAU),
                biomeZoning = BiomeZoneConfig(
                    fallbackBiomeId = BIOME_DUNES,
                    biomeScale = 0.0035,
                    blendRadius = 12,
                ),
                structureRadiusChunks = 2,
            )
        }
    }
}
