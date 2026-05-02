package me.nebula.orbit.utils.mapgen

import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator

class SuperflatGenerator(
    private val bedrockHeight: Int = 1,
    private val dirtHeight: Int = 3,
    private val topBlock: Block = Block.GRASS_BLOCK,
    private val fillerBlock: Block = Block.DIRT,
    private val bedrockBlock: Block = Block.BEDROCK,
) : Generator {

    private val topY = bedrockHeight + dirtHeight
    private val totalHeight = topY + 1

    override fun generate(unit: GenerationUnit) {
        val modifier = unit.modifier()
        val minY = unit.absoluteStart().blockY()
        val maxY = unit.absoluteEnd().blockY()

        val bedrockEnd = bedrockHeight
        val dirtEnd = bedrockEnd + dirtHeight

        if (minY < bedrockEnd) {
            modifier.fillHeight(maxOf(0, minY), minOf(bedrockEnd, maxY), bedrockBlock)
        }
        if (minY < dirtEnd && maxY > bedrockEnd) {
            modifier.fillHeight(maxOf(bedrockEnd, minY), minOf(dirtEnd, maxY), fillerBlock)
        }
        if (minY <= topY && maxY > topY) {
            modifier.fillHeight(topY, totalHeight, topBlock)
        }
    }
}
