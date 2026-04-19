package me.nebula.orbit.utils.gui

import net.minestom.server.entity.Player

data class ClickContext(
    val player: Player,
    val slot: Int,
    val clickType: GuiClickType,
    val gui: Gui,
) {
    fun close() {
        player.closeInventory()
    }
}
