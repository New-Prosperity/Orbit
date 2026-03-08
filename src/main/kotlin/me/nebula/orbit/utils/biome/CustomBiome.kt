package me.nebula.orbit.utils.biome

import net.minestom.server.instance.block.Block
import me.nebula.orbit.utils.mapgen.*

class BiomeBlocksScope @PublishedApi internal constructor() {
    var surface: Block = Block.GRASS_BLOCK
    var filler: Block = Block.DIRT
    var underwaterSurface: Block = Block.SAND
    var stone: Block = Block.STONE
}

enum class TerrainShape(
    val baseHeight: Double,
    val heightVariation: Double,
    val heightCurve: HeightCurve,
) {
    FLAT(64.0, 2.0, HeightCurve.LINEAR),
    PLAINS(64.0, 5.0, HeightCurve.LINEAR),
    ROLLING_HILLS(66.0, 8.0, HeightCurve.SMOOTH),
    ROLLING(66.0, 6.0, HeightCurve.ROLLING),
    HIGHLANDS(72.0, 12.0, HeightCurve.SMOOTH),
    FOOTHILLS(70.0, 14.0, HeightCurve.SMOOTH),
    PLATEAUS(75.0, 14.0, HeightCurve.TERRACE),
    MESA(78.0, 16.0, HeightCurve.MESA),
    MOUNTAINOUS(82.0, 28.0, HeightCurve.AMPLIFIED),
    RIDGED(80.0, 24.0, HeightCurve.RIDGED),
    PEAKS(92.0, 35.0, HeightCurve.CLIFF),
    SPIRES(95.0, 40.0, HeightCurve.RIDGED),
    VALLEYS(56.0, 18.0, HeightCurve.SMOOTH),
    BASIN(50.0, 10.0, HeightCurve.ROLLING),
    CANYON(48.0, 30.0, HeightCurve.CLIFF),
    CLIFFS(70.0, 22.0, HeightCurve.CLIFF),
    ERODED(65.0, 10.0, HeightCurve.LINEAR),
    DUNES(66.0, 8.0, HeightCurve.ROLLING),
    OCEAN_FLOOR(38.0, 6.0, HeightCurve.SMOOTH),
    SHELF(54.0, 4.0, HeightCurve.SMOOTH),
}

class BiomeTerrainScope @PublishedApi internal constructor() {
    var baseHeight: Double = 64.0
    var heightVariation: Double = 6.0
    var heightCurve: HeightCurve = HeightCurve.LINEAR

    fun shape(shape: TerrainShape) {
        baseHeight = shape.baseHeight
        heightVariation = shape.heightVariation
        heightCurve = shape.heightCurve
    }
}

class BiomeClimateScope @PublishedApi internal constructor() {
    var temperature: Double = 0.5
    var moisture: Double = 0.5
    var hasPrecipitation: Boolean = true
    var frozen: Boolean = false
    var snowLine: Int = Int.MAX_VALUE
}

class BiomeVegetationScope @PublishedApi internal constructor() {
    var treeDensity: Double = 0.0
    var vegetationDensity: Double = 0.0
    var treeTypes: List<TreeType> = listOf(TreeType.OAK)

    fun trees(vararg types: TreeType) {
        treeTypes = types.toList()
    }
}

class BiomeVisualsScope @PublishedApi internal constructor() {
    var waterColor: Int = 0x3F76E4
    var grassColor: Int? = null
    var foliageColor: Int? = null
    var grassModifier: GrassModifier = GrassModifier.NONE
}

class BiomeModifiersScope @PublishedApi internal constructor() {
    var caveFrequency: Double = 1.0
    var oreMultiplier: Double = 1.0
}

class CustomBiomeBuilder @PublishedApi internal constructor(val id: String) {

    @PublishedApi internal val blocks = BiomeBlocksScope()
    @PublishedApi internal val terrain = BiomeTerrainScope()
    @PublishedApi internal val climate = BiomeClimateScope()
    @PublishedApi internal val vegetation = BiomeVegetationScope()
    @PublishedApi internal val visuals = BiomeVisualsScope()
    @PublishedApi internal val modifiers = BiomeModifiersScope()

    inline fun blocks(block: BiomeBlocksScope.() -> Unit) { blocks.apply(block) }
    inline fun terrain(block: BiomeTerrainScope.() -> Unit) { terrain.apply(block) }
    inline fun climate(block: BiomeClimateScope.() -> Unit) { climate.apply(block) }
    inline fun vegetation(block: BiomeVegetationScope.() -> Unit) { vegetation.apply(block) }
    inline fun visuals(block: BiomeVisualsScope.() -> Unit) { visuals.apply(block) }
    inline fun modifiers(block: BiomeModifiersScope.() -> Unit) { modifiers.apply(block) }

