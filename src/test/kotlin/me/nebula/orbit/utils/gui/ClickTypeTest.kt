package me.nebula.orbit.utils.gui

import net.minestom.server.inventory.click.Click
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClickTypeTest {

    @Test
    fun `left click maps`() {
        assertEquals(GuiClickType.LEFT, GuiClickType.fromMinestom(Click.Left(5)))
    }

    @Test
    fun `right click maps`() {
        assertEquals(GuiClickType.RIGHT, GuiClickType.fromMinestom(Click.Right(5)))
    }

    @Test
    fun `shift left click maps`() {
        assertEquals(GuiClickType.SHIFT_LEFT, GuiClickType.fromMinestom(Click.LeftShift(5)))
    }

    @Test
    fun `shift right click maps`() {
        assertEquals(GuiClickType.SHIFT_RIGHT, GuiClickType.fromMinestom(Click.RightShift(5)))
    }

    @Test
    fun `middle click maps`() {
        assertEquals(GuiClickType.MIDDLE, GuiClickType.fromMinestom(Click.Middle(5)))
    }

    @Test
    fun `double click maps`() {
        assertEquals(GuiClickType.DOUBLE_CLICK, GuiClickType.fromMinestom(Click.Double(5)))
    }

    @Test
    fun `drop slot single maps to DROP`() {
        assertEquals(GuiClickType.DROP, GuiClickType.fromMinestom(Click.DropSlot(5, false)))
    }

    @Test
    fun `drop slot all maps to DROP_ALL`() {
        assertEquals(GuiClickType.DROP_ALL, GuiClickType.fromMinestom(Click.DropSlot(5, true)))
    }

    @Test
    fun `left drop cursor maps to DROP_CURSOR`() {
        assertEquals(GuiClickType.DROP_CURSOR, GuiClickType.fromMinestom(Click.LeftDropCursor()))
    }

    @Test
    fun `right drop cursor maps to DROP_CURSOR`() {
        assertEquals(GuiClickType.DROP_CURSOR, GuiClickType.fromMinestom(Click.RightDropCursor()))
    }

    @Test
    fun `middle drop cursor maps to DROP_CURSOR`() {
        assertEquals(GuiClickType.DROP_CURSOR, GuiClickType.fromMinestom(Click.MiddleDropCursor()))
    }

    @Test
    fun `hotbar swap maps`() {
        assertEquals(GuiClickType.HOTBAR_SWAP, GuiClickType.fromMinestom(Click.HotbarSwap(3, 5)))
    }

    @Test
    fun `offhand swap maps`() {
        assertEquals(GuiClickType.OFFHAND_SWAP, GuiClickType.fromMinestom(Click.OffhandSwap(5)))
    }

    @Test
    fun `left drag maps`() {
        assertEquals(GuiClickType.LEFT_DRAGGING, GuiClickType.fromMinestom(Click.LeftDrag(listOf(1, 2))))
    }

    @Test
    fun `right drag maps`() {
        assertEquals(GuiClickType.RIGHT_DRAGGING, GuiClickType.fromMinestom(Click.RightDrag(listOf(1, 2))))
    }

    @Test
    fun `middle drag maps`() {
        assertEquals(GuiClickType.MIDDLE_DRAGGING, GuiClickType.fromMinestom(Click.MiddleDrag(listOf(1, 2))))
    }

    @Test
    fun `isDrag true for drag types`() {
        assertTrue(GuiClickType.LEFT_DRAGGING.isDrag)
        assertTrue(GuiClickType.RIGHT_DRAGGING.isDrag)
        assertTrue(GuiClickType.MIDDLE_DRAGGING.isDrag)
    }

    @Test
    fun `isDrag false for non drag`() {
        assertFalse(GuiClickType.LEFT.isDrag)
        assertFalse(GuiClickType.HOTBAR_SWAP.isDrag)
        assertFalse(GuiClickType.DROP.isDrag)
    }

    @Test
    fun `isHotbarShortcut detects swaps`() {
        assertTrue(GuiClickType.HOTBAR_SWAP.isHotbarShortcut)
        assertTrue(GuiClickType.OFFHAND_SWAP.isHotbarShortcut)
        assertFalse(GuiClickType.LEFT.isHotbarShortcut)
    }

    @Test
    fun `isDrop detects drop events`() {
        assertTrue(GuiClickType.DROP.isDrop)
        assertTrue(GuiClickType.DROP_ALL.isDrop)
        assertTrue(GuiClickType.DROP_CURSOR.isDrop)
        assertFalse(GuiClickType.LEFT.isDrop)
    }

    @Test
    fun `isShift detects shift variants`() {
        assertTrue(GuiClickType.SHIFT_LEFT.isShift)
        assertTrue(GuiClickType.SHIFT_RIGHT.isShift)
        assertFalse(GuiClickType.LEFT.isShift)
    }
}
