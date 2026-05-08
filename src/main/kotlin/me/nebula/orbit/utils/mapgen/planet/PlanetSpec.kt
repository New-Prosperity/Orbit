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
    val surfaceJitterAmplitude: Double = 1.2,
    val surfaceJitterScale: Double = 0.18,
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
) {
    init {
        require(id.isNotBlank()) { "PlanetSpec.id must not be blank" }
        require(worldMinY < worldMaxY) { "PlanetSpec.worldMinY ($worldMinY) must be < worldMaxY ($worldMaxY)" }
        require(bedrockHeight >= 0) { "PlanetSpec.bedrockHeight must be >= 0 (got $bedrockHeight)" }
        require(deepslateLevel >= bedrockHeight) { "PlanetSpec.deepslateLevel ($deepslateLevel) must be >= bedrockHeight ($bedrockHeight)" }
        require(seaLevel in worldMinY..worldMaxY) { "PlanetSpec.seaLevel ($seaLevel) must lie within worldMinY..worldMaxY ($worldMinY..$worldMaxY)" }
        require(fillerDepth >= 0) { "PlanetSpec.fillerDepth must be >= 0 (got $fillerDepth)" }
        require(structureRadiusChunks in 0..8) { "PlanetSpec.structureRadiusChunks must be in 0..8 (got $structureRadiusChunks)" }
        require(heightProfile.heightVariation >= 0) { "HeightProfile.heightVariation must be >= 0 (got ${heightProfile.heightVariation})" }
        require(heightProfile.terrainScale > 0) { "HeightProfile.terrainScale must be > 0 (got ${heightProfile.terrainScale})" }
        require(heightProfile.overhangAmplitude >= 0) { "HeightProfile.overhangAmplitude must be >= 0 (got ${heightProfile.overhangAmplitude})" }
        if (island.enabled) {
            require(island.radius > 0) { "IslandConfig.radius must be > 0 when enabled (got ${island.radius})" }
            require(island.falloffWidth in 0.0..island.radius) { "IslandConfig.falloffWidth (${island.falloffWidth}) must be in 0..radius (${island.radius})" }
            require(island.oceanDepth >= 0) { "IslandConfig.oceanDepth must be >= 0 (got ${island.oceanDepth})" }
            require(island.beachWidth >= 0) { "IslandConfig.beachWidth must be >= 0 (got ${island.beachWidth})" }
        }
        if (caveProfile.enabled) {
            require(caveProfile.minY <= caveProfile.maxY) { "CaveProfile.minY (${caveProfile.minY}) must be <= maxY (${caveProfile.maxY})" }
            require(caveProfile.threshold > 0) { "CaveProfile.threshold must be > 0 (got ${caveProfile.threshold})" }
        }
        for (ore in ores) {
            require(ore.minY <= ore.maxY) { "OreEntry minY (${ore.minY}) must be <= maxY (${ore.maxY}) for block ${ore.block.name()}" }
            require(ore.veinSize > 0) { "OreEntry veinSize must be > 0 for block ${ore.block.name()}" }
            require(ore.veinsPerChunk >= 0) { "OreEntry veinsPerChunk must be >= 0 for block ${ore.block.name()}" }
        }
        for (entry in structures) {
            require(entry.chancePerChunk in 0.0..1.0) { "StructureEntry.chancePerChunk for '${entry.schematicId}' must be in 0..1 (got ${entry.chancePerChunk})" }
            require(entry.minSpacingChunks >= 0) { "StructureEntry.minSpacingChunks for '${entry.schematicId}' must be >= 0 (got ${entry.minSpacingChunks})" }
        }
    }
}
