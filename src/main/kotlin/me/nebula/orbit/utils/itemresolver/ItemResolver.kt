package me.nebula.orbit.utils.itemresolver

import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

val ITEM_ID_TAG: Tag<String> = Tag.String("custom_item_id")

object ItemResolver {

    fun resolve(key: String, amount: Int = 1): ItemStack {
        CustomItemRegistry[key]?.let { return it.createStack(amount) }
        val material = Material.fromKey(key) ?: error("Unknown item key: $key")
        return ItemStack.of(material, amount)
    }

    fun resolveMaterial(key: String): Material {
        CustomItemRegistry[key]?.let { return it.baseMaterial }
        return Material.fromKey(key) ?: error("Unknown item key: $key")
    }

    fun isCustom(stack: ItemStack): Boolean = stack.getTag(ITEM_ID_TAG) != null

    fun customId(stack: ItemStack): String? = stack.getTag(ITEM_ID_TAG)
}
