package me.nebula.orbit.utils.itembuilder

import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.tooltip.withTooltipStyle
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.color.Color
import net.minestom.server.component.DataComponent
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.AttributeList
import net.minestom.server.item.component.CustomModelData
import net.minestom.server.item.component.EnchantmentList
import net.minestom.server.item.component.TooltipDisplay
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.ResolvableProfile
import net.minestom.server.registry.RegistryKey
import net.minestom.server.utils.Unit as MinestomUnit
import java.util.UUID

private fun Component.noItalic(): Component =
    decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

class ItemBuilder @PublishedApi internal constructor(private val material: Material) {

    @PublishedApi internal var amount = 1
    @PublishedApi internal var displayName: Component? = null
    @PublishedApi internal val lore = mutableListOf<Component>()
    @PublishedApi internal var customModelData: Int? = null
    @PublishedApi internal var unbreakable = false
    @PublishedApi internal var glowing = false
    @PublishedApi internal var maxStackSize: Int? = null
    @PublishedApi internal var dyeColor: Color? = null
    @PublishedApi internal var damage: Int? = null
    @PublishedApi internal var tooltipStyle: String? = null
    @PublishedApi internal var hideTooltip = false
    @PublishedApi internal val hiddenComponents = mutableSetOf<DataComponent<*>>()
    @PublishedApi internal var stripAttributes = false
    @PublishedApi internal val enchantments = mutableListOf<Pair<RegistryKey<Enchantment>, Int>>()
    @PublishedApi internal var skullTextures: String? = null
    @PublishedApi internal var itemModel: String? = null

    fun amount(amount: Int) { this.amount = amount }

    fun name(text: String) { displayName = miniMessage.deserialize(text) }
    fun name(component: Component) { displayName = component }

    fun lore(text: String) { lore.add(miniMessage.deserialize(text)) }
    fun lore(component: Component) { lore.add(component) }
    fun lore(lines: List<String>) { lines.forEach { lore.add(miniMessage.deserialize(it)) } }
    fun emptyLoreLine() { lore.add(Component.empty()) }

    fun customModelData(value: Int) { customModelData = value }
    fun itemModel(value: String) { itemModel = value }
    fun maxStackSize(size: Int) { maxStackSize = size }

    fun unbreakable() { unbreakable = true }
    fun glowing() { glowing = true }

    fun enchant(enchantment: RegistryKey<Enchantment>, level: Int = 1) {
        enchantments += enchantment to level
    }

    fun color(r: Int, g: Int, b: Int) { dyeColor = Color(r, g, b) }
    fun color(rgb: Int) { dyeColor = Color(rgb) }
    fun color(color: Color) { dyeColor = color }

    fun damage(value: Int) { damage = value }

    fun tooltipStyle(styleId: String) { tooltipStyle = styleId }

    fun skull(texturesBase64: String) { skullTextures = texturesBase64 }

    fun hideTooltip() { hideTooltip = true }
    fun hideAttributes() { hiddenComponents += DataComponents.ATTRIBUTE_MODIFIERS }
    fun hideEnchants() { hiddenComponents += DataComponents.ENCHANTMENTS }
    fun hideUnbreakable() { hiddenComponents += DataComponents.UNBREAKABLE }
    fun hideTrim() { hiddenComponents += DataComponents.TRIM }
    fun hideCanBreak() { hiddenComponents += DataComponents.CAN_BREAK }
    fun hideCanPlace() { hiddenComponents += DataComponents.CAN_PLACE_ON }
    fun hideStoredEnchants() { hiddenComponents += DataComponents.STORED_ENCHANTMENTS }
    fun hideDye() { hiddenComponents += DataComponents.DYED_COLOR }

    fun hideAll() {
        hiddenComponents += DataComponents.ATTRIBUTE_MODIFIERS
        hiddenComponents += DataComponents.ENCHANTMENTS
        hiddenComponents += DataComponents.UNBREAKABLE
        hiddenComponents += DataComponents.TRIM
        hiddenComponents += DataComponents.CAN_BREAK
        hiddenComponents += DataComponents.CAN_PLACE_ON
        hiddenComponents += DataComponents.STORED_ENCHANTMENTS
        hiddenComponents += DataComponents.DYED_COLOR
    }

    fun clean() {
        unbreakable = true
        stripAttributes = true
        hideAll()
    }

    @PublishedApi internal fun build(): ItemStack {
        var item = ItemStack.of(material, amount)
        displayName?.let { item = item.with(DataComponents.CUSTOM_NAME, it.noItalic()) }
        if (lore.isNotEmpty()) item = item.with(DataComponents.LORE, lore.map { it.noItalic() })
        customModelData?.let { item = item.with(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(listOf(it.toFloat()), emptyList(), emptyList(), emptyList())) }
        itemModel?.let { item = item.with(DataComponents.ITEM_MODEL, it) }
        maxStackSize?.let { item = item.with(DataComponents.MAX_STACK_SIZE, it) }
        if (unbreakable) item = item.with(DataComponents.UNBREAKABLE, MinestomUnit.INSTANCE)
        if (glowing) item = item.with(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        dyeColor?.let { item = item.with(DataComponents.DYED_COLOR, it) }
        damage?.let { item = item.with(DataComponents.DAMAGE, it) }
        tooltipStyle?.let { item = item.withTooltipStyle(it) }

        if (enchantments.isNotEmpty()) {
            var enchantList = item.get(DataComponents.ENCHANTMENTS) ?: EnchantmentList.EMPTY
            for ((ench, level) in enchantments) {
                enchantList = enchantList.with(ench, level)
            }
            item = item.with(DataComponents.ENCHANTMENTS, enchantList)
        }

        skullTextures?.let { textures ->
            val profile = GameProfile(UUID(0, 0), "skull", listOf(
                GameProfile.Property("textures", textures, null)
            ))
            item = item.with(DataComponents.PROFILE, ResolvableProfile(profile))
        }

        if (stripAttributes) {
            item = item.with(DataComponents.ATTRIBUTE_MODIFIERS, AttributeList.EMPTY)
        }

        if (hideTooltip || hiddenComponents.isNotEmpty()) {
            item = item.with(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay(hideTooltip, hiddenComponents.toSet()))
        }

        return item
    }
}

inline fun itemStack(material: Material, block: ItemBuilder.() -> Unit = {}): ItemStack =
    ItemBuilder(material).apply(block).build()

inline fun itemStack(material: Material, amount: Int, block: ItemBuilder.() -> Unit = {}): ItemStack =
    ItemBuilder(material).apply { amount(amount) }.apply(block).build()

fun guiItem(material: Material, name: String, vararg loreLines: String): ItemStack =
    itemStack(material) {
        name(name)
        loreLines.forEach { lore(it) }
        clean()
    }

fun guiItem(material: Material, name: String, block: ItemBuilder.() -> Unit): ItemStack =
    itemStack(material) {
        name(name)
        clean()
        block()
    }
