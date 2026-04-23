package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.orbit.utils.customcontent.block.BlockHitbox
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HitboxInferrerTest {

    @Test
    fun `full cell AABB infers Full`() {
        val aabb = CubeAabb(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
        assertEquals(BlockHitbox.Full, HitboxInferrer.bestFit(aabb))
    }

    @Test
    fun `bottom half AABB infers Slab`() {
        val aabb = CubeAabb(0.0, 0.0, 0.0, 16.0, 8.0, 16.0)
        assertEquals(BlockHitbox.Slab, HitboxInferrer.bestFit(aabb))
    }

    @Test
    fun `carpet thin AABB infers Thin`() {
        val aabb = CubeAabb(0.0, 0.0, 0.0, 16.0, 1.0, 16.0)
        assertEquals(BlockHitbox.Thin, HitboxInferrer.bestFit(aabb))
    }

    @Test
    fun `narrow tall pole infers Fence`() {
        val aabb = CubeAabb(6.0, 0.0, 6.0, 10.0, 16.0, 10.0)
        assertEquals(BlockHitbox.Fence, HitboxInferrer.bestFit(aabb))
    }

    @Test
    fun `thin plate against face infers Trapdoor`() {
        val aabb = CubeAabb(0.0, 14.0, 0.0, 16.0, 16.0, 16.0)
        val result = HitboxInferrer.bestFit(aabb)
        assertEquals(true, result == BlockHitbox.Trapdoor || result == BlockHitbox.Thin, "got $result")
    }

    @Test
    fun `near-full AABB still resolves to Full via fill ratio`() {
        val aabb = CubeAabb(0.0, 0.0, 0.0, 16.0, 15.5, 16.0)
        assertEquals(BlockHitbox.Full, HitboxInferrer.bestFit(aabb))
    }

    @Test
    fun `ambiguous weirdly-shaped AABB falls back to Full`() {
        val aabb = CubeAabb(4.0, 4.0, 4.0, 10.0, 12.0, 10.0)
        assertEquals(BlockHitbox.Full, HitboxInferrer.bestFit(aabb))
    }
}
