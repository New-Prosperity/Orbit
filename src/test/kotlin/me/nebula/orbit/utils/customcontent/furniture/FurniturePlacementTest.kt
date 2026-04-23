package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.orbit.utils.modelengine.math.eulerToQuat
import me.nebula.orbit.utils.modelengine.math.quatRotateVec
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.BlockFace
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FurniturePlacementTest {

    private fun assertVecNear(expected: Vec, actual: Vec, epsilon: Double = 1e-5) {
        assertTrue(abs(expected.x() - actual.x()) < epsilon, "x: expected ${expected.x()} got ${actual.x()}")
        assertTrue(abs(expected.y() - actual.y()) < epsilon, "y: expected ${expected.y()} got ${actual.y()}")
        assertTrue(abs(expected.z() - actual.z()) < epsilon, "z: expected ${expected.z()} got ${actual.z()}")
    }

    private fun applyEuler(euler: FurniturePlacementRotation.Euler, v: Vec): Vec {
        val q = eulerToQuat(euler.pitchDegrees, euler.yawDegrees, euler.rollDegrees)
        return quatRotateVec(q, v)
    }

    @Test
    fun `FLOOR profile defaults to top only and no auto orient`() {
        val p = FurniturePlacement.FLOOR
        assertEquals(setOf(BlockFace.TOP), p.allowedFaces)
        assertFalse(p.autoOrient)
    }

    @Test
    fun `CEILING allows top and bottom`() {
        assertEquals(setOf(BlockFace.TOP, BlockFace.BOTTOM), FurniturePlacement.CEILING.allowedFaces)
        assertTrue(FurniturePlacement.CEILING.autoOrient)
    }

    @Test
    fun `WALL allows four horizontal faces`() {
        assertEquals(
            setOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST),
            FurniturePlacement.WALL.allowedFaces,
        )
    }

    @Test
    fun `ANY_AXIS allows all six faces`() {
        assertEquals(6, FurniturePlacement.ANY_AXIS.allowedFaces.size)
    }

    @Test
    fun `byName resolves canonical names case insensitively`() {
        assertEquals(FurniturePlacement.FLOOR, FurniturePlacement.byName("Floor"))
        assertEquals(FurniturePlacement.CEILING, FurniturePlacement.byName("CEILING"))
        assertEquals(FurniturePlacement.WALL, FurniturePlacement.byName("wall"))
        assertEquals(FurniturePlacement.ANY_AXIS, FurniturePlacement.byName("any"))
        assertEquals(FurniturePlacement.ANY_AXIS, FurniturePlacement.byName("any_axis"))
    }

    @Test
    fun `byName returns null for unknown`() {
        assertNull(FurniturePlacement.byName("garbage"))
    }

    @Test
    fun `non auto-orient keeps yaw only`() {
        val e = FurniturePlacementRotation.eulerFor(BlockFace.NORTH, 42f, autoOrient = false)
        assertEquals(42f, e.yawDegrees)
        assertEquals(0f, e.pitchDegrees)
        assertEquals(0f, e.rollDegrees)
    }

    @Test
    fun `TOP auto-orient keeps upright with player yaw`() {
        val e = FurniturePlacementRotation.eulerFor(BlockFace.TOP, 90f, autoOrient = true)
        assertEquals(90f, e.yawDegrees)
        assertEquals(0f, e.pitchDegrees)
        assertEquals(0f, e.rollDegrees)
        val upRotated = applyEuler(e, Vec(0.0, 1.0, 0.0))
        assertVecNear(Vec(0.0, 1.0, 0.0), upRotated)
    }

    @Test
    fun `BOTTOM auto-orient flips up to down`() {
        val e = FurniturePlacementRotation.eulerFor(BlockFace.BOTTOM, 0f, autoOrient = true)
        val upRotated = applyEuler(e, Vec(0.0, 1.0, 0.0))
        assertVecNear(Vec(0.0, -1.0, 0.0), upRotated)
    }

    @Test
    fun `NORTH auto-orient lays up toward minus Z`() {
        val e = FurniturePlacementRotation.eulerFor(BlockFace.NORTH, 0f, autoOrient = true)
        val upRotated = applyEuler(e, Vec(0.0, 1.0, 0.0))
        assertVecNear(Vec(0.0, 0.0, -1.0), upRotated)
    }

    @Test
    fun `SOUTH auto-orient lays up toward plus Z`() {
        val e = FurniturePlacementRotation.eulerFor(BlockFace.SOUTH, 0f, autoOrient = true)
        val upRotated = applyEuler(e, Vec(0.0, 1.0, 0.0))
        assertVecNear(Vec(0.0, 0.0, 1.0), upRotated)
    }

    @Test
    fun `EAST auto-orient lays up toward plus X`() {
        val e = FurniturePlacementRotation.eulerFor(BlockFace.EAST, 0f, autoOrient = true)
        val upRotated = applyEuler(e, Vec(0.0, 1.0, 0.0))
        assertVecNear(Vec(1.0, 0.0, 0.0), upRotated)
    }

    @Test
    fun `WEST auto-orient lays up toward minus X`() {
        val e = FurniturePlacementRotation.eulerFor(BlockFace.WEST, 0f, autoOrient = true)
        val upRotated = applyEuler(e, Vec(0.0, 1.0, 0.0))
        assertVecNear(Vec(-1.0, 0.0, 0.0), upRotated)
    }

    @Test
    fun `allows returns true only for configured faces`() {
        val wall = FurniturePlacement.WALL
        assertTrue(wall.allows(BlockFace.NORTH))
        assertTrue(wall.allows(BlockFace.SOUTH))
        assertFalse(wall.allows(BlockFace.TOP))
        assertFalse(wall.allows(BlockFace.BOTTOM))
    }

    @Test
    fun `any axis allows every face`() {
        val any = FurniturePlacement.ANY_AXIS
        BlockFace.values().forEach { assertTrue(any.allows(it), "expected any axis to allow $it") }
    }

    @Test
    fun `custom placement rejects empty allowed faces`() {
        val result = runCatching { FurniturePlacement(emptySet(), autoOrient = true) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
