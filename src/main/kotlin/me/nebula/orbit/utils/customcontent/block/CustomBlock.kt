package me.nebula.orbit.utils.customcontent.block

import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import me.nebula.orbit.utils.customcontent.item.CustomItem
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.loot.LootTable
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

data class CustomBlock(
    val id: String,
    val hitbox: BlockHitbox,
    val itemId: String,
    val customModelDataId: Int,
    val hardness: Float,
    val drops: CustomBlockDrops,
    val placeSound: String,
    val breakSound: String,
    val allocatedState: Block,
    val miningBlock: Block? = null,
    val mapColor: Int? = null,
) {

    fun item(): CustomItem? = CustomItemRegistry[itemId]

    fun givenStack(amount: Int = 1): ItemStack {
        CustomItemRegistry[id]?.let { return it.createStack(amount) }
        return itemStack(Material.PAPER, amount) {
            name(id)
            customModelData(customModelDataId)
        }
    }
}

sealed class CustomBlockDrops {
    data object SelfDrop : CustomBlockDrops()
    data class LootTableDrop(val lootTable: LootTable) : CustomBlockDrops()
}
