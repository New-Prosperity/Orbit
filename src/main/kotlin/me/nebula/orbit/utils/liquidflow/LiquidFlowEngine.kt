package me.nebula.orbit.utils.liquidflow

import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.util.concurrent.ConcurrentLinkedQueue

private val DX = intArrayOf(-1, 1, 0, 0)
private val DZ = intArrayOf(0, 0, -1, 1)

private val REPLACEABLE_NAMES = setOf(
    "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
    "minecraft:dead_bush", "minecraft:seagrass", "minecraft:tall_seagrass",
    "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
    "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
    "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip",
    "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
    "minecraft:wither_rose", "minecraft:sunflower", "minecraft:lilac",
    "minecraft:rose_bush", "minecraft:peony", "minecraft:torch",
    "minecraft:wall_torch", "minecraft:redstone_torch", "minecraft:redstone_wall_torch",
    "minecraft:soul_torch", "minecraft:soul_wall_torch", "minecraft:snow",
    "minecraft:vine", "minecraft:cobweb",
)

class LiquidFlowEngine(
    private val instance: Instance,
    private val sourceBlock: Block,
    private val maxLevel: Int,
    private val infiniteSource: Boolean,
    private val opposingLiquid: Block? = null,
) {
    private val pending = ConcurrentLinkedQueue<Long>()
    private val isWater = sourceBlock.compare(Block.WATER)

    fun scheduleUpdate(x: Int, y: Int, z: Int) {
        pending.add(packPos(x, y, z))
    }

    fun scheduleNeighborUpdates(x: Int, y: Int, z: Int) {
        for (i in DX.indices) scheduleUpdate(x + DX[i], y, z + DZ[i])
        scheduleUpdate(x, y - 1, z)
        scheduleUpdate(x, y + 1, z)
    }

    fun notifyBlockChanged(x: Int, y: Int, z: Int) {
        scheduleUpdate(x, y, z)
        scheduleNeighborUpdates(x, y, z)
    }

    fun tick() {
        val batch = LinkedHashSet<Long>(minOf(pending.size + 1, MAX_UPDATES_PER_TICK))
        while (batch.size < MAX_UPDATES_PER_TICK) {
            batch.add(pending.poll() ?: break)
        }
        for (packed in batch) {
            processPosition(unpackX(packed), unpackY(packed), unpackZ(packed))
        }
    }

    private fun processPosition(x: Int, y: Int, z: Int) {
        val block = instance.getBlock(x, y, z)

        if (isThisLiquid(block)) {
            processLiquid(x, y, z, getLevel(block))
        } else if (canReplace(block)) {
            tryFillEmpty(x, y, z)
        }
    }

    private fun processLiquid(x: Int, y: Int, z: Int, level: Int) {
        if (level == 0) {
            spread(x, y, z, 0)
            return
        }

        if (infiniteSource && level in 1..7 && countAdjacentSources(x, y, z) >= 2) {
            instance.setBlock(x, y, z, sourceBlock)
            scheduleNeighborUpdates(x, y, z)
            spread(x, y, z, 0)
            return
        }

        val expected = calculateExpectedLevel(x, y, z)
        if (expected < 0) {
            instance.setBlock(x, y, z, Block.AIR)
            scheduleNeighborUpdates(x, y, z)
            return
        }

        if (expected != level) {
            setLiquid(x, y, z, expected)
            scheduleNeighborUpdates(x, y, z)
        }

        spread(x, y, z, expected)
    }

    private fun tryFillEmpty(x: Int, y: Int, z: Int) {
        val above = instance.getBlock(x, y + 1, z)
        if (isThisLiquid(above)) {
            setLiquid(x, y, z, FALLING_LEVEL)
            scheduleNeighborUpdates(x, y, z)
            return
        }

        var minNeighborLevel = Int.MAX_VALUE
        for (i in DX.indices) {
            val neighbor = instance.getBlock(x + DX[i], y, z + DZ[i])
            if (isThisLiquid(neighbor)) {
                val l = getLevel(neighbor)
                if (l < FALLING_LEVEL && l < minNeighborLevel) minNeighborLevel = l
            }
        }

        if (minNeighborLevel < maxLevel) {
            val newLevel = minNeighborLevel + 1
            setLiquid(x, y, z, newLevel)
            scheduleNeighborUpdates(x, y, z)
        }
    }

    private fun spread(x: Int, y: Int, z: Int, level: Int) {
        val below = instance.getBlock(x, y - 1, z)
        if (canReplace(below)) {
            setLiquid(x, y - 1, z, FALLING_LEVEL)
            scheduleNeighborUpdates(x, y - 1, z)
            return
        }

        if (isOpposingLiquid(below)) {
            handleLiquidContact(x, y - 1, z, below, flowingDown = true)
            return
        }

        if (level >= FALLING_LEVEL) {
            spreadHorizontal(x, y, z, 0)
            return
        }

        if (level >= maxLevel) return
        spreadHorizontal(x, y, z, level)
    }

    private fun spreadHorizontal(x: Int, y: Int, z: Int, sourceLevel: Int) {
        val nextLevel = sourceLevel + 1
        if (nextLevel > maxLevel) return

        for (i in DX.indices) {
            val nx = x + DX[i]
            val nz = z + DZ[i]
            val neighbor = instance.getBlock(nx, y, nz)

            if (isOpposingLiquid(neighbor)) {
                handleLiquidContact(nx, y, nz, neighbor, flowingDown = false)
            } else if (canReplace(neighbor)) {
                setLiquid(nx, y, nz, nextLevel)
                scheduleNeighborUpdates(nx, y, nz)
            } else if (isThisLiquid(neighbor)) {
                val neighborLevel = getLevel(neighbor)
                if (neighborLevel in (nextLevel + 1) until FALLING_LEVEL) {
                    setLiquid(nx, y, nz, nextLevel)
                    scheduleNeighborUpdates(nx, y, nz)
                }
            }
        }
    }

    private fun handleLiquidContact(x: Int, y: Int, z: Int, otherBlock: Block, flowingDown: Boolean) {
        if (opposingLiquid == null) return
        val otherLevel = otherBlock.getProperty("level")?.toIntOrNull() ?: 0
        val resultBlock = when {
            isWater && otherLevel == 0 -> Block.OBSIDIAN
            isWater -> Block.COBBLESTONE
            !isWater && flowingDown -> Block.STONE
            !isWater -> Block.COBBLESTONE
            else -> Block.COBBLESTONE
        }
        instance.setBlock(x, y, z, resultBlock)
        scheduleNeighborUpdates(x, y, z)
    }

    private fun isOpposingLiquid(block: Block): Boolean =
        opposingLiquid != null && block.compare(opposingLiquid)

    private fun calculateExpectedLevel(x: Int, y: Int, z: Int): Int {
        val above = instance.getBlock(x, y + 1, z)
        if (isThisLiquid(above)) return FALLING_LEVEL

        var minNeighborLevel = Int.MAX_VALUE
        for (i in DX.indices) {
            val neighbor = instance.getBlock(x + DX[i], y, z + DZ[i])
            if (isThisLiquid(neighbor)) {
                val l = getLevel(neighbor)
                if (l < FALLING_LEVEL && l < minNeighborLevel) minNeighborLevel = l
            }
        }

        if (minNeighborLevel == Int.MAX_VALUE) return -1
        val expected = minNeighborLevel + 1
        return if (expected > maxLevel) -1 else expected
    }

    private fun countAdjacentSources(x: Int, y: Int, z: Int): Int {
        var count = 0
        for (i in DX.indices) {
            val neighbor = instance.getBlock(x + DX[i], y, z + DZ[i])
            if (isThisLiquid(neighbor) && getLevel(neighbor) == 0) count++
        }
        return count
    }

    private fun setLiquid(x: Int, y: Int, z: Int, level: Int) {
        val current = instance.getBlock(x, y, z)
        if (isThisLiquid(current) && getLevel(current) == level) return
        instance.setBlock(x, y, z, sourceBlock.withProperty("level", level.toString()))
    }

    private fun isThisLiquid(block: Block): Boolean = block.compare(sourceBlock)

    private fun canReplace(block: Block): Boolean =
        block.isAir || block.name() in REPLACEABLE_NAMES

    private fun getLevel(block: Block): Int =
        block.getProperty("level")?.toIntOrNull() ?: 0

    companion object {
        private const val MAX_UPDATES_PER_TICK = 2048
        private const val FALLING_LEVEL = 8

        fun packPos(x: Int, y: Int, z: Int): Long =
            (x.toLong() shl 38) or ((z.toLong() and 0x3FFFFFFL) shl 12) or (y.toLong() and 0xFFFL)

        fun unpackX(packed: Long): Int = (packed shr 38).toInt()

        fun unpackZ(packed: Long): Int {
            val raw = ((packed shr 12) and 0x3FFFFFFL).toInt()
            return if (raw and 0x2000000 != 0) raw or ((-1) shl 26) else raw
        }

        fun unpackY(packed: Long): Int {
            val raw = (packed and 0xFFFL).toInt()
            return if (raw and 0x800 != 0) raw or ((-1) shl 12) else raw
        }
    }
}
