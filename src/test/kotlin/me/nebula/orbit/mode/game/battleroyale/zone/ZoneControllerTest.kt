package me.nebula.orbit.mode.game.battleroyale.zone

import me.nebula.orbit.event.GameEvent
import me.nebula.orbit.event.GameEventBus
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZoneControllerTest {

    private class FakeDriver : ZoneBorderDriver {
        override var currentDiameter: Double = 0.0
        override var currentCenterX: Double = 0.0
        override var currentCenterZ: Double = 0.0
        val snaps = mutableListOf<Triple<Double, Double, Double>>()
        val steps = mutableListOf<StepCall>()

        data class StepCall(val cx: Double, val cz: Double, val d: Double, val dur: Double)

        override fun snapTo(centerX: Double, centerZ: Double, diameter: Double) {
            currentCenterX = centerX
            currentCenterZ = centerZ
            currentDiameter = diameter
            snaps += Triple(centerX, centerZ, diameter)
        }

        override fun pushLerpStep(centerX: Double, centerZ: Double, diameter: Double, stepDurationSeconds: Double) {
            currentCenterX = centerX
            currentCenterZ = centerZ
            currentDiameter = diameter
            steps += StepCall(centerX, centerZ, diameter, stepDurationSeconds)
        }

        override fun isOutside(x: Double, z: Double): Boolean {
            val half = currentDiameter / 2
            return x < currentCenterX - half || x > currentCenterX + half ||
                z < currentCenterZ - half || z > currentCenterZ + half
        }
    }

    private class Clock(initial: Long = 1_000_000L) {
        var now: Long = initial
        fun advance(ms: Long) { now += ms }
        fun lambda(): () -> Long = { now }
    }

    private data class Harness(
        val controller: ZoneController,
        val driver: FakeDriver,
        val events: MutableList<GameEvent.ZoneTransition>,
        val clock: Clock,
    )

    private fun harness(
        zoneEnabled: Boolean = true,
        seed: Long = 42L,
        initialDamage: Float = 1f,
        initialDiameter: Double = 500.0,
    ): Harness {
        val bus = GameEventBus()
        val events = mutableListOf<GameEvent.ZoneTransition>()
        bus.subscribe<GameEvent.ZoneTransition> { events += it }
        val driver = FakeDriver()
        val clock = Clock()
        val controller = ZoneController(
            eventBus = bus,
            zoneShrinkingEnabled = { zoneEnabled },
            random = Random(seed),
            clock = clock.lambda(),
            pushIntervalTicks = 10,
        )
        controller.attach(driver, initialDiameter, 0.0, 0.0, initialDamage)
        return Harness(controller, driver, events, clock)
    }

    @Test
    fun `attach snaps driver and publishes Waiting`() {
        val h = harness()
        assertEquals(Triple(0.0, 0.0, 500.0), h.driver.snaps.single())
        assertTrue(h.controller.state is ZoneState.Waiting)
        assertTrue(h.events.any { it.to is ZoneState.Waiting })
    }

    @Test
    fun `shrinkBorderTo picks a new center inside current zone`() {
        val h = harness(seed = 7L)
        h.events.clear()
        h.controller.shrinkBorderTo(200.0, 60.0)

        val state = h.controller.state as ZoneState.Shrinking
        val maxOffset = (500.0 - 200.0) / 2.0
        assertTrue(state.toCenterX in -maxOffset..maxOffset, "new center X must be inside the old zone")
        assertTrue(state.toCenterZ in -maxOffset..maxOffset, "new center Z must be inside the old zone")
        assertEquals(200.0, state.toDiameter)
        assertEquals(60.0, state.durationSeconds)
    }

    @Test
    fun `shrinkBorderTo no-ops when ZONE_SHRINKING disabled`() {
        val h = harness(zoneEnabled = false)
        h.events.clear()
        h.controller.shrinkBorderTo(200.0, 60.0)
        assertTrue(h.controller.state is ZoneState.Waiting)
        assertTrue(h.events.isEmpty())
        assertTrue(h.driver.steps.isEmpty())
    }

    @Test
    fun `planShrink with lead transitions to Announcing and defers shrink until tick`() {
        val h = harness()
        h.events.clear()
        h.controller.planShrink(200.0, 60.0, announceLeadSeconds = 10.0)

        val announcing = h.controller.state as ZoneState.Announcing
        assertEquals(h.clock.now + 10_000L, announcing.shrinkStartsAtMs)
        assertEquals(1, h.events.size)

        h.clock.advance(5_000L)
        h.controller.tick(h.clock.now)
        assertTrue(h.controller.state is ZoneState.Announcing)

        h.clock.advance(5_000L)
        h.controller.tick(h.clock.now)
        assertTrue(h.controller.state is ZoneState.Shrinking)
    }

    @Test
    fun `tick pushes lerp steps at the configured interval and finalizes on completion`() {
        val h = harness()
        h.controller.shrinkBorderTo(100.0, 2.0)
        h.driver.steps.clear()

        h.clock.advance(500L)
        h.controller.tick(h.clock.now)
        assertEquals(1, h.driver.steps.size)

        h.clock.advance(250L)
        h.controller.tick(h.clock.now)
        assertEquals(1, h.driver.steps.size, "under the push interval — no extra push")

        h.clock.advance(2_000L)
        h.controller.tick(h.clock.now)
        val state = h.controller.state
        assertTrue(state is ZoneState.Static)
        state as ZoneState.Static
        assertEquals(100.0, state.diameter, 1e-9)
        assertEquals(h.driver.currentDiameter, 100.0, 1e-9)
    }

    @Test
    fun `setBorderDamage propagates onto the current Shrinking state`() {
        val h = harness()
        h.controller.shrinkBorderTo(200.0, 60.0)
        h.events.clear()
        h.controller.setBorderDamage(5.0)
        val state = h.controller.state as ZoneState.Shrinking
        assertEquals(5f, state.damagePerSecond)
        assertEquals(5f, h.controller.currentDamage)
    }

    @Test
    fun `setBorderDamage does not transition while Waiting`() {
        val h = harness()
        h.events.clear()
        h.controller.setBorderDamage(7.0)
        assertTrue(h.controller.state is ZoneState.Waiting)
        assertEquals(7f, h.controller.currentDamage)
        assertTrue(h.events.isEmpty())
    }

    @Test
    fun `startDeathmatch snapshots current driver position`() {
        val h = harness()
        h.controller.shrinkBorderTo(200.0, 60.0)
        h.clock.advance(500L)
        h.controller.tick(h.clock.now)
        h.events.clear()

        h.controller.startDeathmatch()
        val state = h.controller.state
        assertTrue(state is ZoneState.Deathmatch)
        assertEquals(h.driver.currentDiameter, (state as ZoneState.Deathmatch).diameter)
        assertEquals(1, h.events.size)
    }

    @Test
    fun `shrinkForDeathmatch picks a center and kicks off a shrink frame`() {
        val h = harness()
        h.events.clear()
        h.controller.shrinkForDeathmatch(50.0, 30.0)
        assertTrue(h.controller.state is ZoneState.Deathmatch)
    }

    @Test
    fun `end transitions to Ended preserving driver position`() {
        val h = harness()
        h.controller.shrinkBorderTo(200.0, 10.0)
        h.clock.advance(500L)
        h.controller.tick(h.clock.now)
        h.events.clear()

        h.controller.end()
        val state = h.controller.state as ZoneState.Ended
        assertEquals(h.driver.currentDiameter, state.diameter)
        assertEquals(1, h.events.size)
    }

    @Test
    fun `reset snaps driver back to initial diameter`() {
        val h = harness(initialDiameter = 800.0)
        h.controller.shrinkBorderTo(200.0, 60.0)
        h.driver.snaps.clear()
        h.controller.reset()
        assertEquals(Triple(0.0, 0.0, 800.0), h.driver.snaps.single())
        assertTrue(h.controller.state is ZoneState.Waiting)
    }

    @Test
    fun `pickInsideZone keeps new zone fully contained`() {
        val h = harness()
        repeat(32) {
            val (cx, cz) = h.controller.pickInsideZone(0.0, 0.0, 500.0, 100.0)
            val maxOffset = (500.0 - 100.0) / 2.0
            assertTrue(cx in -maxOffset..maxOffset)
            assertTrue(cz in -maxOffset..maxOffset)
        }
    }

    @Test
    fun `pickInsideZone returns identity when target is not smaller`() {
        val h = harness()
        val (cx, cz) = h.controller.pickInsideZone(10.0, -5.0, 200.0, 200.0)
        assertEquals(10.0, cx)
        assertEquals(-5.0, cz)
    }

    @Test
    fun `phase index increments across successive shrinks`() {
        val h = harness()
        h.controller.shrinkBorderTo(300.0, 30.0)
        h.clock.advance(30_000L)
        h.controller.tick(h.clock.now)
        h.controller.shrinkBorderTo(150.0, 30.0)
        val state = h.controller.state as ZoneState.Shrinking
        assertEquals(2, state.phaseIndex)
    }

    @Test
    fun `driver isOutside reflects current center after a push`() {
        val h = harness()
        h.controller.shrinkBorderTo(100.0, 1.0)
        h.clock.advance(500L)
        h.controller.tick(h.clock.now)
        val cx = h.driver.currentCenterX
        val cz = h.driver.currentCenterZ
        val d = h.driver.currentDiameter
        assertFalse(h.driver.isOutside(cx, cz))
        assertTrue(h.driver.isOutside(cx + d, cz))
    }
}
