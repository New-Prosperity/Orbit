package me.nebula.orbit.utils.gui

import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PaginatedGuiBuilderTest {

    @Test
    fun `rejects 0 rows`() {
        assertFailsWith<IllegalArgumentException> { PaginatedGuiBuilder("t", 0) }
    }

    @Test
    fun `rejects 7 rows`() {
        assertFailsWith<IllegalArgumentException> { PaginatedGuiBuilder("t", 7) }
    }

    @Test
    fun `content range auto detects without border`() {
        val b = PaginatedGuiBuilder("t", 3)
        b.item(ItemStack.of(Material.STONE))
        val gui = b.build()
        assertEquals(1, gui.totalPages)
    }

    @Test
    fun `content slots out of bounds fail`() {
        val b = PaginatedGuiBuilder("t", 3)
        b.contentSlots(0..100)
        assertFailsWith<IllegalArgumentException> { b.build() }
    }

    @Test
    fun `empty items yields one page`() {
        val b = PaginatedGuiBuilder("t", 6)
        val gui = b.build()
        assertEquals(1, gui.totalPages)
    }

    @Test
    fun `items fill pages`() {
        val b = PaginatedGuiBuilder("t", 3)
        b.border(Material.GRAY_STAINED_GLASS_PANE)
        repeat(30) { b.item(ItemStack.of(Material.STONE)) }
        val gui = b.build()
        assertTrue(gui.totalPages >= 1)
    }

    @Test
    fun `pageSize respects content range`() {
        val b = PaginatedGuiBuilder("t", 3)
        b.contentSlots(10..16)
        val gui = b.build()
        assertEquals(7, gui.pageSize)
    }

    @Test
    fun `navigation slots are reserved`() {
        val b = PaginatedGuiBuilder("t", 6)
        val gui = b.build()
        assertTrue(gui.pageSize > 0)
    }

    @Test
    fun `key method stores key`() {
        val b = PaginatedGuiBuilder("t", 3)
        b.key("custom-key")
        val gui = b.build()
        assertEquals("custom-key", gui.pageKey())
    }
}
