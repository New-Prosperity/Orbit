package me.nebula.orbit.utils.customcontent.block

import net.minestom.server.instance.block.Block
import java.util.concurrent.ConcurrentHashMap

object CustomBlockRegistry {

    private val byId = ConcurrentHashMap<String, CustomBlock>()
    private val byStateId = ConcurrentHashMap<Int, CustomBlock>()
    private val byItemId = ConcurrentHashMap<String, CustomBlock>()

    fun register(block: CustomBlock) {
        require(!byId.containsKey(block.id)) { "Custom block already registered: ${block.id}" }
        byId[block.id] = block
        byStateId[block.allocatedState.stateId()] = block
        byItemId[block.itemId] = block
    }

    operator fun get(id: String): CustomBlock? = byId[id]

    fun require(id: String): CustomBlock =
        byId[id] ?: error("Unknown custom block: $id")

    fun fromVanillaBlock(block: Block): CustomBlock? =
        byStateId[block.stateId()]

    fun fromItemId(itemId: String): CustomBlock? =
        byItemId[itemId]

    fun all(): Collection<CustomBlock> = byId.values

    fun isEmpty(): Boolean = byId.isEmpty()

    fun clear() {
        byId.clear()
        byStateId.clear()
        byItemId.clear()
    }
}
