package me.nebula.orbit.utils.customcontent.block

import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import me.nebula.orbit.utils.customcontent.item.CustomItem
import me.nebula.orbit.utils.loot.LootTable
import net.minestom.server.instance.block.Block

data class CustomBlock(
    val id: String,
    val hitbox: BlockHitbox,
    val itemId: String,
    val customModelDataId: Int,
    val hardness: Float,
    val drops: CustomBlockDrops,
    val modelPath: String,
    val placeSound: String,
    val breakSound: String,
    val allocatedState: Block,
) {

    fun item(): CustomItem = CustomItemRegistry.require(itemId)
}

sealed class CustomBlockDrops {
    data object SelfDrop : CustomBlockDrops()
    data class LootTableDrop(val lootTable: LootTable) : CustomBlockDrops()
}
