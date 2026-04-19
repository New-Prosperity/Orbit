package me.nebula.orbit.utils.gui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NumberInputTest {

    @Test
    fun `parseIntOrNull valid`() {
        assertEquals(42, NumberInput.parseIntOrNull("42"))
        assertEquals(-7, NumberInput.parseIntOrNull("-7"))
        assertEquals(0, NumberInput.parseIntOrNull("0"))
    }

    @Test
    fun `parseIntOrNull invalid text`() {
        assertNull(NumberInput.parseIntOrNull("abc"))
        assertNull(NumberInput.parseIntOrNull(""))
        assertNull(NumberInput.parseIntOrNull("1.5"))
    }

    @Test
    fun `parseIntOrNull out of range`() {
        assertNull(NumberInput.parseIntOrNull("10", min = 100))
        assertNull(NumberInput.parseIntOrNull("10", max = 5))
        assertEquals(10, NumberInput.parseIntOrNull("10", min = 5, max = 20))
    }

    @Test
    fun `parseLongOrNull valid`() {
        assertEquals(99L, NumberInput.parseLongOrNull("99"))
        assertEquals(Long.MAX_VALUE, NumberInput.parseLongOrNull(Long.MAX_VALUE.toString()))
    }

    @Test
    fun `parseLongOrNull invalid`() {
        assertNull(NumberInput.parseLongOrNull("abc"))
        assertNull(NumberInput.parseLongOrNull("1e10"))
    }

    @Test
    fun `parseDoubleOrNull valid`() {
        assertEquals(3.14, NumberInput.parseDoubleOrNull("3.14"))
        assertEquals(-0.5, NumberInput.parseDoubleOrNull("-0.5"))
    }

    @Test
    fun `parseDoubleOrNull rejects NaN`() {
        assertNull(NumberInput.parseDoubleOrNull("NaN"))
    }

    @Test
    fun `parseDoubleOrNull respects range`() {
        assertNull(NumberInput.parseDoubleOrNull("1.5", min = 2.0))
        assertNull(NumberInput.parseDoubleOrNull("1.5", max = 1.0))
        assertEquals(1.5, NumberInput.parseDoubleOrNull("1.5", min = 1.0, max = 2.0))
    }

    @Test
    fun `parseIntOrNull handles whitespace`() {
        assertEquals(7, NumberInput.parseIntOrNull("  7 "))
    }
}
