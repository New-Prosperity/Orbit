package me.nebula.orbit.utils.worldedit

import me.nebula.orbit.utils.region.Region
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.random.Random

enum class Axis { X, Y, Z }

data class ClipboardData(
    val blocks: Map<Long, Block>,
    val width: Int,
    val height: Int,
    val depth: Int,
) {
    companion object {
        fun packCoord(x: Int, y: Int, z: Int): Long =
            (x.toLong() and 0x1FFFFF) or
            ((y.toLong() and 0x1FFFFF) shl 21) or
            ((z.toLong() and 0x1FFFFF) shl 42)

        fun unpackX(packed: Long): Int = ((packed and 0x1FFFFF).toInt()).let {
            if (it and 0x100000 != 0) it or (-1 shl 21) else it
        }

        fun unpackY(packed: Long): Int = (((packed shr 21) and 0x1FFFFF).toInt()).let {
            if (it and 0x100000 != 0) it or (-1 shl 21) else it
        }

        fun unpackZ(packed: Long): Int = (((packed shr 42) and 0x1FFFFF).toInt()).let {
            if (it and 0x100000 != 0) it or (-1 shl 21) else it
        }
    }
}

data class WeightedBlock(val block: Block, val weight: Double)

class PatternBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val entries = mutableListOf<WeightedBlock>()

    fun random(vararg blocks: Block) {
        val weight = 1.0 / blocks.size
        blocks.forEach { entries.add(WeightedBlock(it, weight)) }
    }

    fun random(block: Block, weight: Double) {
        entries.add(WeightedBlock(block, weight))
    }

    @PublishedApi internal fun pick(): Block {
        require(entries.isNotEmpty()) { "Pattern requires at least one block" }
        val totalWeight = entries.sumOf { it.weight }
        var roll = Random.nextDouble() * totalWeight
        for (entry in entries) {
            roll -= entry.weight
            if (roll <= 0.0) return entry.block
        }
        return entries.last().block
    }
}

private data class UndoEntry(
    val blocks: Map<Long, Block>,
    val instance: Instance,
)

object WorldEdit {

    private val undoStacks = ConcurrentHashMap<UUID, ConcurrentLinkedDeque<UndoEntry>>()
    private val redoStacks = ConcurrentHashMap<UUID, ConcurrentLinkedDeque<UndoEntry>>()
    private const val MAX_HISTORY = 50

    fun copy(instance: Instance, region: Region): ClipboardData {
        val blocks = mutableMapOf<Long, Block>()
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE

        forEachBlockInRegion(instance, region) { x, y, z, _ ->
            if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z
            if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z
        }

        val baseX = regionMinX(region)
        val baseY = regionMinY(region)
        val baseZ = regionMinZ(region)

        forEachBlockInRegion(instance, region) { x, y, z, block ->
            if (!block.isAir) {
                blocks[ClipboardData.packCoord(x - baseX, y - baseY, z - baseZ)] = block
            }
        }

        return ClipboardData(
            blocks = blocks.toMap(),
            width = (maxX - minX + 1).coerceAtLeast(0),
            height = (maxY - minY + 1).coerceAtLeast(0),
            depth = (maxZ - minZ + 1).coerceAtLeast(0),
        )
    }

    fun paste(clipboard: ClipboardData, instance: Instance, origin: Point, player: Player? = null) {
        val previous = mutableMapOf<Long, Block>()
        clipboard.blocks.forEach { (packed, block) ->
            val x = ClipboardData.unpackX(packed) + origin.blockX()
            val y = ClipboardData.unpackY(packed) + origin.blockY()
            val z = ClipboardData.unpackZ(packed) + origin.blockZ()
            val worldPacked = ClipboardData.packCoord(x, y, z)
            previous[worldPacked] = instance.getBlock(x, y, z)
            instance.setBlock(x, y, z, block)
        }
        player?.let { pushUndo(it, UndoEntry(previous, instance)) }
    }

