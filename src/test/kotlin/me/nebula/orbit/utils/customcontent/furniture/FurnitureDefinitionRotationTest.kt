package me.nebula.orbit.utils.customcontent.furniture

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FurnitureDefinitionRotationTest {

    @Test
    fun `default visualRotationSnap is zero meaning continuous`() {
        val d = FurnitureDefinition(id = "x", itemId = "y")
        assertEquals(0.0, d.visualRotationSnap)
    }

    @Test
    fun `visualRotationSnap 22 point 5 still supported as opt in`() {
        val d = FurnitureDefinition(id = "x", itemId = "y", visualRotationSnap = 22.5)
        assertEquals(22.5, d.visualRotationSnap)
    }

    @Test
    fun `continuous snap passes raw yaw through unchanged`() {
        val d = FurnitureDefinition(id = "x", itemId = "y", visualRotationSnap = 0.0)
        val yaw = FootprintRotation.snapYaw(37.3f, d.visualRotationSnap)
        assertEquals(37.3f, yaw)
    }

    @Test
    fun `visualRotationSnap rejects negative values`() {
        assertThrows<IllegalArgumentException> {
            FurnitureDefinition(id = "x", itemId = "y", visualRotationSnap = -1.0)
        }
    }

    @Test
    fun `visualRotationSnap must evenly divide 360`() {
        assertThrows<IllegalArgumentException> {
            FurnitureDefinition(id = "x", itemId = "y", visualRotationSnap = 7.0)
        }
    }

    @Test
    fun `visualRotationSnap accepts common values`() {
        listOf(15.0, 22.5, 30.0, 45.0, 60.0, 90.0, 180.0, 360.0).forEach { snap ->
            val d = FurnitureDefinition(id = "x", itemId = "y", visualRotationSnap = snap)
            assertEquals(snap, d.visualRotationSnap)
        }
    }

    @Test
    fun `cellKeysOf applies quarter turn rotation to footprint before projecting`() {
        val footprint = FurnitureFootprint(listOf(
            FootprintCell(0, 0, 0),
            FootprintCell(1, 0, 0),
        ))
        val d = FurnitureDefinition(id = "table", itemId = "item_table", footprint = footprint)
        val keys0 = FurnitureInstance.cellKeysOf(d, 10, 64, 20, quarterTurns = 0)
        val keys1 = FurnitureInstance.cellKeysOf(d, 10, 64, 20, quarterTurns = 1)
        val keys2 = FurnitureInstance.cellKeysOf(d, 10, 64, 20, quarterTurns = 2)
        val keys3 = FurnitureInstance.cellKeysOf(d, 10, 64, 20, quarterTurns = 3)

        assertEquals(
            setOf(FurnitureInstance.packKey(10, 64, 20), FurnitureInstance.packKey(11, 64, 20)),
            keys0.toSet(),
        )
        assertEquals(
            setOf(FurnitureInstance.packKey(10, 64, 20), FurnitureInstance.packKey(10, 64, 21)),
            keys1.toSet(),
        )
        assertEquals(
            setOf(FurnitureInstance.packKey(10, 64, 20), FurnitureInstance.packKey(9, 64, 20)),
            keys2.toSet(),
        )
        assertEquals(
            setOf(FurnitureInstance.packKey(10, 64, 20), FurnitureInstance.packKey(10, 64, 19)),
            keys3.toSet(),
        )
    }

    @Test
    fun `cellKeysOf default quarterTurns is zero`() {
        val footprint = FurnitureFootprint(listOf(
            FootprintCell(0, 0, 0),
            FootprintCell(1, 0, 0),
        ))
        val d = FurnitureDefinition(id = "table", itemId = "item_table", footprint = footprint)
        val keys = FurnitureInstance.cellKeysOf(d, 0, 0, 0)
        assertTrue(keys.contains(FurnitureInstance.packKey(0, 0, 0)))
        assertTrue(keys.contains(FurnitureInstance.packKey(1, 0, 0)))
    }
}
