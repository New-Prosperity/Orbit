package me.nebula.orbit.utils.gui

import net.minestom.server.inventory.click.Click

enum class GuiClickType {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    MIDDLE,
    DROP,
    DROP_ALL,
    DROP_CURSOR,
    DOUBLE_CLICK,
    LEFT_DRAGGING,
    RIGHT_DRAGGING,
    MIDDLE_DRAGGING,
    HOTBAR_SWAP,
    OFFHAND_SWAP,
    UNKNOWN;

    val isDrag: Boolean
        get() = this == LEFT_DRAGGING || this == RIGHT_DRAGGING || this == MIDDLE_DRAGGING

    val isHotbarShortcut: Boolean
        get() = this == HOTBAR_SWAP || this == OFFHAND_SWAP

    val isDrop: Boolean
        get() = this == DROP || this == DROP_ALL || this == DROP_CURSOR

    val isShift: Boolean
        get() = this == SHIFT_LEFT || this == SHIFT_RIGHT

    companion object {
        fun fromMinestom(click: Click): GuiClickType = when (click) {
            is Click.Left -> LEFT
            is Click.Right -> RIGHT
            is Click.LeftShift -> SHIFT_LEFT
            is Click.RightShift -> SHIFT_RIGHT
            is Click.Middle -> MIDDLE
            is Click.Double -> DOUBLE_CLICK
            is Click.DropSlot -> if (click.all()) DROP_ALL else DROP
            is Click.LeftDropCursor, is Click.RightDropCursor, is Click.MiddleDropCursor -> DROP_CURSOR
            is Click.LeftDrag -> LEFT_DRAGGING
            is Click.RightDrag -> RIGHT_DRAGGING
            is Click.MiddleDrag -> MIDDLE_DRAGGING
            is Click.HotbarSwap -> HOTBAR_SWAP
            is Click.OffhandSwap -> OFFHAND_SWAP
        }
    }
}
