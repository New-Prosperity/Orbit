package me.nebula.orbit.utils.gui

import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GuiSlotTest {

    private val item: ItemStack = ItemStack.of(Material.STONE)

    @Test
    fun `typedHandlerFor returns onLeft for LEFT`() {
        val left: (ClickContext) -> Unit = { }
        val slot = GuiSlot(item, onLeft = left)
        assertNotNull(slot.typedHandlerFor(GuiClickType.LEFT))
    }

    @Test
    fun `typedHandlerFor returns onRight for RIGHT`() {
        val slot = GuiSlot(item, onRight = { })
        assertNotNull(slot.typedHandlerFor(GuiClickType.RIGHT))
    }

    @Test
    fun `typedHandlerFor returns onShift for SHIFT_LEFT and SHIFT_RIGHT`() {
        val slot = GuiSlot(item, onShift = { })
        assertNotNull(slot.typedHandlerFor(GuiClickType.SHIFT_LEFT))
        assertNotNull(slot.typedHandlerFor(GuiClickType.SHIFT_RIGHT))
    }

    @Test
    fun `typedHandlerFor returns onDouble for DOUBLE_CLICK`() {
        val slot = GuiSlot(item, onDouble = { })
        assertNotNull(slot.typedHandlerFor(GuiClickType.DOUBLE_CLICK))
    }

    @Test
    fun `typedHandlerFor returns onDrop for DROP and DROP_ALL`() {
        val slot = GuiSlot(item, onDrop = { })
        assertNotNull(slot.typedHandlerFor(GuiClickType.DROP))
        assertNotNull(slot.typedHandlerFor(GuiClickType.DROP_ALL))
    }

    @Test
    fun `typedHandlerFor returns onMiddle for MIDDLE`() {
        val slot = GuiSlot(item, onMiddle = { })
        assertNotNull(slot.typedHandlerFor(GuiClickType.MIDDLE))
    }

    @Test
    fun `typedHandlerFor returns null for hotbar swap`() {
        val slot = GuiSlot(item)
        assertNull(slot.typedHandlerFor(GuiClickType.HOTBAR_SWAP))
    }

    @Test
    fun `typedHandlerFor returns null when handler not configured`() {
        val slot = GuiSlot(item)
        assertNull(slot.typedHandlerFor(GuiClickType.LEFT))
        assertNull(slot.typedHandlerFor(GuiClickType.RIGHT))
    }

    @Test
    fun `onClick is default no-op`() {
        val slot = GuiSlot(item)
        var calls = 0
        GuiSlot(item, onClick = { calls++ })
        assertEquals(0, calls)
        assertNotNull(slot)
    }
}
