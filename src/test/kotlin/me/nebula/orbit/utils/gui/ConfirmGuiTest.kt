package me.nebula.orbit.utils.gui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConfirmGuiTest {

    @Test
    fun `outcome enum has three values`() {
        assertEquals(3, ConfirmOutcome.entries.size)
    }

    @Test
    fun `outcome enum values`() {
        assertNotNull(ConfirmOutcome.CONFIRM)
        assertNotNull(ConfirmOutcome.CANCEL)
        assertNotNull(ConfirmOutcome.TIMEOUT)
    }

    @Test
    fun `confirmGuiBuilder defaults are sensible`() {
        val b = ConfirmGuiBuilder("t")
        assertEquals(3, b.rows)
        assertEquals(11, b.confirmSlot)
        assertEquals(15, b.cancelSlot)
        assertEquals(13, b.previewSlot)
    }

    @Test
    fun `confirmGuiBuilder rows setter works`() {
        val b = ConfirmGuiBuilder("t")
        b.rows(5)
        assertEquals(5, b.rows)
    }

    @Test
    fun `confirmGuiBuilder confirm setter works`() {
        val b = ConfirmGuiBuilder("t")
        b.confirm(20)
        assertEquals(20, b.confirmSlot)
    }

    @Test
    fun `confirmGuiBuilder cancel setter works`() {
        val b = ConfirmGuiBuilder("t")
        b.cancel(21)
        assertEquals(21, b.cancelSlot)
    }
}
