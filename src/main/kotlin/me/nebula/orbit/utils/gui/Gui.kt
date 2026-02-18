package me.nebula.orbit.utils.gui

import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

private val miniMessage = MiniMessage.miniMessage()

private fun rowsToType(rows: Int): InventoryType = when (rows) {
    1 -> InventoryType.CHEST_1_ROW
    2 -> InventoryType.CHEST_2_ROW
    3 -> InventoryType.CHEST_3_ROW
    4 -> InventoryType.CHEST_4_ROW
    5 -> InventoryType.CHEST_5_ROW
    6 -> InventoryType.CHEST_6_ROW
    else -> error("Invalid row count: $rows (must be 1-6)")
}

data class GuiSlot(val item: ItemStack, val onClick: (Player) -> Unit = {})

class Gui(
    private val title: String,
    private val type: InventoryType,
    private val slots: Map<Int, GuiSlot>,
    private val fillItem: ItemStack?,
    private val borderItem: ItemStack?,
    private val onClose: ((Player) -> Unit)?,
) {

    fun open(player: Player) {
        val inventory = Inventory(type, miniMessage.deserialize(title))
        val size = type.size

        borderItem?.let { border ->
            val cols = 9
            val rows = size / cols
            for (i in 0 until size) {
                val row = i / cols
                val col = i % cols
                if (row == 0 || row == rows - 1 || col == 0 || col == cols - 1) {
                    if (i !in slots) inventory.setItemStack(i, border)
                }
            }
        }

        fillItem?.let { fill ->
            for (i in 0 until size) {
                if (i !in slots && inventory.getItemStack(i).isAir) {
                    inventory.setItemStack(i, fill)
                }
            }
        }

        slots.forEach { (i, slot) -> inventory.setItemStack(i, slot.item) }

        val guiNode = EventNode.all("gui-${System.identityHashCode(inventory)}")
        guiNode.addListener(InventoryPreClickEvent::class.java) { event ->
            val clicked = event.inventory ?: return@addListener
            if (clicked !== inventory) return@addListener
            event.isCancelled = true
            slots[event.slot]?.onClick?.invoke(event.player)
        }
        guiNode.addListener(InventoryCloseEvent::class.java) { event ->
            if (event.inventory !== inventory) return@addListener
            onClose?.invoke(event.player)
            MinecraftServer.getGlobalEventHandler().removeChild(guiNode)
        }
        MinecraftServer.getGlobalEventHandler().addChild(guiNode)

        player.openInventory(inventory)
    }
}

class GuiBuilder @PublishedApi internal constructor(private val title: String, private val rows: Int) {

    @PublishedApi internal val slots = mutableMapOf<Int, GuiSlot>()
    @PublishedApi internal var fillItem: ItemStack? = null
    @PublishedApi internal var borderItem: ItemStack? = null
    @PublishedApi internal var onCloseHandler: ((Player) -> Unit)? = null

    fun slot(index: Int, item: ItemStack, onClick: (Player) -> Unit = {}) {
        require(index in 0 until rows * 9) { "Slot $index out of bounds for $rows rows" }
        slots[index] = GuiSlot(item, onClick)
    }

    fun fill(material: Material) {
        fillItem = ItemStack.of(material)
    }

    fun fill(item: ItemStack) {
        fillItem = item
    }

    fun border(material: Material) {
        borderItem = ItemStack.of(material)
    }

    fun border(item: ItemStack) {
        borderItem = item
    }

    fun onClose(handler: (Player) -> Unit) {
        onCloseHandler = handler
    }

    @PublishedApi internal fun build(): Gui = Gui(title, rowsToType(rows), slots.toMap(), fillItem, borderItem, onCloseHandler)
}

inline fun gui(title: String, rows: Int = 3, block: GuiBuilder.() -> Unit): Gui =
    GuiBuilder(title, rows).apply(block).build()

fun Player.openGui(gui: Gui) = gui.open(this)

class PaginatedGui(
    private val title: String,
    private val rows: Int,
    private val items: List<GuiSlot>,
    private val staticSlots: Map<Int, GuiSlot>,
    private val fillItem: ItemStack?,
    private val borderItem: ItemStack?,
    private val contentSlots: IntRange,
) {
    private val pageSize = contentSlots.count()
    val totalPages: Int get() = ((items.size + pageSize - 1) / pageSize).coerceAtLeast(1)

    fun open(player: Player, page: Int = 0) {
        val safePage = page.coerceIn(0, totalPages - 1)
        val builder = GuiBuilder(title, rows)

        fillItem?.let { builder.fill(it) }
        borderItem?.let { builder.border(it) }

        val offset = safePage * pageSize
        contentSlots.forEachIndexed { i, slot ->
            val itemIndex = offset + i
            if (itemIndex < items.size) {
                builder.slots[slot] = items[itemIndex]
            }
        }

        staticSlots.forEach { (slot, guiSlot) -> builder.slots[slot] = guiSlot }

        if (safePage > 0) {
            builder.slots[rows * 9 - 9] = GuiSlot(ItemStack.of(Material.ARROW)) { p -> open(p, safePage - 1) }
        }
        if (safePage < totalPages - 1) {
            builder.slots[rows * 9 - 1] = GuiSlot(ItemStack.of(Material.ARROW)) { p -> open(p, safePage + 1) }
        }

        builder.build().open(player)
    }
}

class PaginatedGuiBuilder @PublishedApi internal constructor(private val title: String, private val rows: Int) {

    @PublishedApi internal val items = mutableListOf<GuiSlot>()
    @PublishedApi internal val staticSlots = mutableMapOf<Int, GuiSlot>()
    @PublishedApi internal var fillItem: ItemStack? = null
    @PublishedApi internal var borderItem: ItemStack? = null
    @PublishedApi internal var contentRange: IntRange = 10..rows * 9 - 11

    fun item(item: ItemStack, onClick: (Player) -> Unit = {}) {
        items += GuiSlot(item, onClick)
    }

    fun <T> items(collection: Collection<T>, transform: (T) -> ItemStack, onClick: (Player, T) -> Unit = { _, _ -> }) {
        collection.forEach { element ->
            items += GuiSlot(transform(element)) { p -> onClick(p, element) }
        }
    }

    fun staticSlot(index: Int, item: ItemStack, onClick: (Player) -> Unit = {}) {
        staticSlots[index] = GuiSlot(item, onClick)
    }

    fun fill(material: Material) { fillItem = ItemStack.of(material) }
    fun border(material: Material) { borderItem = ItemStack.of(material) }
    fun contentSlots(range: IntRange) { contentRange = range }

    @PublishedApi internal fun build(): PaginatedGui =
        PaginatedGui(title, rows, items.toList(), staticSlots.toMap(), fillItem, borderItem, contentRange)
}

inline fun paginatedGui(title: String, rows: Int = 6, block: PaginatedGuiBuilder.() -> Unit): PaginatedGui =
    PaginatedGuiBuilder(title, rows).apply(block).build()
