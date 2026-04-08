package me.nebula.orbit.utils.blockindex

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BlockPositionIndexTest {

    @Test
    fun `pack and unpack round-trip positive coordinates`() {
        val packed = BlockPositionIndex.pack(100, 64, 200)
        val (x, y, z) = BlockPositionIndex.unpack(packed)
        assertEquals(100, x)
        assertEquals(64, y)
        assertEquals(200, z)
    }

    @Test
    fun `pack and unpack round-trip negative coordinates`() {
        val packed = BlockPositionIndex.pack(-100, -64, -200)
        val (x, y, z) = BlockPositionIndex.unpack(packed)
        assertEquals(-100, x)
        assertEquals(-64, y)
        assertEquals(-200, z)
    }

    @Test
    fun `pack and unpack round-trip zero`() {
        val packed = BlockPositionIndex.pack(0, 0, 0)
        val (x, y, z) = BlockPositionIndex.unpack(packed)
        assertEquals(0, x)
        assertEquals(0, y)
        assertEquals(0, z)
    }

    @Test
    fun `pack and unpack round-trip mixed coordinates`() {
        val packed = BlockPositionIndex.pack(1234, -32, -7890)
        val (x, y, z) = BlockPositionIndex.unpack(packed)
        assertEquals(1234, x)
        assertEquals(-32, y)
        assertEquals(-7890, z)
    }

    @Test
    fun `pack and unpack edge case minimum y`() {
        val packed = BlockPositionIndex.pack(0, -64, 0)
        val (_, y, _) = BlockPositionIndex.unpack(packed)
        assertEquals(-64, y)
    }

    @Test
    fun `pack and unpack edge case maximum y`() {
        val packed = BlockPositionIndex.pack(0, 320, 0)
        val (_, y, _) = BlockPositionIndex.unpack(packed)
        assertEquals(320, y)
    }

    @Test
    fun `different positions produce different packed values`() {
        val a = BlockPositionIndex.pack(0, 0, 0)
        val b = BlockPositionIndex.pack(1, 0, 0)
        val c = BlockPositionIndex.pack(0, 1, 0)
        val d = BlockPositionIndex.pack(0, 0, 1)
        assertEquals(4, setOf(a, b, c, d).size)
    }
}
