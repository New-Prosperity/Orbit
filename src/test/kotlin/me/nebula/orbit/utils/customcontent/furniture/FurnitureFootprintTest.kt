package me.nebula.orbit.utils.customcontent.furniture

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class FurnitureFootprintTest {

    @Test
    fun `single-cell footprint is the default`() {
        val f = FurnitureFootprint.SINGLE
        assertEquals(1, f.size)
        assertEquals(listOf(FootprintCell(0, 0, 0)), f.cells)
    }

    @Test
    fun `multi-cell footprint preserves order`() {
        val cells = listOf(FootprintCell(0, 0, 0), FootprintCell(1, 0, 0))
        val f = FurnitureFootprint(cells)
        assertEquals(2, f.size)
        assertEquals(cells, f.cells)
    }

    @Test
    fun `empty footprint is rejected`() {
        assertThrows<IllegalArgumentException> { FurnitureFootprint(emptyList()) }
    }

    @Test
    fun `duplicate cells are rejected`() {
        val cells = listOf(FootprintCell(0, 0, 0), FootprintCell(0, 0, 0))
        assertThrows<IllegalArgumentException> { FurnitureFootprint(cells) }
    }

    @Test
    fun `footprint without anchor cell is rejected`() {
        val cells = listOf(FootprintCell(1, 0, 0), FootprintCell(2, 0, 0))
        assertThrows<IllegalArgumentException> { FurnitureFootprint(cells) }
    }

    @Test
    fun `definition rejects blank id`() {
        assertThrows<IllegalArgumentException> { FurnitureDefinition(id = "", itemId = "x") }
    }

    @Test
    fun `definition rejects blank itemId`() {
        assertThrows<IllegalArgumentException> { FurnitureDefinition(id = "x", itemId = "") }
    }

    @Test
    fun `definition rejects non-positive scale`() {
        assertThrows<IllegalArgumentException> { FurnitureDefinition(id = "x", itemId = "y", scale = 0.0) }
        assertThrows<IllegalArgumentException> { FurnitureDefinition(id = "x", itemId = "y", scale = -1.0) }
    }
}
