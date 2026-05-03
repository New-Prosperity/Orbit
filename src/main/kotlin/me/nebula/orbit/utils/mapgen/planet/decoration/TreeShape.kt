package me.nebula.orbit.utils.mapgen.planet.decoration

import me.nebula.orbit.utils.mapgen.planet.BlockResolver
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.UnitModifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

enum class TreeBlockKind { TRUNK, LEAVES, EXPLICIT }

data class TreeShapeBlock(
    val dx: Int,
    val dy: Int,
    val dz: Int,
    val kind: TreeBlockKind = TreeBlockKind.LEAVES,
    val explicit: String? = null,
)

data class TreeShape(
    val id: String,
    val trunkBlock: String,
    val leavesBlock: String,
    val blocks: List<TreeShapeBlock>,
    val variants: List<List<TreeShapeBlock>> = emptyList(),
) {
    private val resolvedTrunk: Block by lazy { BlockResolver.resolve(trunkBlock) }
    private val resolvedLeaves: Block by lazy { BlockResolver.resolve(leavesBlock) }

    fun place(modifier: UnitModifier, x: Int, y: Int, z: Int, rng: Random) {
        val variant = if (variants.isEmpty()) blocks else variants[rng.nextInt(variants.size)]
        for (entry in variant) {
            val block = when (entry.kind) {
                TreeBlockKind.TRUNK -> resolvedTrunk
                TreeBlockKind.LEAVES -> resolvedLeaves
                TreeBlockKind.EXPLICIT -> entry.explicit?.let { BlockResolver.resolveOrNull(it) } ?: resolvedLeaves
            }
            modifier.setBlock(x + entry.dx, y + entry.dy, z + entry.dz, block)
        }
    }
}

object TreeShapeRegistry {

    private val shapes = ConcurrentHashMap<String, TreeShape>()

    fun register(shape: TreeShape) {
        shapes[shape.id.lowercase()] = shape
    }

    operator fun get(id: String): TreeShape? = shapes[id.lowercase()]

    fun all(): Collection<TreeShape> = shapes.values

    fun isEmpty(): Boolean = shapes.isEmpty()

    fun clear() = shapes.clear()

    fun registerBuiltins() {
        register(buildOak("oak", "minecraft:oak_log", "minecraft:oak_leaves", trunkExtra = 0))
        register(buildOak("birch", "minecraft:birch_log", "minecraft:birch_leaves", trunkExtra = 0))
        register(buildOak("dark_oak", "minecraft:dark_oak_log", "minecraft:dark_oak_leaves", trunkExtra = 1))
        register(buildOak("jungle", "minecraft:jungle_log", "minecraft:jungle_leaves", trunkExtra = 3))
        register(buildSpruce("spruce", "minecraft:spruce_log", "minecraft:spruce_leaves"))
        register(buildAcacia("acacia", "minecraft:acacia_log", "minecraft:acacia_leaves"))
    }

    private fun buildOak(id: String, trunk: String, leaves: String, trunkExtra: Int): TreeShape {
        val trunkHeight = 5 + trunkExtra
        val blocks = mutableListOf<TreeShapeBlock>()
        for (dy in 0 until trunkHeight) blocks += TreeShapeBlock(0, dy, 0, TreeBlockKind.TRUNK)
        val canopyBaseY = trunkHeight - 2
        for (dy in 0..2) {
            val r = if (dy < 2) 2 else 1
            for (dx in -r..r) {
                for (dz in -r..r) {
                    if (kotlin.math.abs(dx) == r && kotlin.math.abs(dz) == r) continue
                    if (dx == 0 && dz == 0 && dy < 2) continue
                    blocks += TreeShapeBlock(dx, canopyBaseY + dy, dz, TreeBlockKind.LEAVES)
                }
            }
        }
        blocks += TreeShapeBlock(0, canopyBaseY + 2, 0, TreeBlockKind.LEAVES)
        return TreeShape(id, trunk, leaves, blocks)
    }

    private fun buildSpruce(id: String, trunk: String, leaves: String): TreeShape {
        val trunkHeight = 9
        val blocks = mutableListOf<TreeShapeBlock>()
        for (dy in 0 until trunkHeight) blocks += TreeShapeBlock(0, dy, 0, TreeBlockKind.TRUNK)
        var radius = 1
        var dy = trunkHeight - 1
        while (dy > 1) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (kotlin.math.abs(dx) == radius && kotlin.math.abs(dz) == radius) continue
                    if (dx == 0 && dz == 0) continue
                    blocks += TreeShapeBlock(dx, dy, dz, TreeBlockKind.LEAVES)
                }
            }
            radius++
            dy--
            if (radius > 3) radius = 1
        }
        blocks += TreeShapeBlock(0, trunkHeight, 0, TreeBlockKind.LEAVES)
        return TreeShape(id, trunk, leaves, blocks)
    }

    private fun buildAcacia(id: String, trunk: String, leaves: String): TreeShape {
        val trunkHeight = 5
        val blocks = mutableListOf<TreeShapeBlock>()
        for (dy in 0 until trunkHeight) blocks += TreeShapeBlock(0, dy, 0, TreeBlockKind.TRUNK)
        blocks += TreeShapeBlock(1, trunkHeight - 1, 0, TreeBlockKind.TRUNK)
        for (dx in -2..2) {
            for (dz in -2..2) {
                if (kotlin.math.abs(dx) == 2 && kotlin.math.abs(dz) == 2) continue
                blocks += TreeShapeBlock(1 + dx, trunkHeight, dz, TreeBlockKind.LEAVES)
            }
        }
        for (dx in -1..1) {
            for (dz in -1..1) {
                blocks += TreeShapeBlock(1 + dx, trunkHeight + 1, dz, TreeBlockKind.LEAVES)
            }
        }
        return TreeShape(id, trunk, leaves, blocks)
    }
}
