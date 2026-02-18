package me.nebula.orbit.utils.blocksnapshot

import me.nebula.orbit.utils.region.Region
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

data class CaptureRegion(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
) {
    val volume: Long get() = (maxX - minX + 1).toLong() * (maxY - minY + 1) * (maxZ - minZ + 1)
}

data class BlockChange(
    val x: Int,
    val y: Int,
    val z: Int,
    val before: Block,
    val after: Block,
)

class BlockSnapshot(
    private val blocks: ConcurrentHashMap<Long, Block>,
    val width: Int,
    val height: Int,
    val depth: Int,
) {

    val blockCount: Int get() = blocks.size

    fun restore(instance: Instance) {
        blocks.forEach { (packed, block) ->
            val (x, y, z) = unpackCoords(packed)
            instance.setBlock(x, y, z, block)
        }
    }

    fun restoreAsync(instance: Instance): CompletableFuture<Void> =
        CompletableFuture.runAsync { restore(instance) }

    fun diff(instance: Instance): List<BlockChange> = buildList {
        blocks.forEach { (packed, savedBlock) ->
            val (x, y, z) = unpackCoords(packed)
            val currentBlock = instance.getBlock(x, y, z)
            if (currentBlock.id() != savedBlock.id()) {
                add(BlockChange(x, y, z, savedBlock, currentBlock))
            }
        }
    }

    fun pasteAt(instance: Instance, offset: Point) {
        blocks.forEach { (packed, block) ->
            val (x, y, z) = unpackCoords(packed)
            instance.setBlock(
                x + offset.blockX(),
                y + offset.blockY(),
                z + offset.blockZ(),
                block,
            )
        }
    }

    fun createInstance(): InstanceContainer {
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        blocks.forEach { (packed, block) ->
            val (x, y, z) = unpackCoords(packed)
            instance.setBlock(x, y, z, block)
        }
        return instance
    }

    fun getBlock(x: Int, y: Int, z: Int): Block? =
        blocks[packCoords(x, y, z)]

    companion object {

        fun capture(instance: Instance, region: CaptureRegion): BlockSnapshot {
            val blocks = ConcurrentHashMap<Long, Block>()
            for (x in region.minX..region.maxX) {
                for (y in region.minY..region.maxY) {
                    for (z in region.minZ..region.maxZ) {
                        blocks[packCoords(x, y, z)] = instance.getBlock(x, y, z)
                    }
                }
            }
            return BlockSnapshot(
                blocks,
                region.maxX - region.minX + 1,
                region.maxY - region.minY + 1,
                region.maxZ - region.minZ + 1,
            )
        }

        fun capture(instance: Instance, min: Point, max: Point): BlockSnapshot =
            capture(instance, captureRegion(min, max))

        fun capture(instance: Instance, region: Region): BlockSnapshot {
            val blocks = ConcurrentHashMap<Long, Block>()
            var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE

            forEachBlockInRegion(region) { x, y, z ->
                val block = instance.getBlock(x, y, z)
                if (!block.isAir) {
                    blocks[packCoords(x, y, z)] = block
                    minX = minOf(minX, x); minY = minOf(minY, y); minZ = minOf(minZ, z)
                    maxX = maxOf(maxX, x); maxY = maxOf(maxY, y); maxZ = maxOf(maxZ, z)
                }
            }

            val width = if (blocks.isEmpty()) 0 else maxX - minX + 1
            val height = if (blocks.isEmpty()) 0 else maxY - minY + 1
            val depth = if (blocks.isEmpty()) 0 else maxZ - minZ + 1

            return BlockSnapshot(blocks, width, height, depth)
        }

        fun captureAsync(instance: Instance, region: CaptureRegion): CompletableFuture<BlockSnapshot> =
            CompletableFuture.supplyAsync { capture(instance, region) }

        private fun forEachBlockInRegion(region: Region, action: (Int, Int, Int) -> Unit) {
            when (region) {
                is me.nebula.orbit.utils.region.CuboidRegion -> {
                    for (x in region.min.blockX()..region.max.blockX()) {
                        for (y in region.min.blockY()..region.max.blockY()) {
                            for (z in region.min.blockZ()..region.max.blockZ()) {
                                action(x, y, z)
                            }
                        }
                    }
                }
                is me.nebula.orbit.utils.region.SphereRegion -> {
                    val cx = region.center.blockX(); val cy = region.center.blockY(); val cz = region.center.blockZ()
                    val r = region.radius.toInt()
                    for (x in (cx - r)..(cx + r)) {
                        for (y in (cy - r)..(cy + r)) {
                            for (z in (cz - r)..(cz + r)) {
                                if (region.contains(x.toDouble(), y.toDouble(), z.toDouble())) action(x, y, z)
                            }
                        }
                    }
                }
                is me.nebula.orbit.utils.region.CylinderRegion -> {
                    val cx = region.center.blockX(); val cy = region.center.blockY(); val cz = region.center.blockZ()
                    val r = region.radius.toInt(); val h = region.height.toInt()
                    for (x in (cx - r)..(cx + r)) {
                        for (y in cy..(cy + h)) {
                            for (z in (cz - r)..(cz + r)) {
                                if (region.contains(x.toDouble(), y.toDouble(), z.toDouble())) action(x, y, z)
                            }
                        }
                    }
                }
            }
        }
    }
}

