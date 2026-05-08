package me.nebula.orbit.utils.mapgen.planet

import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import me.nebula.orbit.utils.mapgen.BiomeDefinition
import net.minestom.server.instance.block.Block
import java.awt.image.BufferedImage
import kotlin.math.max

object MinimapRenderer {

    fun render(planet: PlanetGenerator, sizePx: Int = 256, blocksPerPixel: Int = 4): BufferedImage {
        require(sizePx > 0) { "sizePx must be > 0" }
        require(blocksPerPixel > 0) { "blocksPerPixel must be > 0" }

        val image = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_RGB)
        val centerX = planet.spec.island.centerX
        val centerZ = planet.spec.island.centerZ
        val span = sizePx * blocksPerPixel
        val originX = centerX - span / 2
        val originZ = centerZ - span / 2

        val seaLevel = planet.spec.seaLevel
        val maxLandY = seaLevel + 80

        for (px in 0 until sizePx) {
            for (py in 0 until sizePx) {
                val wx = originX + px * blocksPerPixel
                val wz = originZ + py * blocksPerPixel
                val state = planet.islandStateAt(wx, wz)
                val ground = planet.groundLevelAt(wx, wz).toInt()
                val biome = planet.biomeAt(wx, wz)

                val color = when (state) {
                    PlanetGenerator.IslandState.OCEAN -> shadeOcean(ground, seaLevel, planet.spec.island.oceanDepth)
                    PlanetGenerator.IslandState.COAST -> blend(0xC2A874, baseLandColor(biome), 0.5)
                    PlanetGenerator.IslandState.LAND -> shadeLand(ground, seaLevel, maxLandY, biome)
                }
                image.setRGB(px, py, color)
            }
        }
        return image
    }

    private fun baseLandColor(biome: BiomeDefinition?): Int {
        if (biome == null) return 0x6F8C5A
        biome.grassColor?.let { return it }
        return colorOfBlock(biome.surfaceBlock)
    }

    private fun shadeOcean(ground: Int, seaLevel: Int, depth: Int): Int {
        val drop = (seaLevel - ground).coerceIn(0, depth)
        val t = drop.toDouble() / max(1, depth).toDouble()
        return blend(0x3F76E4, 0x07103A, t)
    }

    private fun shadeLand(ground: Int, seaLevel: Int, maxY: Int, biome: BiomeDefinition?): Int {
        val baseColor = baseLandColor(biome)
        val height = (ground - seaLevel).coerceAtLeast(0)
        val cap = max(1, maxY - seaLevel)
        val t = (height.toDouble() / cap).coerceIn(0.0, 1.0)
        return blend(baseColor, 0xFFFFFF, t * 0.4)
    }

    private fun colorOfBlock(block: Block): Int {
        CustomBlockRegistry.fromVanillaBlock(block)?.mapColor?.let { return it }
        return colorOfVanillaBlock(block)
    }

    private fun colorOfVanillaBlock(block: Block): Int = when {
        block.compare(Block.GRASS_BLOCK) -> 0x6F8C5A
        block.compare(Block.DIRT) -> 0x6E5034
        block.compare(Block.SAND) -> 0xE5D9A6
        block.compare(Block.RED_SAND) -> 0xC36A2C
        block.compare(Block.GRAVEL) -> 0x8C8884
        block.compare(Block.STONE) -> 0x7A7A7A
        block.compare(Block.TERRACOTTA) -> 0x9E5E3F
        block.compare(Block.ORANGE_TERRACOTTA) -> 0xA34F22
        block.compare(Block.RED_SANDSTONE) -> 0xB35630
        block.compare(Block.SNOW_BLOCK) -> 0xF7F7F7
        block.compare(Block.PACKED_ICE) -> 0xB0DFE0
        block.compare(Block.MOSS_BLOCK) -> 0x6A8C44
        block.compare(Block.PODZOL) -> 0x4D341B
        else -> 0x808080
    }

    private fun blend(rgbA: Int, rgbB: Int, t: Double): Int {
        val tt = t.coerceIn(0.0, 1.0)
        val ar = (rgbA shr 16) and 0xFF
        val ag = (rgbA shr 8) and 0xFF
        val ab = rgbA and 0xFF
        val br = (rgbB shr 16) and 0xFF
        val bg = (rgbB shr 8) and 0xFF
        val bb = rgbB and 0xFF
        val r = (ar * (1.0 - tt) + br * tt).toInt().coerceIn(0, 255)
        val g = (ag * (1.0 - tt) + bg * tt).toInt().coerceIn(0, 255)
        val b = (ab * (1.0 - tt) + bb * tt).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

}
