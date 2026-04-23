package me.nebula.orbit.mode.game.battleroyale.spawn

import net.minestom.server.coordinate.Pos
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpawnImmunityControllerTest {

    private class Clock(start: Long = 1_000_000L) {
        var now: Long = start
        fun advance(ms: Long) { now += ms }
        fun lambda(): () -> Long = { now }
    }

    @Test
    fun `grant registers an active immunity record`() {
        val clock = Clock()
        val ctrl = SpawnImmunityController(defaultTimeoutMs = 10_000L, clock = clock.lambda())
        val uuid = UUID.randomUUID()
        ctrl.grant(uuid, Pos(0.0, 0.0, 0.0))
        assertTrue(ctrl.isImmune(uuid))
        assertEquals(setOf(uuid), ctrl.activeUuids())
    }

    @Test
    fun `isImmune expires after timeout`() {
        val clock = Clock()
        val ctrl = SpawnImmunityController(defaultTimeoutMs = 10_000L, clock = clock.lambda())
        val uuid = UUID.randomUUID()
        ctrl.grant(uuid, Pos(0.0, 0.0, 0.0))
        clock.advance(9_999L)
        assertTrue(ctrl.isImmune(uuid))
        clock.advance(1L)
        assertFalse(ctrl.isImmune(uuid))
        assertTrue(ctrl.activeUuids().isEmpty())
    }

    @Test
    fun `checkMovement releases when player moves past threshold`() {
        val clock = Clock()
        val ctrl = SpawnImmunityController(defaultTimeoutMs = 10_000L, movementThresholdBlocks = 3.0, clock = clock.lambda())
        val uuid = UUID.randomUUID()
        ctrl.grant(uuid, Pos(0.0, 0.0, 0.0))
        ctrl.checkMovement(uuid, Pos(2.0, 0.0, 2.0))
        assertTrue(ctrl.isImmune(uuid), "hypot 2.83 < threshold 3.0 — still immune")
        ctrl.checkMovement(uuid, Pos(3.0, 0.0, 3.0))
        assertFalse(ctrl.isImmune(uuid), "hypot 4.24 >= threshold 3.0 — released")
    }

    @Test
    fun `movement threshold ignores vertical distance`() {
        val clock = Clock()
        val ctrl = SpawnImmunityController(defaultTimeoutMs = 10_000L, movementThresholdBlocks = 3.0, clock = clock.lambda())
        val uuid = UUID.randomUUID()
        ctrl.grant(uuid, Pos(0.0, 65.0, 0.0))
        ctrl.checkMovement(uuid, Pos(0.0, 200.0, 0.0))
        assertTrue(ctrl.isImmune(uuid), "vertical-only movement must not break immunity")
    }

    @Test
    fun `onDamageTaken releases immunity and returns true when one existed`() {
        val clock = Clock()
        val ctrl = SpawnImmunityController(clock = clock.lambda())
        val uuid = UUID.randomUUID()
        ctrl.grant(uuid, Pos(0.0, 0.0, 0.0))
        assertTrue(ctrl.onDamageTaken(uuid))
        assertFalse(ctrl.isImmune(uuid))
        assertFalse(ctrl.onDamageTaken(uuid), "second call with no record returns false")
    }

    @Test
    fun `grantAll populates map entries`() {
        val clock = Clock()
        val ctrl = SpawnImmunityController(clock = clock.lambda())
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        ctrl.grantAll(mapOf(a to Pos(0.0, 0.0, 0.0), b to Pos(50.0, 0.0, 50.0)))
        assertEquals(2, ctrl.size())
        assertTrue(ctrl.isImmune(a))
        assertTrue(ctrl.isImmune(b))
    }

    @Test
    fun `releaseAll clears every entry`() {
        val clock = Clock()
        val ctrl = SpawnImmunityController(clock = clock.lambda())
        ctrl.grant(UUID.randomUUID(), Pos(0.0, 0.0, 0.0))
        ctrl.grant(UUID.randomUUID(), Pos(0.0, 0.0, 0.0))
        ctrl.releaseAll()
        assertEquals(0, ctrl.size())
    }

    @Test
    fun `timeout overrides the default value per call`() {
        val clock = Clock()
        val ctrl = SpawnImmunityController(defaultTimeoutMs = 10_000L, clock = clock.lambda())
        val uuid = UUID.randomUUID()
        ctrl.grant(uuid, Pos(0.0, 0.0, 0.0), timeoutMs = 500L)
        clock.advance(499L)
        assertTrue(ctrl.isImmune(uuid))
        clock.advance(1L)
        assertFalse(ctrl.isImmune(uuid))
    }
}