class BlockRestoreHandle @PublishedApi internal constructor(
    private val instance: Instance,
    autoRestoreAfter: Duration?,
) {

    private val originals = ConcurrentHashMap<Long, Block>()
    @Volatile private var autoTask: Task? = null
    @Volatile private var restored = false

    init {
        autoRestoreAfter?.let { duration ->
            autoTask = MinecraftServer.getSchedulerManager()
                .buildTask { restore() }
                .delay(TaskSchedule.duration(duration))
                .schedule()
        }
    }

    fun setBlock(pos: Point, block: Block) {
        check(!restored) { "Cannot modify after restore" }
        val key = packCoords(pos.blockX(), pos.blockY(), pos.blockZ())
        originals.computeIfAbsent(key) { instance.getBlock(pos) }
        instance.setBlock(pos, block)
    }

    fun restore() {
        if (restored) return
        restored = true
        autoTask?.cancel()
        originals.forEach { (packed, original) ->
            val (x, y, z) = unpackCoords(packed)
            instance.setBlock(x, y, z, original)
        }
        originals.clear()
    }

    fun restoreAsync() {
        if (restored) return
        restored = true
        autoTask?.cancel()
        Thread.startVirtualThread {
            originals.forEach { (packed, original) ->
                val (x, y, z) = unpackCoords(packed)
                instance.setBlock(x, y, z, original)
            }
            originals.clear()
        }
    }

    val isRestored: Boolean get() = restored
    val modifiedCount: Int get() = originals.size
}

class BlockRestoreBuilder @PublishedApi internal constructor(
    @PublishedApi internal val instance: Instance,
) {

    @PublishedApi internal var autoRestoreAfter: Duration? = null

    fun autoRestoreAfter(duration: Duration) { autoRestoreAfter = duration }
    fun autoRestoreAfterSeconds(seconds: Long) { autoRestoreAfter = Duration.ofSeconds(seconds) }
    fun autoRestoreAfterTicks(ticks: Int) { autoRestoreAfter = Duration.ofMillis(ticks * 50L) }

    @PublishedApi internal fun build(): BlockRestoreHandle =
        BlockRestoreHandle(instance, autoRestoreAfter)
}

inline fun blockRestore(instance: Instance, builder: BlockRestoreBuilder.() -> Unit): BlockRestoreHandle =
    BlockRestoreBuilder(instance).apply(builder).build()

fun Instance.captureSnapshot(region: CaptureRegion): BlockSnapshot =
    BlockSnapshot.capture(this, region)

fun Instance.captureSnapshot(min: Point, max: Point): BlockSnapshot =
    BlockSnapshot.capture(this, min, max)

fun Instance.captureSnapshot(region: Region): BlockSnapshot =
    BlockSnapshot.capture(this, region)

@JvmName("instanceBlockRestore")
inline fun Instance.blockRestore(builder: BlockRestoreBuilder.() -> Unit): BlockRestoreHandle =
    blockRestore(this, builder)

fun captureRegion(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): CaptureRegion =
    CaptureRegion(
        minOf(minX, maxX), minOf(minY, maxY), minOf(minZ, maxZ),
        maxOf(minX, maxX), maxOf(minY, maxY), maxOf(minZ, maxZ),
    )

fun captureRegion(min: Pos, max: Pos): CaptureRegion =
    captureRegion(
        min.blockX(), min.blockY(), min.blockZ(),
        max.blockX(), max.blockY(), max.blockZ(),
    )

fun captureRegion(min: Point, max: Point): CaptureRegion =
    captureRegion(
        min.blockX(), min.blockY(), min.blockZ(),
        max.blockX(), max.blockY(), max.blockZ(),
    )

private fun packCoords(x: Int, y: Int, z: Int): Long =
    (x.toLong() and 0x3FFFFFF shl 38) or (z.toLong() and 0x3FFFFFF shl 12) or (y.toLong() and 0xFFF)

private data class UnpackedCoords(val x: Int, val y: Int, val z: Int)

private fun unpackCoords(packed: Long): UnpackedCoords {
    val x = (packed shr 38).toInt()
    val z = ((packed shr 12) and 0x3FFFFFF).toInt()
    val y = (packed and 0xFFF).toInt().let { if (it >= 2048) it - 4096 else it }
    return UnpackedCoords(x, y, z)
}
