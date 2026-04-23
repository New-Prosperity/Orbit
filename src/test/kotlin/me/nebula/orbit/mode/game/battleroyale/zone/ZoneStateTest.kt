package me.nebula.orbit.mode.game.battleroyale.zone

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZoneStateTest {

    private fun shrink(
        from: Triple<Double, Double, Double> = Triple(0.0, 0.0, 500.0),
        to: Triple<Double, Double, Double> = Triple(100.0, 50.0, 100.0),
        startedAtMs: Long = 1_000_000L,
        duration: Double = 10.0,
        damage: Float = 1f,
    ) = ZoneState.Shrinking(
        phaseIndex = 1,
        fromCenterX = from.first, fromCenterZ = from.second, fromDiameter = from.third,
        toCenterX = to.first, toCenterZ = to.second, toDiameter = to.third,
        startedAtMs = startedAtMs, durationSeconds = duration, damagePerSecond = damage,
    )

    @Test
    fun `progress zero at start, one at end, clamps outside`() {
        val start = 1_000_000L
        val state = shrink(startedAtMs = start)
        assertEquals(0.0, state.progress(start - 5_000), 1e-9)
        assertEquals(0.0, state.progress(start), 1e-9)
        assertEquals(1.0, state.progress(start + 10_000), 1e-9)
        assertEquals(1.0, state.progress(start + 30_000), 1e-9)
    }

    @Test
    fun `center and diameter interpolate linearly`() {
        val start = 1_000_000L
        val state = shrink(
            from = Triple(0.0, 0.0, 500.0),
            to = Triple(200.0, 100.0, 100.0),
            startedAtMs = start,
            duration = 10.0,
        )
        assertEquals(100.0, state.currentCenterX(start + 5_000), 1e-9)
        assertEquals(50.0, state.currentCenterZ(start + 5_000), 1e-9)
        assertEquals(300.0, state.currentDiameter(start + 5_000), 1e-9)
    }

    @Test
    fun `interface accessors track clock`() {
        val state = shrink(startedAtMs = 0L, duration = 10.0)
        assertTrue(state.diameter in 0.0..500.0)
        assertTrue(state.centerX in -100.0..200.0)
    }

    @Test
    fun `remainingSeconds counts down to zero`() {
        val start = 1_000_000L
        val state = shrink(startedAtMs = start, duration = 10.0)
        assertEquals(10.0, state.remainingSeconds(start), 1e-9)
        assertEquals(3.0, state.remainingSeconds(start + 7_000), 1e-9)
        assertEquals(0.0, state.remainingSeconds(start + 10_000), 1e-9)
    }

    @Test
    fun `isComplete at duration boundary`() {
        val start = 1_000_000L
        val state = shrink(startedAtMs = start, duration = 10.0)
        assertFalse(state.isComplete(start + 9_999))
        assertTrue(state.isComplete(start + 10_000))
    }

    @Test
    fun `shrinking with zero duration is immediately complete`() {
        val state = shrink(startedAtMs = 0L, duration = 0.0)
        assertTrue(state.isComplete(0L))
        assertEquals(1.0, state.progress(0L), 1e-9)
    }

    @Test
    fun `static state carries centre diameter and damage`() {
        val state = ZoneState.Static(2, 10.0, -10.0, 120.0, 2.5f)
        assertEquals(10.0, state.centerX, 1e-9)
        assertEquals(-10.0, state.centerZ, 1e-9)
        assertEquals(120.0, state.diameter, 1e-9)
        assertEquals(2.5f, state.damagePerSecond)
    }

    @Test
    fun `waiting has zero damage by default`() {
        val state: ZoneState = ZoneState.Waiting(0.0, 0.0, 500.0)
        assertEquals(0f, state.damagePerSecond)
    }

    @Test
    fun `announcing state preserves current and future positions`() {
        val state = ZoneState.Announcing(
            phaseIndex = 1,
            centerX = 0.0, centerZ = 0.0, diameter = 500.0,
            nextCenterX = 100.0, nextCenterZ = -50.0, nextDiameter = 200.0,
            shrinkStartsAtMs = 5_000L,
            shrinkDurationSeconds = 30.0,
            damagePerSecond = 1f,
        )
        assertEquals(0.0, state.centerX, 1e-9)
        assertEquals(100.0, state.nextCenterX, 1e-9)
        assertEquals(200.0, state.nextDiameter, 1e-9)
    }
}
