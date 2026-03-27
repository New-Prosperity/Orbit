package me.nebula.orbit.utils.gui

import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.sound.playSound
import net.kyori.adventure.text.Component
import me.nebula.orbit.utils.scheduler.delay
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import java.util.concurrent.atomic.AtomicBoolean

val CUSTOM_GUI_TAG: Tag<Boolean> = Tag.Boolean("nebula:custom_gui").defaultValue(false)

private fun rowsToType(rows: Int): InventoryType = when (rows) {
    1 -> InventoryType.CHEST_1_ROW
    2 -> InventoryType.CHEST_2_ROW
    3 -> InventoryType.CHEST_3_ROW
    4 -> InventoryType.CHEST_4_ROW
    5 -> InventoryType.CHEST_5_ROW
    6 -> InventoryType.CHEST_6_ROW
    else -> error("Invalid row count: $rows (must be 1-6)")
}

private val DEFAULT_FILLER = itemStack(Material.GRAY_STAINED_GLASS_PANE) { name(" "); hideTooltip() }
private val DEFAULT_BORDER = itemStack(Material.BLACK_STAINED_GLASS_PANE) { name(" "); hideTooltip() }

data class GuiSlot(
    val item: ItemStack,
    val onClick: (Player) -> Unit = {},
    val sound: SoundEvent? = null,
)

