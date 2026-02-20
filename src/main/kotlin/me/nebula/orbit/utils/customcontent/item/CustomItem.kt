package me.nebula.orbit.utils.customcontent.item

import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

data class CustomItem(
    val id: String,
    val baseMaterial: Material,
    val customModelDataId: Int,
    val displayName: String?,
    val lore: List<String>,
    val unbreakable: Boolean,
    val glowing: Boolean,
    val maxStackSize: Int,
    val modelPath: String,
) {

    fun createStack(amount: Int = 1): ItemStack {
        var stack = itemStack(baseMaterial, amount) {
            this@CustomItem.displayName?.let { name(it) }
            if (this@CustomItem.lore.isNotEmpty()) lore(this@CustomItem.lore)
            customModelData(customModelDataId)
            if (this@CustomItem.unbreakable) unbreakable()
            if (this@CustomItem.glowing) glowing()
        }
        if (maxStackSize != baseMaterial.maxStackSize()) {
            stack = stack.with(DataComponents.MAX_STACK_SIZE, maxStackSize)
        }
        return stack
    }
}
