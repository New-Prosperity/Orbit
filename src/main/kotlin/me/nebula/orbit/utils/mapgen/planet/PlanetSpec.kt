package me.nebula.orbit.utils.mapgen.planet

import me.nebula.orbit.utils.mapgen.BiomeZoneConfig
import me.nebula.orbit.utils.mapgen.HeightCurve
import net.minestom.server.instance.block.Block

data class PlanetPalette(
    val surfaceBlock: Block = Block.GRASS_BLOCK,
    val fillerBlock: Block = Block.DIRT,
    val underwaterSurface: Block = Block.SAND,
    val stoneBlock: Block = Block.STONE,
    val deepslateBlock: Block = Block.DEEPSLATE,
    val bedrockBlock: Block = Block.BEDROCK,
    val foundationBlock: Block = Block.STONE,
)

data class HeightProfile(
    val baseHeight: Int = 64,
    val heightVariation: Int = 24,
    val terrainScale: Double = 0.01,
    val continentalScale: Double = 0.002,
    val continentalInfluence: Double = 15.0,
    val erosionScale: Double = 0.005,
    val erosionStrength: Double = 0.4,
    val heightCurve: HeightCurve = HeightCurve.SMOOTH,
    val ridgedMix: Double = 0.0,
    val overhangAmplitude: Double = 0.0,
    val overhangScaleXZ: Double = 0.04,
    val overhangScaleY: Double = 0.08,
)

data class RiverConfig(
    val enabled: Boolean = false,
    val noiseScale: Double = 0.004,
    val widthThreshold: Double = 0.02,
    val depth: Int = 4,
    val bankBlock: String = "minecraft:sand",
)

data class DecorationConfig(
    val enabled: Boolean = true,
    val maxTreesPerChunk: Int = 12,
    val maxVegetationPerChunk: Int = 64,
    val underwaterVegetation: Boolean = true,
)

data class IslandConfig(
    val enabled: Boolean = false,
    val centerX: Int = 0,
    val centerZ: Int = 0,
    val radius: Double = 256.0,
    val falloffWidth: Double = 64.0,
    val coastlineNoiseScale: Double = 0.012,
    val coastlineNoiseAmplitude: Double = 24.0,
    val oceanDepth: Int = 12,
    val oceanFloorBlock: String = "minecraft:gravel",
    val beachBlock: String = "minecraft:sand",
    val beachWidth: Int = 3,
)

data class CaveProfile(
    val enabled: Boolean = true,
    val noiseScale: Double = 0.04,
    val threshold: Double = 0.62,
    val minY: Int = 4,
    val maxY: Int = 56,
    val noodleEnabled: Boolean = true,
    val noodleScale: Double = 0.06,
    val noodleThreshold: Double = 0.78,
    val ravinesEnabled: Boolean = false,
    val ravineChancePerChunk: Double = 0.005,
)

data class OreEntry(
    val block: Block,
    val minY: Int,
    val maxY: Int,
    val veinSize: Int,
    val veinsPerChunk: Int,
    val clusterShape: String? = null,
)

data class StructureEntry(
    val schematicId: String,
    val chancePerChunk: Double = 0.05,
    val minSpacingChunks: Int = 4,
)

data class PlanetSpec(
    val id: String,
    val seed: Long,
    val palette: PlanetPalette = PlanetPalette(),
    val heightProfile: HeightProfile = HeightProfile(),
    val caveProfile: CaveProfile = CaveProfile(),
    val rivers: RiverConfig = RiverConfig(),
    val decoration: DecorationConfig = DecorationConfig(),
    val island: IslandConfig = IslandConfig(),
    val seaLevel: Int = 62,
    val bedrockHeight: Int = 1,
    val fillerDepth: Int = 3,
    val deepslateLevel: Int = 8,
    val ores: List<OreEntry> = emptyList(),
    val structures: List<StructureEntry> = emptyList(),
    val biomes: List<String> = emptyList(),
    val biomeZoning: BiomeZoneConfig = BiomeZoneConfig(),
    val structureRadiusChunks: Int = 2,
    val maxStructureTransitionRadius: Int = 16,
    val worldMinY: Int = 0,
    val worldMaxY: Int = 256,
)