class Gui(
    private val title: String,
    private val type: InventoryType,
    private val slots: Map<Int, GuiSlot>,
    private val fillItem: ItemStack?,
    private val borderItem: ItemStack?,
    private val onClose: ((Player) -> Unit)?,
    private val clickSound: SoundEvent?,
    private val preventClose: Boolean,
) {

    fun open(player: Player) {
        val inventory = Inventory(type, miniMessage.deserialize(title))
        inventory.setTag(CUSTOM_GUI_TAG, true)
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
        val removed = AtomicBoolean(false)
        val cleanup = { p: Player ->
            if (removed.compareAndSet(false, true)) {
                onClose?.invoke(p)
                MinecraftServer.getGlobalEventHandler().removeChild(guiNode)
            }
        }
        guiNode.addListener(InventoryPreClickEvent::class.java) { event ->
            val clicked = event.inventory ?: return@addListener
            if (clicked !== inventory) return@addListener
            event.isCancelled = true
            val guiSlot = slots[event.slot] ?: return@addListener
            val sound = guiSlot.sound ?: clickSound
            sound?.let { event.player.playSound(it, 1f, 1f) }
            guiSlot.onClick(event.player)
        }
        guiNode.addListener(InventoryCloseEvent::class.java) { event ->
            if (event.inventory !== inventory) return@addListener
            if (preventClose) {
                delay(1) { event.player.openInventory(inventory) }
                return@addListener
            }
            cleanup(event.player)
        }
        guiNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            if (event.player.openInventory !== inventory) return@addListener
            cleanup(event.player)
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
    @PublishedApi internal var clickSound: SoundEvent? = null
    @PublishedApi internal var preventClose: Boolean = false

    fun slot(index: Int, item: ItemStack, onClick: (Player) -> Unit = {}) {
        require(index in 0 until rows * 9) { "Slot $index out of bounds for $rows rows" }
        slots[index] = GuiSlot(item, onClick)
    }

    fun slot(index: Int, item: ItemStack, sound: SoundEvent, onClick: (Player) -> Unit) {
        require(index in 0 until rows * 9) { "Slot $index out of bounds for $rows rows" }
        slots[index] = GuiSlot(item, onClick, sound)
    }

    fun fill(material: Material) { fillItem = itemStack(material) { name(" "); hideTooltip() } }
    fun fill(item: ItemStack) { fillItem = item }
    fun fillDefault() { fillItem = DEFAULT_FILLER }

    fun border(material: Material) { borderItem = itemStack(material) { name(" "); hideTooltip() } }
    fun border(item: ItemStack) { borderItem = item }
    fun borderDefault() { borderItem = DEFAULT_BORDER }

    fun onClose(handler: (Player) -> Unit) { onCloseHandler = handler }
    fun clickSound(sound: SoundEvent) { clickSound = sound }
    fun preventClose() { preventClose = true }

    fun backButton(slot: Int, onClick: (Player) -> Unit) {
        slots[slot] = GuiSlot(
            itemStack(Material.ARROW) { name("<gray>Back"); clean() },
            onClick,
            SoundEvent.UI_BUTTON_CLICK,
        )
    }

    fun closeButton(slot: Int) {
        slots[slot] = GuiSlot(
            itemStack(Material.BARRIER) { name("<red>Close"); clean() },
            { it.closeInventory() },
            SoundEvent.UI_BUTTON_CLICK,
        )
    }

    @PublishedApi internal fun build(): Gui = Gui(
        title, rowsToType(rows), slots.toMap(), fillItem, borderItem,
        onCloseHandler, clickSound, preventClose,
    )
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
    private val clickSound: SoundEvent?,
) {
    private val pageSize = contentSlots.count()
    val totalPages: Int get() = ((items.size + pageSize - 1) / pageSize).coerceAtLeast(1)

    fun open(player: Player, page: Int = 0) {
        val safePage = page.coerceIn(0, totalPages - 1)
        val builder = GuiBuilder("$title <dark_gray>(${safePage + 1}/$totalPages)", rows)

        fillItem?.let { builder.fill(it) }
        borderItem?.let { builder.border(it) }
        clickSound?.let { builder.clickSound(it) }

        val offset = safePage * pageSize
        contentSlots.forEachIndexed { i, slot ->
            val itemIndex = offset + i
            if (itemIndex < items.size) {
                builder.slots[slot] = items[itemIndex]
            }
        }

        staticSlots.forEach { (slot, guiSlot) -> builder.slots[slot] = guiSlot }

        if (safePage > 0) {
            builder.slots[rows * 9 - 9] = GuiSlot(
                itemStack(Material.ARROW) { name("<yellow>Previous Page"); clean() },
                { p -> open(p, safePage - 1) },
                SoundEvent.UI_BUTTON_CLICK,
            )
        }
        if (safePage < totalPages - 1) {
            builder.slots[rows * 9 - 1] = GuiSlot(
                itemStack(Material.ARROW) { name("<yellow>Next Page"); clean() },
                { p -> open(p, safePage + 1) },
                SoundEvent.UI_BUTTON_CLICK,
            )
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
    @PublishedApi internal var clickSound: SoundEvent? = null

    fun item(item: ItemStack, onClick: (Player) -> Unit = {}) {
        items += GuiSlot(item, onClick)
    }

    fun <T> items(collection: Collection<T>, transform: (T) -> ItemStack, onClick: (Player, T) -> Unit = { _, _ -> }) {
        collection.forEach { element ->
            items += GuiSlot(transform(element), onClick = { p -> onClick(p, element) })
        }
    }

    fun staticSlot(index: Int, item: ItemStack, onClick: (Player) -> Unit = {}) {
        staticSlots[index] = GuiSlot(item, onClick)
    }

    fun fill(material: Material) { fillItem = itemStack(material) { name(" "); hideTooltip() } }
    fun border(material: Material) { borderItem = itemStack(material) { name(" "); hideTooltip() } }
    fun contentSlots(range: IntRange) { contentRange = range }
    fun clickSound(sound: SoundEvent) { clickSound = sound }

    @PublishedApi internal fun build(): PaginatedGui =
        PaginatedGui(title, rows, items.toList(), staticSlots.toMap(), fillItem, borderItem, contentRange, clickSound)
}

inline fun paginatedGui(title: String, rows: Int = 6, block: PaginatedGuiBuilder.() -> Unit): PaginatedGui =
    PaginatedGuiBuilder(title, rows).apply(block).build()

fun confirmGui(
    title: String,
    message: String,
    onConfirm: (Player) -> Unit,
    onCancel: (Player) -> Unit = { it.closeInventory() },
): Gui = gui(title, 3) {
    fillDefault()
    clickSound(SoundEvent.UI_BUTTON_CLICK)
    slot(11, itemStack(Material.LIME_STAINED_GLASS_PANE) { name("<green><bold>Confirm"); lore(message); clean() }) { onConfirm(it) }
    slot(15, itemStack(Material.RED_STAINED_GLASS_PANE) { name("<red><bold>Cancel"); lore(message); clean() }) { onCancel(it) }
}
