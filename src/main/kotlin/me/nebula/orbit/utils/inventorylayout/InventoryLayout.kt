package me.nebula.orbit.utils.inventorylayout

import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

typealias ClickHandler = (Player, Int) -> Unit

data class LayoutSlot(
    val item: ItemStack,
    val onClick: ClickHandler? = null,
)

class InventoryLayout(
    val title: Component,
    val type: InventoryType,
    val slots: Map<Int, LayoutSlot>,
    val fillEmpty: ItemStack? = null,
    val cancelClicks: Boolean = true,
) {
    fun open(player: Player) {
        val inventory = Inventory(type, title)

        if (fillEmpty != null) {
            for (i in 0 until inventory.size) {
                inventory.setItemStack(i, fillEmpty)
            }
        }

        slots.forEach { (slot, layoutSlot) ->
            inventory.setItemStack(slot, layoutSlot.item)
        }

        val node = EventNode.all("layout-${System.identityHashCode(inventory)}")
        node.addListener(InventoryPreClickEvent::class.java) { event ->
            val clicked = event.inventory ?: return@addListener
            if (clicked !== inventory) return@addListener
            if (cancelClicks) event.isCancelled = true
            slots[event.slot]?.onClick?.invoke(event.player, event.slot)
        }
        node.addListener(InventoryCloseEvent::class.java) { event ->
            if (event.inventory !== inventory) return@addListener
            MinecraftServer.getGlobalEventHandler().removeChild(node)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)

        player.openInventory(inventory)
    }
}

class InventoryLayoutBuilder {
    var title: Component = Component.empty()
    var type: InventoryType = InventoryType.CHEST_3_ROW
    var fillEmpty: ItemStack? = null
    var cancelClicks: Boolean = true
    private val slots = mutableMapOf<Int, LayoutSlot>()

    fun slot(index: Int, item: ItemStack, onClick: ClickHandler? = null) {
        slots[index] = LayoutSlot(item, onClick)
    }

    fun border(item: ItemStack) {
        val rows = type.size / 9
        for (i in 0 until 9) {
            slots.putIfAbsent(i, LayoutSlot(item))
            slots.putIfAbsent((rows - 1) * 9 + i, LayoutSlot(item))
        }
        for (row in 1 until rows - 1) {
            slots.putIfAbsent(row * 9, LayoutSlot(item))
            slots.putIfAbsent(row * 9 + 8, LayoutSlot(item))
        }
    }

    fun row(rowIndex: Int, item: ItemStack, onClick: ClickHandler? = null) {
        for (col in 0 until 9) {
            slots[rowIndex * 9 + col] = LayoutSlot(item, onClick)
        }
    }

    fun column(colIndex: Int, item: ItemStack, onClick: ClickHandler? = null) {
        val rows = type.size / 9
        for (row in 0 until rows) {
            slots[row * 9 + colIndex] = LayoutSlot(item, onClick)
        }
    }

    fun pattern(pattern: List<String>, mappings: Map<Char, LayoutSlot>) {
        pattern.forEachIndexed { row, line ->
            line.forEachIndexed { col, char ->
                if (char != ' ') {
                    mappings[char]?.let { slots[row * 9 + col] = it }
                }
            }
        }
    }

    fun centerItems(items: List<Pair<ItemStack, ClickHandler?>>, rowIndex: Int = 1) {
        val startCol = (9 - items.size) / 2
        items.forEachIndexed { index, (item, onClick) ->
            slots[rowIndex * 9 + startCol + index] = LayoutSlot(item, onClick)
        }
    }

    fun build(): InventoryLayout = InventoryLayout(title, type, slots.toMap(), fillEmpty, cancelClicks)
}

inline fun inventoryLayout(block: InventoryLayoutBuilder.() -> Unit): InventoryLayout =
    InventoryLayoutBuilder().apply(block).build()

fun fillerItem(material: Material = Material.GRAY_STAINED_GLASS_PANE): ItemStack =
    ItemStack.of(material).with { builder ->
        builder.set(net.minestom.server.component.DataComponents.CUSTOM_NAME, Component.empty())
    }
