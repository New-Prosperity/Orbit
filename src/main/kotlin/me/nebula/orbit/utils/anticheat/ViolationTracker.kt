package me.nebula.orbit.utils.anticheat

import java.util.concurrent.ConcurrentLinkedDeque

data class Violation(val type: String, val weight: Int, val timestamp: Long)

class ViolationTracker(private val windowMs: Long = 30_000L) {

    private val violations = ConcurrentLinkedDeque<Violation>()

    fun addViolation(type: String, weight: Int = 1) {
        val now = System.currentTimeMillis()
        violations.addLast(Violation(type, weight, now))
        pruneExpired(now)
    }

    fun totalViolations(): Int {
        pruneExpired(System.currentTimeMillis())
        return violations.sumOf { it.weight }
    }

    fun shouldKick(threshold: Int): Boolean = totalViolations() >= threshold

    private fun pruneExpired(now: Long) {
        val cutoff = now - windowMs
        while (true) {
            val head = violations.peekFirst() ?: break
            if (head.timestamp < cutoff) violations.pollFirst() else break
        }
    }
}
