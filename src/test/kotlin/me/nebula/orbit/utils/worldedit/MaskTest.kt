package me.nebula.orbit.utils.worldedit

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaskTest {

    private val even: Mask = Mask { it % 2 == 0 }
    private val positive: Mask = Mask { it > 0 }

    @Test
    fun `mask functional interface tests value`() {
        assertTrue(even.test(4))
        assertFalse(even.test(5))
    }

    @Test
    fun `not mask inverts result`() {
        val odd = Masks.not(even)
        assertTrue(odd.test(5))
        assertFalse(odd.test(4))
    }

    @Test
    fun `any mask returns true if any matches`() {
        val combined = Masks.any(even, positive)
        assertTrue(combined.test(4))
        assertTrue(combined.test(3))
        assertFalse(combined.test(-3))
    }

    @Test
    fun `any with empty list returns false`() {
        val combined = Masks.any()
        assertFalse(combined.test(0))
    }

    @Test
    fun `all mask requires every match`() {
        val combined = Masks.all(even, positive)
        assertTrue(combined.test(4))
        assertFalse(combined.test(-2))
        assertFalse(combined.test(3))
    }

    @Test
    fun `all with empty list returns true`() {
        val combined = Masks.all()
        assertTrue(combined.test(0))
    }

    @Test
    fun `chained combinators`() {
        val notEven = Masks.not(even)
        val oddAndPositive = Masks.all(notEven, positive)
        assertTrue(oddAndPositive.test(3))
        assertFalse(oddAndPositive.test(2))
        assertFalse(oddAndPositive.test(-3))
    }

    @Test
    fun `double not is identity`() {
        val doubled = Masks.not(Masks.not(even))
        assertTrue(doubled.test(4))
        assertFalse(doubled.test(5))
    }
}
