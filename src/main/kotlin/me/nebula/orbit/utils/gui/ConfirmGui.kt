package me.nebula.orbit.utils.gui

import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.scheduler.delay
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

enum class ConfirmOutcome { CONFIRM, CANCEL, TIMEOUT }

class ConfirmGuiBuilder @PublishedApi internal constructor(
    @PublishedApi internal val title: String,
) {
    @PublishedApi internal var confirmSlot: Int = 11
    @PublishedApi internal var cancelSlot: Int = 15
    @PublishedApi internal var previewSlot: Int = 13
    @PublishedApi internal var confirmItem: ItemStack = itemStack(Material.LIME_STAINED_GLASS_PANE) { name("<green><bold>Confirm"); clean() }
    @PublishedApi internal var cancelItem: ItemStack = itemStack(Material.RED_STAINED_GLASS_PANE) { name("<red><bold>Cancel"); clean() }
    @PublishedApi internal var previewItem: ItemStack? = null
    @PublishedApi internal var timeout: Duration? = null
    @PublishedApi internal var outcomeHandler: ((Player, ConfirmOutcome) -> Unit)? = null
    @PublishedApi internal var rows: Int = 3

    fun rows(rows: Int) { this.rows = rows }
    fun confirm(slot: Int, item: ItemStack? = null) {
        confirmSlot = slot
        if (item != null) confirmItem = item
    }
    fun cancel(slot: Int, item: ItemStack? = null) {
        cancelSlot = slot
        if (item != null) cancelItem = item
    }
    fun preview(slot: Int, item: ItemStack?) {
        previewSlot = slot
        previewItem = item
    }
    fun timeout(duration: Duration) { timeout = duration }
    fun onOutcome(handler: (Player, ConfirmOutcome) -> Unit) { outcomeHandler = handler }
}

fun confirmGui(
    title: String,
    message: String,
    onConfirm: (Player) -> Unit,
    onCancel: (Player) -> Unit = { it.closeInventory() },
): Gui = gui(title, 3) {
    fillDefault()
    clickSound(SoundEvent.UI_BUTTON_CLICK)
    slot(11, itemStack(Material.LIME_STAINED_GLASS_PANE) { name("<green><bold>Confirm"); lore(message); clean() }) { p -> onConfirm(p) }
    slot(15, itemStack(Material.RED_STAINED_GLASS_PANE) { name("<red><bold>Cancel"); lore(message); clean() }) { p -> onCancel(p) }
}

fun confirmGui(
    title: String,
    confirmItem: ItemStack,
    cancelItem: ItemStack,
    previewItem: ItemStack? = null,
    onConfirm: (Player) -> Unit,
    onCancel: (Player) -> Unit = { it.closeInventory() },
): Gui = gui(title, 3) {
    fillDefault()
    clickSound(SoundEvent.UI_BUTTON_CLICK)
    slot(11, confirmItem) { p -> onConfirm(p) }
    previewItem?.let { slot(13, it) }
    slot(15, cancelItem) { p -> onCancel(p) }
}

inline fun advancedConfirmGui(
    title: String,
    block: ConfirmGuiBuilder.() -> Unit,
): Gui {
    val builder = ConfirmGuiBuilder(title).apply(block)
    val handler = builder.outcomeHandler
    val decided = AtomicBoolean(false)
    val resolveOnce: (Player, ConfirmOutcome) -> Unit = { player, outcome ->
        if (decided.compareAndSet(false, true)) handler?.invoke(player, outcome)
    }
    val gui = gui(title, builder.rows) {
        fillDefault()
        clickSound(SoundEvent.UI_BUTTON_CLICK)
        slot(builder.confirmSlot, builder.confirmItem) { p -> resolveOnce(p, ConfirmOutcome.CONFIRM); p.closeInventory() }
        builder.previewItem?.let { slot(builder.previewSlot, it) }
        slot(builder.cancelSlot, builder.cancelItem) { p -> resolveOnce(p, ConfirmOutcome.CANCEL); p.closeInventory() }
        onClose { p ->
            if (decided.compareAndSet(false, true)) handler?.invoke(p, ConfirmOutcome.CANCEL)
        }
    }
    val to = builder.timeout
    if (to != null) {
        delay(to) {
            if (!decided.get()) {
                decided.set(true)
                gui.inventory?.viewers?.toList()?.forEach { p ->
                    handler?.invoke(p, ConfirmOutcome.TIMEOUT)
                    p.closeInventory()
                }
            }
        }
    }
    return gui
}
