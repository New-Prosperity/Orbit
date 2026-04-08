package me.nebula.orbit.utils.condition

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConditionTest {

    private val alwaysTrue: Condition<Int> = Condition { true }
    private val alwaysFalse: Condition<Int> = Condition { false }
    private val isPositive: Condition<Int> = Condition { it > 0 }
    private val isEven: Condition<Int> = Condition { it % 2 == 0 }

    @Test
    fun `single condition tests directly`() {
        assertTrue(isPositive.test(5))
        assertFalse(isPositive.test(-1))
    }

    @Test
    fun `and combinator returns true when both true`() {
        val combined = isPositive and isEven
        assertTrue(combined.test(4))
    }

    @Test
    fun `and combinator returns false when one false`() {
        val combined = isPositive and isEven
        assertFalse(combined.test(3))
        assertFalse(combined.test(-2))
    }

    @Test
    fun `or combinator returns true when either true`() {
        val combined = isPositive or isEven
        assertTrue(combined.test(3))
        assertTrue(combined.test(-2))
        assertTrue(combined.test(4))
    }

    @Test
    fun `or combinator returns false when both false`() {
        val combined = isPositive or isEven
        assertFalse(combined.test(-1))
    }

    @Test
    fun `xor combinator returns true when exactly one is true`() {
        val combined = isPositive xor isEven
        assertTrue(combined.test(3))
        assertTrue(combined.test(-2))
    }

    @Test
    fun `xor combinator returns false when both true or both false`() {
        val combined = isPositive xor isEven
        assertFalse(combined.test(4))
        assertFalse(combined.test(-1))
    }

    @Test
    fun `not operator inverts result`() {
        val negated = !isPositive
        assertTrue(negated.test(-1))
        assertFalse(negated.test(5))
    }

    @Test
    fun `not function inverts condition`() {
        val negated = not(isPositive)
        assertTrue(negated.test(-1))
        assertFalse(negated.test(5))
    }

    @Test
    fun `allOf returns true when every condition true`() {
        val combined = allOf(isPositive, isEven, alwaysTrue)
        assertTrue(combined.test(4))
        assertFalse(combined.test(3))
    }

    @Test
    fun `allOf with empty list returns true`() {
        val combined = allOf<Int>()
        assertTrue(combined.test(0))
    }

    @Test
    fun `anyOf returns true when at least one is true`() {
        val combined = anyOf(isPositive, isEven)
        assertTrue(combined.test(3))
        assertTrue(combined.test(-2))
        assertFalse(combined.test(-1))
    }

    @Test
    fun `anyOf with empty list returns false`() {
        val combined = anyOf<Int>()
        assertFalse(combined.test(0))
    }

    @Test
    fun `noneOf returns true only if all conditions false`() {
        val combined = noneOf(isPositive, isEven)
        assertTrue(combined.test(-1))
        assertFalse(combined.test(2))
        assertFalse(combined.test(3))
    }

    @Test
    fun `chained combinators`() {
        val combined = (isPositive and isEven) or alwaysFalse
        assertTrue(combined.test(4))
        assertFalse(combined.test(3))
        assertFalse(combined.test(-2))
    }

    @Test
    fun `double negation returns original`() {
        val doubled = !(!isPositive)
        assertTrue(doubled.test(5))
        assertFalse(doubled.test(-1))
    }
}
