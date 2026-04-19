package me.nebula.orbit.utils.gui

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.sound.playSound
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

val CUSTOM_GUI_TAG: Tag<Boolean> = Tag.Boolean("nebula:custom_gui").defaultValue(false)

internal val DEFAULT_FILLER: ItemStack = itemStack(Material.GRAY_STAINED_GLASS_PANE) { name(" "); hideTooltip() }
internal val DEFAULT_BORDER: ItemStack = itemStack(Material.BLACK_STAINED_GLASS_PANE) { name(" "); hideTooltip() }

internal fun rowsToType(rows: Int): InventoryType = when (rows) {
    1 -> InventoryType.CHEST_1_ROW
    2 -> InventoryType.CHEST_2_ROW
    3 -> InventoryType.CHEST_3_ROW
    4 -> InventoryType.CHEST_4_ROW
    5 -> InventoryType.CHEST_5_ROW
    6 -> InventoryType.CHEST_6_ROW
    else -> error("Invalid row count: $rows (must be 1-6)")
}

private val guiLogger = logger("Gui")

class Gui internal constructor(
    val title: String,
    val type: InventoryType,
    initialSlots: Map<Int, GuiSlot>,
    private val fillItem: ItemStack?,
    private val borderItem: ItemStack?,
    private val onClose: ((Player) -> Unit)?,
    private val clickSound: SoundEvent?,
    private val preventClose: Boolean,
    private val allowedInteractionSlots: Set<Int>,
) {

    val size: Int = type.size
    private val slots = ConcurrentHashMap<Int, GuiSlot>(initialSlots)
    private val inventoryRef = AtomicReference<Inventory>()
    private val eventNodeRef = AtomicReference<EventNode<Event>>()
    private val closedFlag = AtomicBoolean(false)

    @Volatile
    private var viewer: Player? = null

    val inventory: Inventory? get() = inventoryRef.get()
    val isOpen: Boolean get() = viewer != null && !closedFlag.get()

    fun open(player: Player) {
        val inv = Inventory(type, miniMessage.deserialize(title))
        inv.setTag(CUSTOM_GUI_TAG, true)
        inventoryRef.set(inv)
        viewer = player
        closedFlag.set(false)
        renderAll(inv)
        attachNode(inv)
        GuiRegistry.track(player, this)
        player.openInventory(inv)
    }

    fun updateSlot(slot: Int, guiSlot: GuiSlot) {
        require(slot in 0 until size) { "Slot $slot out of bounds for size $size" }
        slots[slot] = guiSlot
        inventoryRef.get()?.setItemStack(slot, guiSlot.item)
    }

    fun updateItem(slot: Int, item: ItemStack) {
        require(slot in 0 until size) { "Slot $slot out of bounds for size $size" }
        val existing = slots[slot]
        val next = existing?.copy(item = item) ?: GuiSlot(item)
        slots[slot] = next
        inventoryRef.get()?.setItemStack(slot, item)
    }

    fun updateItem(slot: Int, item: ItemStack, onClick: (ClickContext) -> Unit) {
        require(slot in 0 until size) { "Slot $slot out of bounds for size $size" }
        slots[slot] = GuiSlot(item, onClick)
        inventoryRef.get()?.setItemStack(slot, item)
    }

    fun removeSlot(slot: Int) {
        require(slot in 0 until size) { "Slot $slot out of bounds for size $size" }
        slots.remove(slot)
        inventoryRef.get()?.setItemStack(slot, ItemStack.AIR)
    }

    fun refresh() {
        val inv = inventoryRef.get() ?: return
        for (i in 0 until size) inv.setItemStack(i, ItemStack.AIR)
        renderAll(inv)
    }

    fun getSlot(slot: Int): GuiSlot? = slots[slot]

    fun slotCount(): Int = slots.size

    internal fun forceClose(player: Player, notify: Boolean) {
        if (!closedFlag.compareAndSet(false, true)) return
        eventNodeRef.get()?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        if (notify) onClose?.invoke(player)
        GuiRegistry.untrack(player, this)
        viewer = null
    }

    private fun renderAll(inv: Inventory) {
        val cols = 9
        val rows = size / cols
        if (borderItem != null) {
            for (i in 0 until size) {
                val row = i / cols
                val col = i % cols
                if (row == 0 || row == rows - 1 || col == 0 || col == cols - 1) {
                    if (!slots.containsKey(i)) inv.setItemStack(i, borderItem)
                }
            }
        }
        if (fillItem != null) {
            for (i in 0 until size) {
                if (!slots.containsKey(i) && inv.getItemStack(i).isAir) {
                    inv.setItemStack(i, fillItem)
                }
            }
        }
        for ((i, slot) in slots) inv.setItemStack(i, slot.item)
    }

    private fun attachNode(inv: Inventory) {
        val node = EventNode.all("gui-${System.identityHashCode(inv)}")
        eventNodeRef.set(node)

        node.addListener(InventoryPreClickEvent::class.java) { event ->
            val clicked = event.inventory ?: return@addListener
            if (clicked !== inv) return@addListener
            val click = event.click
            val clickType = GuiClickType.fromMinestom(click)

            if (clickType.isDrag || clickType.isHotbarShortcut || clickType == GuiClickType.UNKNOWN) {
                event.isCancelled = true
                return@addListener
            }

            val eventSlot = event.slot
            val guiSlot = slots[eventSlot]

            if (guiSlot == null) {
                if (eventSlot in allowedInteractionSlots) return@addListener
                event.isCancelled = true
                return@addListener
            }

            event.isCancelled = true

            val sound = guiSlot.sound ?: clickSound
            sound?.let { event.player.playSound(it, 1f, 1f) }
            val ctx = ClickContext(event.player, eventSlot, clickType, this)
            runCatching { guiSlot.dispatch(ctx) }
                .onFailure { guiLogger.warn(it) { "GUI click handler threw for slot $eventSlot" } }
        }
        node.addListener(InventoryCloseEvent::class.java) { event ->
            if (event.inventory !== inv) return@addListener
            if (preventClose && !closedFlag.get()) {
                delay(1) {
                    if (!closedFlag.get()) event.player.openInventory(inv)
                }
                return@addListener
            }
            forceClose(event.player, notify = true)
        }
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            if (event.player.openInventory !== inv) return@addListener
            forceClose(event.player, notify = false)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
    }
}

fun Player.openGui(gui: Gui) = gui.open(this)
