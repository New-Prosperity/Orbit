package me.nebula.orbit.utils.gui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PaginationTest {

    @Test
    fun `empty list has one page`() {
        assertEquals(1, PaginatedGui.pageCountOf(0, 28))
    }

    @Test
    fun `single item has one page`() {
        assertEquals(1, PaginatedGui.pageCountOf(1, 28))
    }

    @Test
    fun `exact page count`() {
        assertEquals(2, PaginatedGui.pageCountOf(56, 28))
        assertEquals(1, PaginatedGui.pageCountOf(28, 28))
    }

    @Test
    fun `overflow rounds up`() {
        assertEquals(2, PaginatedGui.pageCountOf(29, 28))
        assertEquals(3, PaginatedGui.pageCountOf(57, 28))
    }

    @Test
    fun `zero page size coerces to 1`() {
        assertEquals(5, PaginatedGui.pageCountOf(5, 0))
    }

    @Test
    fun `negative item count returns 1`() {
        assertEquals(1, PaginatedGui.pageCountOf(-1, 28))
    }

    @Test
    fun `large numbers`() {
        assertEquals(4, PaginatedGui.pageCountOf(100, 28))
        assertEquals(36, PaginatedGui.pageCountOf(1000, 28))
    }
}