    fun rotate(clipboard: ClipboardData, degrees: Int): ClipboardData {
        require(degrees in setOf(90, 180, 270)) { "Rotation must be 90, 180, or 270 degrees" }
        val rotated = mutableMapOf<Long, Block>()
        val steps = degrees / 90
        clipboard.blocks.forEach { (packed, block) ->
            var x = ClipboardData.unpackX(packed)
            var z = ClipboardData.unpackZ(packed)
            val y = ClipboardData.unpackY(packed)
            repeat(steps) {
                val newX = -z
                val newZ = x
                x = newX; z = newZ
            }
            rotated[ClipboardData.packCoord(x, y, z)] = block
        }
        val newWidth = if (degrees == 180) clipboard.width else clipboard.depth
        val newDepth = if (degrees == 180) clipboard.depth else clipboard.width
        return ClipboardData(rotated, newWidth, clipboard.height, newDepth)
    }

    fun flip(clipboard: ClipboardData, axis: Axis): ClipboardData {
        val flipped = mutableMapOf<Long, Block>()
        clipboard.blocks.forEach { (packed, block) ->
            val x = ClipboardData.unpackX(packed)
            val y = ClipboardData.unpackY(packed)
            val z = ClipboardData.unpackZ(packed)
            val newPacked = when (axis) {
                Axis.X -> ClipboardData.packCoord(-x, y, z)
                Axis.Y -> ClipboardData.packCoord(x, -y, z)
                Axis.Z -> ClipboardData.packCoord(x, y, -z)
            }
            flipped[newPacked] = block
        }
        return ClipboardData(flipped, clipboard.width, clipboard.height, clipboard.depth)
    }

    fun fill(instance: Instance, region: Region, block: Block, player: Player? = null) {
        val previous = mutableMapOf<Long, Block>()
        forEachBlockInRegion(instance, region) { x, y, z, existing ->
            val packed = ClipboardData.packCoord(x, y, z)
            previous[packed] = existing
            instance.setBlock(x, y, z, block)
        }
        player?.let { pushUndo(it, UndoEntry(previous, instance)) }
    }

    fun replace(instance: Instance, region: Region, from: Block, to: Block, player: Player? = null) {
        val previous = mutableMapOf<Long, Block>()
        forEachBlockInRegion(instance, region) { x, y, z, existing ->
            if (existing.compare(from)) {
                val packed = ClipboardData.packCoord(x, y, z)
                previous[packed] = existing
                instance.setBlock(x, y, z, to)
            }
        }
        player?.let { pushUndo(it, UndoEntry(previous, instance)) }
    }

    fun fillPattern(instance: Instance, region: Region, pattern: PatternBuilder, player: Player? = null): Int {
        val previous = mutableMapOf<Long, Block>()
        var count = 0
        forEachBlockInRegion(instance, region) { x, y, z, existing ->
            val packed = ClipboardData.packCoord(x, y, z)
            previous[packed] = existing
            instance.setBlock(x, y, z, pattern.pick())
            count++
        }
        player?.let { pushUndo(it, UndoEntry(previous, instance)) }
        return count
    }

    fun undo(player: Player) {
        val stack = undoStacks[player.uuid] ?: return
        val entry = stack.pollFirst() ?: return
        val instance = entry.instance
        val redo = mutableMapOf<Long, Block>()
        entry.blocks.forEach { (packed, block) ->
            val x = ClipboardData.unpackX(packed)
            val y = ClipboardData.unpackY(packed)
            val z = ClipboardData.unpackZ(packed)
            redo[packed] = instance.getBlock(x, y, z)
            instance.setBlock(x, y, z, block)
        }
        pushRedo(player, UndoEntry(redo, instance))
    }

    fun redo(player: Player) {
        val stack = redoStacks[player.uuid] ?: return
        val entry = stack.pollFirst() ?: return
        val instance = entry.instance
        val undoMap = mutableMapOf<Long, Block>()
        entry.blocks.forEach { (packed, block) ->
            val x = ClipboardData.unpackX(packed)
            val y = ClipboardData.unpackY(packed)
            val z = ClipboardData.unpackZ(packed)
            undoMap[packed] = instance.getBlock(x, y, z)
            instance.setBlock(x, y, z, block)
        }
        pushUndo(player, UndoEntry(undoMap, instance))
    }

    fun clearHistory(player: Player) {
        undoStacks.remove(player.uuid)
        redoStacks.remove(player.uuid)
    }