    @PublishedApi internal fun build(): BiomeDefinition = BiomeDefinition(
        id = id,
        surfaceBlock = blocks.surface,
        fillerBlock = blocks.filler,
        underwaterSurface = blocks.underwaterSurface,
        stoneBlock = blocks.stone,
        baseHeight = terrain.baseHeight,
        heightVariation = terrain.heightVariation,
        heightCurve = terrain.heightCurve,
        temperature = climate.temperature,
        moisture = climate.moisture,
        hasPrecipitation = climate.hasPrecipitation,
        frozen = climate.frozen,
        snowLine = climate.snowLine,
        treeDensity = vegetation.treeDensity,
        vegetationDensity = vegetation.vegetationDensity,
        treeTypes = vegetation.treeTypes,
        caveFrequencyMultiplier = modifiers.caveFrequency,
        oreMultiplier = modifiers.oreMultiplier,
        waterColor = visuals.waterColor,
        grassColor = visuals.grassColor,
        foliageColor = visuals.foliageColor,
        grassModifier = visuals.grassModifier,
    )
}

inline fun customBiome(id: String, block: CustomBiomeBuilder.() -> Unit): BiomeDefinition =
    CustomBiomeBuilder(id).apply(block).build()

object BiomePresets {

    fun volcanic() = customBiome("volcanic") {
        blocks {
            surface = Block.BLACKSTONE
            filler = Block.BASALT
            underwaterSurface = Block.MAGMA_BLOCK
            stone = Block.DEEPSLATE
        }
        terrain {
            shape(TerrainShape.CLIFFS)
            baseHeight = 75.0
        }
        climate {
            temperature = 1.5
            moisture = 0.0
            hasPrecipitation = false
        }
        vegetation {
            treeDensity = 0.0
            vegetationDensity = 0.02
        }
        visuals {
            waterColor = 0xFF4500
            grassColor = 0x3B3B3B
            foliageColor = 0x3B3B3B
        }
        modifiers {
            caveFrequency = 1.5
            oreMultiplier = 2.0
        }
    }

    fun mushroomFields() = customBiome("mushroom_fields") {
        blocks {
            surface = Block.MYCELIUM
            filler = Block.DIRT
            underwaterSurface = Block.DIRT
        }
        terrain {
            shape(TerrainShape.ROLLING_HILLS)
            baseHeight = 66.0
            heightVariation = 5.0
        }
        climate {
            temperature = 0.9
            moisture = 1.0
        }
        vegetation {
            treeDensity = 0.0
            vegetationDensity = 0.1
        }
        visuals {
            waterColor = 0x8A8997
        }
        modifiers {
            caveFrequency = 0.5
        }
    }

    fun frozenWasteland() = customBiome("frozen_wasteland") {
        blocks {
            surface = Block.SNOW_BLOCK
            filler = Block.PACKED_ICE
            underwaterSurface = Block.ICE
            stone = Block.BLUE_ICE
        }
        terrain {
            shape(TerrainShape.HIGHLANDS)
        }
        climate {
            temperature = 0.0
            moisture = 0.3
            frozen = true
            snowLine = 0
        }
        visuals {
            waterColor = 0x3D57D6
        }
    }

    fun lushCaves() = customBiome("lush_caves") {
        blocks {
            surface = Block.MOSS_BLOCK
            filler = Block.ROOTED_DIRT
            underwaterSurface = Block.CLAY
        }
        terrain {
            shape(TerrainShape.FLAT)
            baseHeight = 62.0
            heightVariation = 4.0
        }
        climate {
            temperature = 0.5
            moisture = 0.8
        }
        vegetation {
            treeDensity = 0.02
            vegetationDensity = 0.6
            trees(TreeType.OAK)
        }
        visuals {
            waterColor = 0x43D5EE
            grassColor = 0x6ABE30
            foliageColor = 0x6ABE30
        }
        modifiers {
            caveFrequency = 2.0
        }
    }

    fun cherryGrove() = customBiome("cherry_grove") {
        blocks {
            surface = Block.GRASS_BLOCK
            filler = Block.DIRT
            underwaterSurface = Block.SAND
        }
        terrain {
            shape(TerrainShape.ROLLING_HILLS)
            baseHeight = 72.0
            heightVariation = 6.0
        }
        climate {
            temperature = 0.5
            moisture = 0.8
        }
        vegetation {
            treeDensity = 0.08
            vegetationDensity = 0.5
            trees(TreeType.OAK)
        }
        visuals {
            waterColor = 0x5DB7EF
            grassColor = 0xB6DB61
            foliageColor = 0xB6DB61
        }
    }

    fun deepDark() = customBiome("deep_dark") {
        blocks {
            surface = Block.SCULK
            filler = Block.DEEPSLATE
            underwaterSurface = Block.DEEPSLATE
            stone = Block.DEEPSLATE
        }
        terrain {
            shape(TerrainShape.VALLEYS)
            baseHeight = 20.0
            heightVariation = 8.0
        }
        climate {
            temperature = 0.8
            moisture = 0.4
        }
        vegetation {
            treeDensity = 0.0
            vegetationDensity = 0.0
        }
        visuals {
            waterColor = 0x3F76E4
            grassColor = 0x2D4A22
            foliageColor = 0x2D4A22
        }
        modifiers {
            caveFrequency = 3.0
            oreMultiplier = 1.5
        }
    }

    fun all(): List<BiomeDefinition> = listOf(
        volcanic(), mushroomFields(), frozenWasteland(),
        lushCaves(), cherryGrove(), deepDark(),
    )
}
