package me.nebula.orbit.utils.customcontent.furniture

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FootprintRotationTest {

    @Test
    fun `zero turns returns the same footprint`() {
        val f = FurnitureFootprint(listOf(FootprintCell(0, 0, 0), FootprintCell(1, 0, 0)))
        assertEquals(f, FootprintRotation.rotate(f, 0))
    }

    @Test
    fun `90 degrees rotates plus X to plus Z`() {
        val f = FurnitureFootprint(listOf(FootprintCell(0, 0, 0), FootprintCell(1, 0, 0)))
        val rotated = FootprintRotation.rotate(f, 1)
        assertEquals(setOf(FootprintCell(0, 0, 0), FootprintCell(0, 0, 1)), rotated.cells.toSet())
    }

    @Test
    fun `180 degrees flips both axes`() {
        val f = FurnitureFootprint(listOf(FootprintCell(0, 0, 0), FootprintCell(2, 0, 1)))
        val rotated = FootprintRotation.rotate(f, 2)
        assertEquals(setOf(FootprintCell(0, 0, 0), FootprintCell(-2, 0, -1)), rotated.cells.toSet())
    }

    @Test
    fun `270 degrees rotates plus X to minus Z`() {
        val f = FurnitureFootprint(listOf(FootprintCell(0, 0, 0), FootprintCell(1, 0, 0)))
        val rotated = FootprintRotation.rotate(f, 3)
        assertEquals(setOf(FootprintCell(0, 0, 0), FootprintCell(0, 0, -1)), rotated.cells.toSet())
    }

    @Test
    fun `four quarter turns return to origin`() {
        val f = FurnitureFootprint(listOf(FootprintCell(0, 0, 0), FootprintCell(2, 0, 1), FootprintCell(-1, 0, 3)))
        val rotated = FootprintRotation.rotate(f, 4)
        assertEquals(f.cells.toSet(), rotated.cells.toSet())
    }

    @Test
    fun `negative quarter turns wrap correctly`() {
        val f = FurnitureFootprint(listOf(FootprintCell(0, 0, 0), FootprintCell(1, 0, 0)))
        val minusOne = FootprintRotation.rotate(f, -1)
        val threePositive = FootprintRotation.rotate(f, 3)
        assertEquals(threePositive.cells.toSet(), minusOne.cells.toSet())
    }

    @Test
    fun `y axis is preserved under rotation`() {
        val f = FurnitureFootprint(listOf(FootprintCell(0, 0, 0), FootprintCell(1, 2, 0)))
        val rotated = FootprintRotation.rotate(f, 1)
        assertEquals(setOf(FootprintCell(0, 0, 0), FootprintCell(0, 2, 1)), rotated.cells.toSet())
    }

    @Test
    fun `2x2 square footprint stays coherent under rotation`() {
        val cells = listOf(
            FootprintCell(0, 0, 0), FootprintCell(1, 0, 0),
            FootprintCell(0, 0, 1), FootprintCell(1, 0, 1),
        )
        val f = FurnitureFootprint(cells)
        val rotated = FootprintRotation.rotate(f, 1)
        val expected = setOf(
            FootprintCell(0, 0, 0), FootprintCell(0, 0, 1),
            FootprintCell(-1, 0, 0), FootprintCell(-1, 0, 1),
        )
        assertEquals(expected, rotated.cells.toSet())
    }

    @Test
    fun `L-shaped footprint rotates coherently across all four cardinals`() {
        val lShape = FurnitureFootprint(listOf(
            FootprintCell(0, 0, 0),
            FootprintCell(1, 0, 0),
            FootprintCell(0, 0, 1),
        ))
        val ninety = FootprintRotation.rotate(lShape, 1).cells.toSet()
        val oneEighty = FootprintRotation.rotate(lShape, 2).cells.toSet()
        val twoSeventy = FootprintRotation.rotate(lShape, 3).cells.toSet()
        assertEquals(setOf(FootprintCell(0, 0, 0), FootprintCell(0, 0, 1), FootprintCell(-1, 0, 0)), ninety)
        assertEquals(setOf(FootprintCell(0, 0, 0), FootprintCell(-1, 0, 0), FootprintCell(0, 0, -1)), oneEighty)
        assertEquals(setOf(FootprintCell(0, 0, 0), FootprintCell(0, 0, -1), FootprintCell(1, 0, 0)), twoSeventy)
    }

    @Test
    fun `yawToQuarterTurns snaps to nearest cardinal`() {
        assertEquals(0, FootprintRotation.yawToQuarterTurns(0f))
        assertEquals(1, FootprintRotation.yawToQuarterTurns(90f))
        assertEquals(2, FootprintRotation.yawToQuarterTurns(180f))
        assertEquals(3, FootprintRotation.yawToQuarterTurns(270f))
        assertEquals(0, FootprintRotation.yawToQuarterTurns(360f))
    }

    @Test
    fun `yawToQuarterTurns snaps to nearest quarter with tolerance`() {
        assertEquals(0, FootprintRotation.yawToQuarterTurns(44f))
        assertEquals(1, FootprintRotation.yawToQuarterTurns(46f))
        assertEquals(1, FootprintRotation.yawToQuarterTurns(134f))
        assertEquals(2, FootprintRotation.yawToQuarterTurns(136f))
    }

    @Test
    fun `yawToQuarterTurns handles negative yaw`() {
        assertEquals(3, FootprintRotation.yawToQuarterTurns(-90f))
        assertEquals(0, FootprintRotation.yawToQuarterTurns(-10f))
        assertEquals(2, FootprintRotation.yawToQuarterTurns(-180f))
    }

    @Test
    fun `snapYaw rounds to the nearest multiple`() {
        assertEquals(0f, FootprintRotation.snapYaw(10f, 22.5))
        assertEquals(22.5f, FootprintRotation.snapYaw(20f, 22.5))
        assertEquals(45f, FootprintRotation.snapYaw(40f, 22.5))
        assertEquals(45f, FootprintRotation.snapYaw(50f, 45.0))
        assertEquals(90f, FootprintRotation.snapYaw(91f, 22.5))
    }

    @Test
    fun `snapYaw returns raw value when snap is zero or negative`() {
        assertEquals(37f, FootprintRotation.snapYaw(37f, 0.0))
        assertEquals(37f, FootprintRotation.snapYaw(37f, -1.0))
    }

    @Test
    fun `snapYaw handles negatives by normalizing to zero to 360`() {
        assertEquals(270f, FootprintRotation.snapYaw(-90f, 22.5))
        assertEquals(0f, FootprintRotation.snapYaw(-5f, 45.0))
    }
}
