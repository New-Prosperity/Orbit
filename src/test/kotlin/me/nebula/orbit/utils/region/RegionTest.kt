package me.nebula.orbit.utils.region

import net.minestom.server.coordinate.Pos
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionTest {

    @Test
    fun `cuboid contains point in interior`() {
        val region = CuboidRegion("box", Pos(0.0, 0.0, 0.0), Pos(10.0, 10.0, 10.0))
        assertTrue(region.contains(5.0, 5.0, 5.0))
    }

    @Test
    fun `cuboid contains point at boundary`() {
        val region = CuboidRegion("box", Pos(0.0, 0.0, 0.0), Pos(10.0, 10.0, 10.0))
        assertTrue(region.contains(0.0, 0.0, 0.0))
        assertTrue(region.contains(10.0, 10.0, 10.0))
    }

    @Test
    fun `cuboid excludes point outside`() {
        val region = CuboidRegion("box", Pos(0.0, 0.0, 0.0), Pos(10.0, 10.0, 10.0))
        assertFalse(region.contains(11.0, 5.0, 5.0))
        assertFalse(region.contains(5.0, -1.0, 5.0))
        assertFalse(region.contains(5.0, 5.0, 100.0))
    }

    @Test
    fun `cuboid size and center`() {
        val region = CuboidRegion("box", Pos(0.0, 0.0, 0.0), Pos(10.0, 20.0, 30.0))
        assertEquals(10.0, region.sizeX)
        assertEquals(20.0, region.sizeY)
        assertEquals(30.0, region.sizeZ)
        assertEquals(Pos(5.0, 10.0, 15.0), region.center)
    }

    @Test
    fun `cuboid volume`() {
        val region = CuboidRegion("box", Pos(0.0, 0.0, 0.0), Pos(2.0, 3.0, 4.0))
        assertEquals(24.0, region.volume)
    }

    @Test
    fun `cuboidRegion factory normalizes min and max`() {
        val region = cuboidRegion("box", Pos(10.0, 10.0, 10.0), Pos(0.0, 0.0, 0.0))
        assertEquals(0.0, region.min.x())
        assertEquals(10.0, region.max.x())
        assertTrue(region.contains(5.0, 5.0, 5.0))
    }

    @Test
    fun `sphere contains center`() {
        val region = SphereRegion("ball", Pos(0.0, 0.0, 0.0), radius = 5.0)
        assertTrue(region.contains(0.0, 0.0, 0.0))
    }

    @Test
    fun `sphere contains point at radius`() {
        val region = SphereRegion("ball", Pos(0.0, 0.0, 0.0), radius = 5.0)
        assertTrue(region.contains(5.0, 0.0, 0.0))
        assertTrue(region.contains(0.0, 5.0, 0.0))
        assertTrue(region.contains(0.0, 0.0, 5.0))
    }

    @Test
    fun `sphere excludes point outside radius`() {
        val region = SphereRegion("ball", Pos(0.0, 0.0, 0.0), radius = 5.0)
        assertFalse(region.contains(5.1, 0.0, 0.0))
        assertFalse(region.contains(10.0, 10.0, 10.0))
    }

    @Test
    fun `sphere volume formula`() {
        val region = SphereRegion("ball", Pos(0.0, 0.0, 0.0), radius = 3.0)
        val expected = (4.0 / 3.0) * PI * 27.0
        assertEquals(expected, region.volume, 0.001)
    }

    @Test
    fun `sphere offset center`() {
        val region = SphereRegion("ball", Pos(100.0, 50.0, -20.0), radius = 5.0)
        assertTrue(region.contains(102.0, 51.0, -19.0))
        assertFalse(region.contains(0.0, 0.0, 0.0))
    }

    @Test
    fun `cylinder contains point inside footprint and height`() {
        val region = CylinderRegion("col", Pos(0.0, 0.0, 0.0), radius = 5.0, height = 10.0)
        assertTrue(region.contains(0.0, 5.0, 0.0))
        assertTrue(region.contains(3.0, 5.0, 4.0))
    }

    @Test
    fun `cylinder excludes point above or below`() {
        val region = CylinderRegion("col", Pos(0.0, 0.0, 0.0), radius = 5.0, height = 10.0)
        assertFalse(region.contains(0.0, 11.0, 0.0))
        assertFalse(region.contains(0.0, -1.0, 0.0))
    }

    @Test
    fun `cylinder excludes point outside radius`() {
        val region = CylinderRegion("col", Pos(0.0, 0.0, 0.0), radius = 5.0, height = 10.0)
        assertFalse(region.contains(6.0, 5.0, 0.0))
        assertFalse(region.contains(0.0, 5.0, 6.0))
    }

    @Test
    fun `cylinder boundary cases`() {
        val region = CylinderRegion("col", Pos(0.0, 0.0, 0.0), radius = 5.0, height = 10.0)
        assertTrue(region.contains(5.0, 0.0, 0.0))
        assertTrue(region.contains(0.0, 10.0, 0.0))
    }
}
