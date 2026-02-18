package me.nebula.orbit.utils.structureblock

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.util.concurrent.ConcurrentHashMap

data class BlockEntry(val relativeX: Int, val relativeY: Int, val relativeZ: Int, val block: Block)

class Structure(val name: String, val blocks: List<BlockEntry>) {

    val sizeX: Int get() = blocks.maxOfOrNull { it.relativeX }?.plus(1) ?: 0
    val sizeY: Int get() = blocks.maxOfOrNull { it.relativeY }?.plus(1) ?: 0
    val sizeZ: Int get() = blocks.maxOfOrNull { it.relativeZ }?.plus(1) ?: 0

    fun paste(instance: Instance, origin: Point, ignoreAir: Boolean = false) {
        for (entry in blocks) {
            if (ignoreAir && entry.block == Block.AIR) continue
            instance.setBlock(
                origin.blockX() + entry.relativeX,
                origin.blockY() + entry.relativeY,
                origin.blockZ() + entry.relativeZ,
                entry.block,
            )
        }
    }

    fun pasteRotated90(instance: Instance, origin: Point, rotations: Int = 1, ignoreAir: Boolean = false) {
        val r = ((rotations % 4) + 4) % 4
        for (entry in blocks) {
            if (ignoreAir && entry.block == Block.AIR) continue
            val (rx, rz) = rotateXZ(entry.relativeX, entry.relativeZ, r)
            instance.setBlock(
                origin.blockX() + rx,
                origin.blockY() + entry.relativeY,
                origin.blockZ() + rz,
                entry.block,
            )
        }
    }

    fun clear(instance: Instance, origin: Point) {
        for (entry in blocks) {
            instance.setBlock(
                origin.blockX() + entry.relativeX,
                origin.blockY() + entry.relativeY,
                origin.blockZ() + entry.relativeZ,
                Block.AIR,
            )
        }
    }

    private fun rotateXZ(x: Int, z: Int, rotations: Int): Pair<Int, Int> = when (rotations) {
        0 -> x to z
        1 -> -z to x
        2 -> -x to -z
        3 -> z to -x
        else -> x to z
    }
}

object StructureRegistry {
    private val structures = ConcurrentHashMap<String, Structure>()

    fun register(structure: Structure) {
        structures[structure.name] = structure
    }

    fun get(name: String): Structure? = structures[name]

    fun remove(name: String): Structure? = structures.remove(name)

    fun all(): Collection<Structure> = structures.values
}

fun captureStructure(name: String, instance: Instance, from: Point, to: Point): Structure {
    val minX = minOf(from.blockX(), to.blockX())
    val minY = minOf(from.blockY(), to.blockY())
    val minZ = minOf(from.blockZ(), to.blockZ())
    val maxX = maxOf(from.blockX(), to.blockX())
    val maxY = maxOf(from.blockY(), to.blockY())
    val maxZ = maxOf(from.blockZ(), to.blockZ())

    val entries = buildList {
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val block = instance.getBlock(x, y, z)
                    add(BlockEntry(x - minX, y - minY, z - minZ, block))
                }
            }
        }
    }
    return Structure(name, entries)
}
