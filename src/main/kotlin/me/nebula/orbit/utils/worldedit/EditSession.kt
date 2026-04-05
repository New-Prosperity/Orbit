package me.nebula.orbit.utils.worldedit

import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.util.concurrent.CompletableFuture

data class EditResult(val blocksChanged: Int, val durationMs: Long)

class EditSession(
    val instance: Instance,
    val player: Player? = null,
    private val limit: Int = 500_000,
) : AutoCloseable {

    private val queue = ChunkBatchQueue(instance)
    private val changeSet = ChangeSet()
    private var blockCount = 0
    private val startTime = System.currentTimeMillis()

    val changes: Int get() = blockCount

    fun setBlock(x: Int, y: Int, z: Int, block: Block) {
        if (blockCount >= limit) return
        val currentBlock = instance.getBlock(x, y, z)
        if (currentBlock.stateId() == block.stateId()) return
        changeSet.add(x, y, z, currentBlock.stateId().toShort(), block.stateId().toShort())
        queue.setBlock(x, y, z, block.stateId())
        blockCount++
        if (queue.shouldFlush()) queue.flush().join()
    }

    fun setBlock(x: Int, y: Int, z: Int, pattern: Pattern) {
        setBlock(x, y, z, Block.fromStateId(pattern.apply(x, y, z).toInt()) ?: Block.AIR)
    }

    fun setBlockFast(x: Int, y: Int, z: Int, stateId: Int) {
        if (blockCount >= limit) return
        val currentId = instance.getBlock(x, y, z).stateId()
        if (currentId == stateId) return
        changeSet.add(x, y, z, currentId.toShort(), stateId.toShort())
        queue.setBlock(x, y, z, stateId)
        blockCount++
    }

    fun getBlock(x: Int, y: Int, z: Int): Block {
        val buffered = queue.getBuffered(x, y, z)
        if (buffered >= 0) return Block.fromStateId(buffered.toInt()) ?: Block.AIR
        return instance.getBlock(x, y, z)
    }

    fun commit(): CompletableFuture<EditResult> {
        changeSet.close()
        return queue.flush().thenApply {
            EditResult(blockCount, System.currentTimeMillis() - startTime)
        }
    }

    fun changeSet(): ChangeSet = changeSet

    fun cancel() {
        changeSet.close()
    }

    override fun close() {
        commit()
    }
}
