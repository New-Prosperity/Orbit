package me.nebula.orbit.utils.gui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnvilResultTest {

    @Test
    fun `submitted carries text`() {
        val r: AnvilResult = AnvilResult.Submitted("hello")
        assertTrue(r is AnvilResult.Submitted)
        assertEquals("hello", r.text)
    }

    @Test
    fun `cancelled is singleton-like`() {
        val a = AnvilResult.Cancelled
        val b = AnvilResult.Cancelled
        assertEquals(a, b)
    }

    @Test
    fun `submitted equality`() {
        val a: AnvilResult = AnvilResult.Submitted("x")
        val b: AnvilResult = AnvilResult.Submitted("x")
        val c: AnvilResult = AnvilResult.Submitted("y")
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun `validation valid vs invalid`() {
        val valid: AnvilValidation = AnvilValidation.Valid
        val invalid: AnvilValidation = AnvilValidation.Invalid(null, "nope")
        assertTrue(valid is AnvilValidation.Valid)
        assertTrue(invalid is AnvilValidation.Invalid)
    }
}
