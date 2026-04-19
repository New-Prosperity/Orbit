package me.nebula.orbit.utils.gui

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

private val builderLogger = logger("GuiBuilder")

class GuiBuilder @PublishedApi internal constructor(
    @PublishedApi internal val title: String,
    @PublishedApi internal val rows: Int,
) {
    init {
        require(rows in 1..6) { "GuiBuilder rows must be 1..6, got $rows" }
    }

    @PublishedApi internal val slots = mutableMapOf<Int, GuiSlot>()
    @PublishedApi internal var fillItem: ItemStack? = null
    @PublishedApi internal var borderItem: ItemStack? = null
    @PublishedApi internal var onCloseHandler: ((Player) -> Unit)? = null
    @PublishedApi internal var clickSound: SoundEvent? = null
    @PublishedApi internal var preventClose: Boolean = false
    @PublishedApi internal val interactionSlots = mutableSetOf<Int>()

    @PublishedApi internal val size: Int get() = rows * 9

    internal fun putSlot(index: Int, slot: GuiSlot) {
        require(index in 0 until size) { "Slot $index out of bounds for $rows rows (size=$size)" }
        val previous = slots.put(index, slot)
        if (previous != null) {
            builderLogger.warn { "Slot $index collision in GUI '$title' — previous slot overwritten" }
        }
    }

    fun slot(index: Int, item: ItemStack) {
        putSlot(index, GuiSlot(item))
    }

    fun slot(index: Int, item: ItemStack, onClick: (Player) -> Unit) {
        putSlot(index, GuiSlot(item, { ctx -> onClick(ctx.player) }))
    }

    fun slot(index: Int, item: ItemStack, sound: SoundEvent, onClick: (Player) -> Unit) {
        putSlot(index, GuiSlot(item, { ctx -> onClick(ctx.player) }, sound))
    }

    fun slot(index: Int, slot: GuiSlot) {
        putSlot(index, slot)
    }

    fun contextSlot(index: Int, item: ItemStack, onClick: (ClickContext) -> Unit) {
        putSlot(index, GuiSlot(item, onClick))
    }

    fun contextSlot(index: Int, item: ItemStack, sound: SoundEvent, onClick: (ClickContext) -> Unit) {
        putSlot(index, GuiSlot(item, onClick, sound))
    }

    fun clickable(
        index: Int,
        item: ItemStack,
        onLeft: (ClickContext) -> Unit,
        onRight: ((ClickContext) -> Unit)? = null,
        onShift: ((ClickContext) -> Unit)? = null,
        onMiddle: ((ClickContext) -> Unit)? = null,
    ) {
        putSlot(index, GuiSlot(
            item = item,
            onClick = { ctx -> if (ctx.clickType == GuiClickType.LEFT) onLeft(ctx) },
            onLeft = onLeft,
            onRight = onRight,
            onShift = onShift,
            onMiddle = onMiddle,
        ))
    }

    fun toggle(
        index: Int,
        currentValue: Boolean,
        onItem: ItemStack,
        offItem: ItemStack,
        onToggle: (Boolean) -> Unit,
    ) {
        val item = if (currentValue) onItem else offItem
        putSlot(index, GuiSlot(item, { onToggle(!currentValue) }))
    }

    fun allowSlotInteraction(index: Int) {
        require(index in 0 until size) { "Interaction slot $index out of bounds for $rows rows" }
        require(index !in slots) { "Slot $index cannot be both a managed slot and allow-interaction" }
        interactionSlots += index
    }

    fun fill(material: Material) {
        fillItem = itemStack(material) { name(" "); hideTooltip() }
    }

    fun fill(item: ItemStack) {
        fillItem = item
    }

    fun fillDefault() {
        fillItem = DEFAULT_FILLER
    }

    fun border(material: Material) {
        borderItem = itemStack(material) { name(" "); hideTooltip() }
    }

    fun border(item: ItemStack) {
        borderItem = item
    }

    fun borderDefault() {
        borderItem = DEFAULT_BORDER
    }

    fun onClose(handler: (Player) -> Unit) {
        onCloseHandler = handler
    }

    fun clickSound(sound: SoundEvent) {
        clickSound = sound
    }

    fun preventClose() {
        preventClose = true
    }

    fun backButton(index: Int, onClick: (Player) -> Unit) {
        putSlot(index, GuiSlot(
            itemStack(Material.ARROW) { name("<gray>Back"); clean() },
            { ctx -> onClick(ctx.player) },
            SoundEvent.UI_BUTTON_CLICK,
        ))
    }

    fun closeButton(index: Int) {
        putSlot(index, GuiSlot(
            itemStack(Material.BARRIER) { name("<red>Close"); clean() },
            { ctx -> ctx.player.closeInventory() },
            SoundEvent.UI_BUTTON_CLICK,
        ))
    }

    @PublishedApi internal fun build(): Gui = Gui(
        title = title,
        type = rowsToType(rows),
        initialSlots = slots.toMap(),
        fillItem = fillItem,
        borderItem = borderItem,
        onClose = onCloseHandler,
        clickSound = clickSound,
        preventClose = preventClose,
        allowedInteractionSlots = interactionSlots.toSet(),
    )
}

inline fun gui(title: String, rows: Int = 3, block: GuiBuilder.() -> Unit): Gui =
    GuiBuilder(title, rows).apply(block).build()
