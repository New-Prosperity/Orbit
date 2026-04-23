package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.translation.TranslationKey
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class FurnitureInteractionTest {

    @Test
    fun `seat default offset is 0 dot 4`() {
        val seat = FurnitureInteraction.Seat()
        assertEquals(0.4, seat.offsetY)
        assertEquals(0f, seat.yawOffsetDegrees)
        assertEquals(DismountTrigger.Sneak, seat.dismount)
    }

    @Test
    fun `seat respects custom offset`() {
        val seat = FurnitureInteraction.Seat(offsetY = 0.75, yawOffsetDegrees = 15f)
        assertEquals(0.75, seat.offsetY)
        assertEquals(15f, seat.yawOffsetDegrees)
    }

    @Test
    fun `open close carries both item ids`() {
        val oc = FurnitureInteraction.OpenClose("item_cabinet_open", "item_cabinet_closed")
        assertEquals("item_cabinet_open", oc.openItemId)
        assertEquals("item_cabinet_closed", oc.closedItemId)
    }

    @Test
    fun `loot container default rows is 3`() {
        val lc = FurnitureInteraction.LootContainer()
        assertEquals(3, lc.rows)
    }

    @Test
    fun `loot container rejects invalid rows`() {
        assertThrows<IllegalArgumentException> { FurnitureInteraction.LootContainer(rows = 0) }
        assertThrows<IllegalArgumentException> { FurnitureInteraction.LootContainer(rows = 7) }
    }

    @Test
    fun `loot container supports titleKey`() {
        val lc = FurnitureInteraction.LootContainer(rows = 6, titleKey = TranslationKey("orbit.furniture.chest.title"))
        assertEquals(6, lc.rows)
        assertEquals("orbit.furniture.chest.title", lc.titleKey?.value)
    }

    @Test
    fun `custom rejects blank handlerId`() {
        assertThrows<IllegalArgumentException> { FurnitureInteraction.Custom("") }
    }
}
