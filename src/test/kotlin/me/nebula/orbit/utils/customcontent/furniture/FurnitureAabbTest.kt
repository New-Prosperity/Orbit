package me.nebula.orbit.utils.customcontent.furniture

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FurnitureAabbTest {

    @Test
    fun `cellsTouched single cell at origin`() {
        val aabb = CubeAabb(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
        assertEquals(listOf(FootprintCell(0, 0, 0)), aabb.cellsTouched())
    }

    @Test
    fun `cellsTouched multi-cell on X axis`() {
        val aabb = CubeAabb(0.0, 0.0, 0.0, 32.0, 16.0, 16.0)
        assertEquals(setOf(FootprintCell(0, 0, 0), FootprintCell(1, 0, 0)), aabb.cellsTouched().toSet())
    }

    @Test
    fun `clipToCell returns the intersection`() {
        val aabb = CubeAabb(0.0, 0.0, 0.0, 32.0, 16.0, 16.0)
        val clipped = aabb.clipToCell(FootprintCell(1, 0, 0))
        assertEquals(CubeAabb(16.0, 0.0, 0.0, 32.0, 16.0, 16.0), clipped)
    }

    @Test
    fun `clipToCell returns null on no intersection`() {
        val aabb = CubeAabb(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
        assertNull(aabb.clipToCell(FootprintCell(1, 0, 0)))
    }

    @Test
    fun `toCellLocal subtracts cell offset`() {
        val aabb = CubeAabb(20.0, 0.0, 20.0, 28.0, 16.0, 28.0)
        val local = aabb.toCellLocal(FootprintCell(1, 0, 1))
        assertEquals(CubeAabb(4.0, 0.0, 4.0, 12.0, 16.0, 12.0), local)
    }

    @Test
    fun `union combines two AABBs`() {
        val a = CubeAabb(0.0, 0.0, 0.0, 8.0, 8.0, 8.0)
        val b = CubeAabb(4.0, 4.0, 4.0, 16.0, 16.0, 16.0)
        assertEquals(CubeAabb(0.0, 0.0, 0.0, 16.0, 16.0, 16.0), a.union(b))
    }

    @Test
    fun `volume reports pixel cube volume`() {
        val aabb = CubeAabb(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
        assertEquals(4096.0, aabb.volumePixels)
    }

    @Test
    fun `cellsTouched excludes edge-aligned neighboring cell`() {
        val aabb = CubeAabb(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
        assertEquals(listOf(FootprintCell(0, 0, 0)), aabb.cellsTouched())
    }

    @Test
    fun `cellsTouched handles Y axis too`() {
        val aabb = CubeAabb(0.0, 0.0, 0.0, 16.0, 32.0, 16.0)
        assertEquals(setOf(FootprintCell(0, 0, 0), FootprintCell(0, 1, 0)), aabb.cellsTouched().toSet())
    }
}
