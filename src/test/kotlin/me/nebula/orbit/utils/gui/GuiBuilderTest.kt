package me.nebula.orbit.utils.gui

import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GuiBuilderTest {

    private val testItem: ItemStack = ItemStack.of(Material.STONE)

    @Test
    fun `builder rejects zero rows`() {
        assertFailsWith<IllegalArgumentException> { GuiBuilder("t", 0) }
    }

    @Test
    fun `builder rejects 7 rows`() {
        assertFailsWith<IllegalArgumentException> { GuiBuilder("t", 7) }
    }

    @Test
    fun `builder accepts rows 1 through 6`() {
        (1..6).forEach { GuiBuilder("t", it) }
    }

    @Test
    fun `slot with negative index fails`() {
        val b = GuiBuilder("t", 3)
        assertFailsWith<IllegalArgumentException> { b.slot(-1, testItem) }
    }

    @Test
    fun `slot beyond size fails`() {
        val b = GuiBuilder("t", 3)
        assertFailsWith<IllegalArgumentException> { b.slot(27, testItem) }
    }

    @Test
    fun `valid slot is stored`() {
        val b = GuiBuilder("t", 3)
        b.slot(10, testItem)
        assertNotNull(b.slots[10])
    }

    @Test
    fun `collision overwrites and does not fail`() {
        val b = GuiBuilder("t", 3)
        b.slot(10, testItem)
        b.slot(10, ItemStack.of(Material.DIRT))
        val stored = b.slots[10]
        assertNotNull(stored)
        assertEquals(Material.DIRT, stored.item.material())
    }

    @Test
    fun `allowSlotInteraction rejects out of bounds`() {
        val b = GuiBuilder("t", 3)
        assertFailsWith<IllegalArgumentException> { b.allowSlotInteraction(27) }
    }

    @Test
    fun `allowSlotInteraction rejects when slot is managed`() {
        val b = GuiBuilder("t", 3)
        b.slot(10, testItem)
        assertFailsWith<IllegalArgumentException> { b.allowSlotInteraction(10) }
    }

    @Test
    fun `allowSlotInteraction stores valid slot`() {
        val b = GuiBuilder("t", 3)
        b.allowSlotInteraction(15)
        assertTrue(15 in b.interactionSlots)
    }

    @Test
    fun `toggle uses onItem when true`() {
        val b = GuiBuilder("t", 3)
        val onItem = ItemStack.of(Material.LIME_DYE)
        val offItem = ItemStack.of(Material.GRAY_DYE)
        b.toggle(10, true, onItem, offItem) { }
        assertEquals(Material.LIME_DYE, b.slots[10]?.item?.material())
    }

    @Test
    fun `toggle uses offItem when false`() {
        val b = GuiBuilder("t", 3)
        val onItem = ItemStack.of(Material.LIME_DYE)
        val offItem = ItemStack.of(Material.GRAY_DYE)
        b.toggle(10, false, onItem, offItem) { }
        assertEquals(Material.GRAY_DYE, b.slots[10]?.item?.material())
    }

    @Test
    fun `clickable stores per-click handlers`() {
        val b = GuiBuilder("t", 3)
        b.clickable(10, testItem, onLeft = {}, onRight = {}, onShift = {}, onMiddle = {})
        val slot = b.slots[10]
        assertNotNull(slot)
        assertNotNull(slot.onLeft)
        assertNotNull(slot.onRight)
        assertNotNull(slot.onShift)
        assertNotNull(slot.onMiddle)
    }

    @Test
    fun `gui dsl builds a Gui`() {
        val gui = gui("title", 3) {
            slot(10, testItem) { }
            slot(11, testItem) { }
            fillDefault()
            borderDefault()
        }
        assertEquals(27, gui.size)
    }
}
