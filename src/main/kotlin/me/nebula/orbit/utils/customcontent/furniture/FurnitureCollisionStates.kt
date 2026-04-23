package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.customcontent.block.BlockHitbox
import me.nebula.orbit.utils.customcontent.block.BlockStateAllocator
import net.minestom.server.instance.block.Block
import java.util.concurrent.ConcurrentHashMap

object FurnitureCollisionStates {

    private val logger = logger("FurnitureCollisionStates")
    private val cache = ConcurrentHashMap<BlockHitbox, Block>()

    fun resolve(hitbox: BlockHitbox): Block {
        if (hitbox == BlockHitbox.Full) return Block.BARRIER
        return cache.getOrPut(hitbox) {
            val id = "furniture:collision:${hitbox.name.lowercase()}"
            val state = BlockStateAllocator.allocate(id, hitbox)
            logger.info { "Allocated furniture collision state for ${hitbox.name}: $state" }
            state
        }
    }

    fun allocations(): Map<BlockHitbox, Block> = cache.toMap()

    fun isFurnitureCollisionBlock(block: Block): Boolean {
        if (block.compare(Block.BARRIER)) return true
        return cache.values.any { it.stateId() == block.stateId() }
    }

    fun clear() { cache.clear() }
}
