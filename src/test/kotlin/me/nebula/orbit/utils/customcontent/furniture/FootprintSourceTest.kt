package me.nebula.orbit.utils.customcontent.furniture

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FootprintSourceTest {

    @Test
    fun `box 2x1x1 expands to two cells`() {
        val cells = FootprintSource.Box(2, 1, 1).resolveCells()
        assertEquals(setOf(FootprintCell(0, 0, 0), FootprintCell(1, 0, 0)), cells.toSet())
    }

    @Test
    fun `box 2x2x2 expands to eight cells`() {
        val cells = FootprintSource.Box(2, 2, 2).resolveCells()
        assertEquals(8, cells.size)
        assertTrue(cells.contains(FootprintCell(0, 0, 0)))
        assertTrue(cells.contains(FootprintCell(1, 1, 1)))
    }

    @Test
    fun `box defaults height and depth to 1`() {
        val cells = FootprintSource.Box(3).resolveCells()
        assertEquals(listOf(
            FootprintCell(0, 0, 0), FootprintCell(1, 0, 0), FootprintCell(2, 0, 0),
        ), cells)
    }

    @Test
    fun `box rejects zero or negative dimensions`() {
        assertThrows<IllegalArgumentException> { FootprintSource.Box(0) }
        assertThrows<IllegalArgumentException> { FootprintSource.Box(1, 0, 1) }
        assertThrows<IllegalArgumentException> { FootprintSource.Box(1, 1, -1) }
    }

    @Test
    fun `cells variant passes through`() {
        val cells = listOf(FootprintCell(0, 0, 0), FootprintCell(1, 0, 0))
        val resolved = FootprintSource.Cells(cells).resolveCells()
        assertEquals(cells, resolved)
    }

    @Test
    fun `cells rejects empty list`() {
        assertThrows<IllegalArgumentException> { FootprintSource.Cells(emptyList()) }
    }

    @Test
    fun `fromBones requires a lookup`() {
        val source = FootprintSource.FromBones("collider")
        assertThrows<IllegalStateException> { source.resolveCells() }
    }

    @Test
    fun `fromBones uses lookup when provided`() {
        val source = FootprintSource.FromBones("collider")
        val resolved = source.resolveCells { prefix ->
            assertEquals("collider", prefix)
            listOf(FootprintCell(0, 0, 0), FootprintCell(1, 0, 0))
        }
        assertEquals(2, resolved.size)
    }

    @Test
    fun `fromBones rejects blank prefix`() {
        assertThrows<IllegalArgumentException> { FootprintSource.FromBones("") }
    }

    @Test
    fun `SINGLE is one anchor cell`() {
        val cells = FootprintSource.SINGLE.resolveCells()
        assertEquals(listOf(FootprintCell(0, 0, 0)), cells)
    }
}
