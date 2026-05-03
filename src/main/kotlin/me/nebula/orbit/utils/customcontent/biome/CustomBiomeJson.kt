package me.nebula.orbit.utils.customcontent.biome

import me.nebula.orbit.utils.mapgen.BiomeDefinition
import me.nebula.orbit.utils.mapgen.GrassModifier
import me.nebula.orbit.utils.mapgen.HeightCurve
import me.nebula.orbit.utils.mapgen.TreeType
import me.nebula.orbit.utils.mapgen.planet.BlockResolver

data class CustomBiomeJson(
    val id: String,
    val surfaceBlock: String = "minecraft:grass_block",
    val fillerBlock: String = "minecraft:dirt",
    val underwaterSurface: String = "minecraft:sand",
    val stoneBlock: String = "minecraft:stone",
    val baseHeight: Double = 64.0,
    val heightVariation: Double = 6.0,
    val heightCurve: HeightCurve = HeightCurve.LINEAR,
    val temperature: Double = 0.5,
    val moisture: Double = 0.5,
    val treeDensity: Double = 0.0,
    val vegetationDensity: Double = 0.0,
    val treeTypes: List<TreeType> = emptyList(),
    val caveFrequencyMultiplier: Double = 1.0,
    val oreMultiplier: Double = 1.0,
    val snowLine: Int? = null,
    val frozen: Boolean = false,
    val hasPrecipitation: Boolean = true,
    val waterColor: Int = 0x3F76E4,
    val grassColor: Int? = null,
    val foliageColor: Int? = null,
    val grassModifier: GrassModifier = GrassModifier.NONE,
) {
    fun toDefinition(): BiomeDefinition = BiomeDefinition(
        id = id,
        surfaceBlock = BlockResolver.resolve(surfaceBlock),
        fillerBlock = BlockResolver.resolve(fillerBlock),
        underwaterSurface = BlockResolver.resolve(underwaterSurface),
        stoneBlock = BlockResolver.resolve(stoneBlock),
        baseHeight = baseHeight,
        heightVariation = heightVariation,
        heightCurve = heightCurve,
        temperature = temperature,
        moisture = moisture,
        treeDensity = treeDensity,
        vegetationDensity = vegetationDensity,
        treeTypes = treeTypes.ifEmpty { listOf(TreeType.OAK) },
        caveFrequencyMultiplier = caveFrequencyMultiplier,
        oreMultiplier = oreMultiplier,
        snowLine = snowLine ?: Int.MAX_VALUE,
        frozen = frozen,
        hasPrecipitation = hasPrecipitation,
        waterColor = waterColor,
        grassColor = grassColor,
        foliageColor = foliageColor,
        grassModifier = grassModifier,
    )
}
