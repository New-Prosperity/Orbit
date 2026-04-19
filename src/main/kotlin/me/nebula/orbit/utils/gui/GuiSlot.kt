package me.nebula.orbit.utils.gui

import net.minestom.server.item.ItemStack
import net.minestom.server.sound.SoundEvent

data class GuiSlot(
    val item: ItemStack,
    val onClick: (ClickContext) -> Unit = {},
    val sound: SoundEvent? = null,
    val onLeft: ((ClickContext) -> Unit)? = null,
    val onRight: ((ClickContext) -> Unit)? = null,
    val onShift: ((ClickContext) -> Unit)? = null,
    val onDouble: ((ClickContext) -> Unit)? = null,
    val onDrop: ((ClickContext) -> Unit)? = null,
    val onMiddle: ((ClickContext) -> Unit)? = null,
) {
    fun dispatch(ctx: ClickContext) {
        val typed = typedHandlerFor(ctx.clickType)
        if (typed != null) typed(ctx) else onClick(ctx)
    }

    fun typedHandlerFor(clickType: GuiClickType): ((ClickContext) -> Unit)? = when (clickType) {
        GuiClickType.LEFT -> onLeft
        GuiClickType.RIGHT -> onRight
        GuiClickType.SHIFT_LEFT, GuiClickType.SHIFT_RIGHT -> onShift
        GuiClickType.DOUBLE_CLICK -> onDouble
        GuiClickType.DROP, GuiClickType.DROP_ALL -> onDrop
        GuiClickType.MIDDLE -> onMiddle
        else -> null
    }
}