    private fun pushUndo(player: Player, entry: UndoEntry) {
        val stack = undoStacks.computeIfAbsent(player.uuid) { ConcurrentLinkedDeque() }
        stack.addFirst(entry)
        while (stack.size > MAX_HISTORY) stack.removeLast()
        redoStacks[player.uuid]?.clear()
    }

    private fun pushRedo(player: Player, entry: UndoEntry) {
        val stack = redoStacks.computeIfAbsent(player.uuid) { ConcurrentLinkedDeque() }
        stack.addFirst(entry)
        while (stack.size > MAX_HISTORY) stack.removeLast()
    }

    private inline fun forEachBlockInRegion(
        instance: Instance,
        region: Region,
        action: (Int, Int, Int, Block) -> Unit,
    ) {
        val minX = regionMinX(region)
        val minY = regionMinY(region)
        val minZ = regionMinZ(region)
        val maxX = regionMaxX(region)
        val maxY = regionMaxY(region)
        val maxZ = regionMaxZ(region)
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    if (region.contains(x.toDouble() + 0.5, y.toDouble() + 0.5, z.toDouble() + 0.5)) {
                        action(x, y, z, instance.getBlock(x, y, z))
                    }
                }
            }
        }
    }

    private fun regionMinX(region: Region): Int = when (region) {
        is me.nebula.orbit.utils.region.CuboidRegion -> region.min.blockX()
        is me.nebula.orbit.utils.region.SphereRegion -> (region.center.x() - region.radius).toInt()
        is me.nebula.orbit.utils.region.CylinderRegion -> (region.center.x() - region.radius).toInt()
    }

    private fun regionMinY(region: Region): Int = when (region) {
        is me.nebula.orbit.utils.region.CuboidRegion -> region.min.blockY()
        is me.nebula.orbit.utils.region.SphereRegion -> (region.center.y() - region.radius).toInt()
        is me.nebula.orbit.utils.region.CylinderRegion -> region.center.blockY()
    }

    private fun regionMinZ(region: Region): Int = when (region) {
        is me.nebula.orbit.utils.region.CuboidRegion -> region.min.blockZ()
        is me.nebula.orbit.utils.region.SphereRegion -> (region.center.z() - region.radius).toInt()
        is me.nebula.orbit.utils.region.CylinderRegion -> (region.center.z() - region.radius).toInt()
    }

    private fun regionMaxX(region: Region): Int = when (region) {
        is me.nebula.orbit.utils.region.CuboidRegion -> region.max.blockX()
        is me.nebula.orbit.utils.region.SphereRegion -> (region.center.x() + region.radius).toInt()
        is me.nebula.orbit.utils.region.CylinderRegion -> (region.center.x() + region.radius).toInt()
    }

    private fun regionMaxY(region: Region): Int = when (region) {
        is me.nebula.orbit.utils.region.CuboidRegion -> region.max.blockY()
        is me.nebula.orbit.utils.region.SphereRegion -> (region.center.y() + region.radius).toInt()
        is me.nebula.orbit.utils.region.CylinderRegion -> (region.center.y() + region.height).toInt()
    }

    private fun regionMaxZ(region: Region): Int = when (region) {
        is me.nebula.orbit.utils.region.CuboidRegion -> region.max.blockZ()
        is me.nebula.orbit.utils.region.SphereRegion -> (region.center.z() + region.radius).toInt()
        is me.nebula.orbit.utils.region.CylinderRegion -> (region.center.z() + region.radius).toInt()
    }
}

fun Instance.copyRegion(region: Region): ClipboardData = WorldEdit.copy(this, region)
fun Instance.fillRegion(region: Region, block: Block, player: Player? = null) = WorldEdit.fill(this, region, block, player)
fun Instance.replaceRegion(region: Region, from: Block, to: Block, player: Player? = null) = WorldEdit.replace(this, region, from, to, player)

inline fun Instance.fillPattern(region: Region, player: Player? = null, block: PatternBuilder.() -> Unit): Int =
    WorldEdit.fillPattern(this, region, PatternBuilder().apply(block), player)

fun Player.undoEdit() = WorldEdit.undo(this)
fun Player.redoEdit() = WorldEdit.redo(this)
