package me.nebula.orbit.utils.anticheat

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViolationTrackerTest {

    @Test
    fun `new tracker has zero violations`() {
        val tracker = ViolationTracker(windowMs = 30_000L)
        assertEquals(0, tracker.totalViolations())
    }

    @Test
    fun `adding violations sums weights`() {
        val tracker = ViolationTracker(windowMs = 30_000L)
        tracker.addViolation("speed", weight = 1)
        tracker.addViolation("fly", weight = 2)
        tracker.addViolation("reach", weight = 3)
        assertEquals(6, tracker.totalViolations())
    }

    @Test
    fun `default weight is 1`() {
        val tracker = ViolationTracker(windowMs = 30_000L)
        tracker.addViolation("speed")
        tracker.addViolation("speed")
        assertEquals(2, tracker.totalViolations())
    }

    @Test
    fun `shouldKick is false below threshold`() {
        val tracker = ViolationTracker(windowMs = 30_000L)
        tracker.addViolation("a", weight = 4)
        assertFalse(tracker.shouldKick(threshold = 5))
    }

    @Test
    fun `shouldKick is true at threshold`() {
        val tracker = ViolationTracker(windowMs = 30_000L)
        tracker.addViolation("a", weight = 5)
        assertTrue(tracker.shouldKick(threshold = 5))
    }

    @Test
    fun `shouldKick is true above threshold`() {
        val tracker = ViolationTracker(windowMs = 30_000L)
        tracker.addViolation("a", weight = 10)
        assertTrue(tracker.shouldKick(threshold = 5))
    }

    @Test
    fun `expired violations are pruned`() {
        val tracker = ViolationTracker(windowMs = 0L)
        tracker.addViolation("a", weight = 5)
        Thread.sleep(10)
        assertEquals(0, tracker.totalViolations())
    }

    @Test
    fun `mixing weights aggregates correctly`() {
        val tracker = ViolationTracker(windowMs = 30_000L)
        tracker.addViolation("a", weight = 3)
        tracker.addViolation("b", weight = 7)
        tracker.addViolation("c", weight = 2)
        assertEquals(12, tracker.totalViolations())
    }
}
