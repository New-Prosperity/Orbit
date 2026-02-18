package me.nebula.orbit.utils.itembuilder

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData
import net.minestom.server.utils.Unit as MinestomUnit

private val miniMessage = MiniMessage.miniMessage()

class ItemBuilder @PublishedApi internal constructor(private val material: Material) {

    @PublishedApi internal var amount = 1
    @PublishedApi internal var displayName: Component? = null
    @PublishedApi internal val lore = mutableListOf<Component>()
    @PublishedApi internal var customModelData: Int? = null
    @PublishedApi internal var unbreakable = false
    @PublishedApi internal var glowing = false

    fun amount(amount: Int) { this.amount = amount }
    fun name(text: String) { displayName = miniMessage.deserialize(text) }
    fun name(component: Component) { displayName = component }
    fun lore(text: String) { lore.add(miniMessage.deserialize(text)) }
    fun lore(component: Component) { lore.add(component) }
    fun lore(lines: List<String>) { lines.forEach { lore.add(miniMessage.deserialize(it)) } }
    fun customModelData(value: Int) { customModelData = value }
    fun unbreakable() { unbreakable = true }
    fun glowing() { glowing = true }

    @PublishedApi internal fun build(): ItemStack {
        var item = ItemStack.of(material, amount)
        displayName?.let { item = item.with(DataComponents.CUSTOM_NAME, it) }
        if (lore.isNotEmpty()) item = item.with(DataComponents.LORE, lore.toList())
        customModelData?.let { item = item.with(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(listOf(it.toFloat()), emptyList(), emptyList(), emptyList())) }
        if (unbreakable) item = item.with(DataComponents.UNBREAKABLE, MinestomUnit.INSTANCE)
        if (glowing) item = item.with(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        return item
    }
}

inline fun itemStack(material: Material, block: ItemBuilder.() -> Unit = {}): ItemStack =
    ItemBuilder(material).apply(block).build()

inline fun itemStack(material: Material, amount: Int, block: ItemBuilder.() -> Unit = {}): ItemStack =
    ItemBuilder(material).apply { amount(amount) }.apply(block).build()
