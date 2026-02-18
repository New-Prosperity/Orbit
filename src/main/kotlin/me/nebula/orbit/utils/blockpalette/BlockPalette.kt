package me.nebula.orbit.utils.blockpalette

import net.minestom.server.coordinate.Point
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.util.concurrent.ThreadLocalRandom

data class WeightedBlock(val block: Block, val weight: Double)

class BlockPalette(
    val name: String,
    private val entries: List<WeightedBlock>,
) {

    private val totalWeight = entries.sumOf { it.weight }

    init {
        require(entries.isNotEmpty()) { "BlockPalette '$name' must have at least one entry" }
        require(totalWeight > 0.0) { "BlockPalette '$name' total weight must be positive" }
    }

    fun randomBlock(): Block {
        var remaining = ThreadLocalRandom.current().nextDouble(totalWeight)
        for (entry in entries) {
            remaining -= entry.weight
            if (remaining <= 0.0) return entry.block
        }
        return entries.last().block
    }

    fun place(instance: Instance, pos: Point) {
        instance.setBlock(pos, randomBlock())
    }

    fun fillRegion(instance: Instance, min: Point, max: Point) {
        val minX = minOf(min.blockX(), max.blockX())
        val minY = minOf(min.blockY(), max.blockY())
        val minZ = minOf(min.blockZ(), max.blockZ())
        val maxX = maxOf(min.blockX(), max.blockX())
        val maxY = maxOf(min.blockY(), max.blockY())
        val maxZ = maxOf(min.blockZ(), max.blockZ())
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    instance.setBlock(x, y, z, randomBlock())
                }
            }
        }
    }
}

class BlockPaletteBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal val entries = mutableListOf<WeightedBlock>()

    fun block(block: Block, weight: Double = 1.0) {
        require(weight > 0.0) { "Weight must be positive" }
        entries += WeightedBlock(block, weight)
    }

    @PublishedApi internal fun build(): BlockPalette = BlockPalette(name, entries.toList())
}

inline fun blockPalette(name: String, builder: BlockPaletteBuilder.() -> Unit): BlockPalette =
    BlockPaletteBuilder(name).apply(builder).build()

fun Instance.fillWithPalette(palette: BlockPalette, min: Point, max: Point) =
    palette.fillRegion(this, min, max)
