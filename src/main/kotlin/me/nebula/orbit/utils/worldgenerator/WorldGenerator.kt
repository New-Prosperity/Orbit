package me.nebula.orbit.utils.worldgenerator

import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator

class LayeredGenerator(
    private val layers: List<GeneratorLayer>,
) : Generator {

    override fun generate(unit: GenerationUnit) {
        val modifier = unit.modifier()
        for (layer in layers) {
            modifier.fillHeight(layer.minY, layer.maxY, layer.block)
        }
    }
}

data class GeneratorLayer(val minY: Int, val maxY: Int, val block: Block)

class LayeredGeneratorBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val layers = mutableListOf<GeneratorLayer>()

    fun layer(minY: Int, maxY: Int, block: Block) {
        layers.add(GeneratorLayer(minY, maxY, block))
    }

    fun bedrock(y: Int = 0) { layer(y, y + 1, Block.BEDROCK) }

    fun stone(minY: Int, maxY: Int) { layer(minY, maxY, Block.STONE) }

    fun dirt(minY: Int, maxY: Int) { layer(minY, maxY, Block.DIRT) }

    fun grass(y: Int) { layer(y, y + 1, Block.GRASS_BLOCK) }

    fun fill(minY: Int, maxY: Int, block: Block) { layer(minY, maxY, block) }

    @PublishedApi internal fun build(): LayeredGenerator = LayeredGenerator(layers.toList())
}

inline fun layeredGenerator(block: LayeredGeneratorBuilder.() -> Unit): LayeredGenerator =
    LayeredGeneratorBuilder().apply(block).build()

fun voidGenerator(): Generator = Generator { }

fun flatGenerator(height: Int = 40, surface: Block = Block.GRASS_BLOCK): Generator = Generator { unit ->
    val modifier = unit.modifier()
    modifier.fillHeight(0, 1, Block.BEDROCK)
    modifier.fillHeight(1, height - 1, Block.STONE)
    modifier.fillHeight(height - 1, height, Block.DIRT)
    modifier.fillHeight(height, height + 1, surface)
}

fun superFlatGenerator(vararg layers: Pair<Int, Block>): Generator = Generator { unit ->
    val modifier = unit.modifier()
    var currentY = 0
    for ((thickness, block) in layers) {
        modifier.fillHeight(currentY, currentY + thickness, block)
        currentY += thickness
    }
}

fun checkerboardGenerator(
    height: Int = 1,
    block1: Block = Block.WHITE_CONCRETE,
    block2: Block = Block.BLACK_CONCRETE,
    baseY: Int = 40,
): Generator = Generator { unit ->
    val modifier = unit.modifier()
    modifier.fillHeight(0, baseY, Block.STONE)
    val start = unit.absoluteStart()
    val end = unit.absoluteEnd()
    for (x in start.blockX() until end.blockX()) {
        for (z in start.blockZ() until end.blockZ()) {
            val block = if ((x + z) % 2 == 0) block1 else block2
            for (y in baseY until baseY + height) {
                modifier.setBlock(x, y, z, block)
            }
        }
    }
}
