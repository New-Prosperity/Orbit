package me.nebula.orbit.mode.game.battleroyale.zone

sealed interface ZoneState {

    val centerX: Double
    val centerZ: Double
    val diameter: Double
    val damagePerSecond: Float get() = 0f

    data class Waiting(
        override val centerX: Double,
        override val centerZ: Double,
        override val diameter: Double,
    ) : ZoneState

    data class Announcing(
        val phaseIndex: Int,
        override val centerX: Double,
        override val centerZ: Double,
        override val diameter: Double,
        val nextCenterX: Double,
        val nextCenterZ: Double,
        val nextDiameter: Double,
        val shrinkStartsAtMs: Long,
        val shrinkDurationSeconds: Double,
        override val damagePerSecond: Float,
    ) : ZoneState

    data class Shrinking(
        val phaseIndex: Int,
        val fromCenterX: Double,
        val fromCenterZ: Double,
        val fromDiameter: Double,
        val toCenterX: Double,
        val toCenterZ: Double,
        val toDiameter: Double,
        val startedAtMs: Long,
        val durationSeconds: Double,
        override val damagePerSecond: Float,
    ) : ZoneState {
        override val centerX: Double get() = currentCenterX(System.currentTimeMillis())
        override val centerZ: Double get() = currentCenterZ(System.currentTimeMillis())
        override val diameter: Double get() = currentDiameter(System.currentTimeMillis())

        fun progress(nowMs: Long): Double {
            if (durationSeconds <= 0.0) return 1.0
            val elapsed = (nowMs - startedAtMs).coerceAtLeast(0L).toDouble() / 1000.0
            return (elapsed / durationSeconds).coerceIn(0.0, 1.0)
        }

        fun currentCenterX(nowMs: Long): Double = lerp(fromCenterX, toCenterX, progress(nowMs))
        fun currentCenterZ(nowMs: Long): Double = lerp(fromCenterZ, toCenterZ, progress(nowMs))
        fun currentDiameter(nowMs: Long): Double = lerp(fromDiameter, toDiameter, progress(nowMs))

        fun remainingSeconds(nowMs: Long): Double =
            (durationSeconds - (nowMs - startedAtMs).coerceAtLeast(0L) / 1000.0).coerceAtLeast(0.0)

        fun isComplete(nowMs: Long): Boolean = progress(nowMs) >= 1.0
    }

    data class Static(
        val phaseIndex: Int,
        override val centerX: Double,
        override val centerZ: Double,
        override val diameter: Double,
        override val damagePerSecond: Float,
    ) : ZoneState

    data class Deathmatch(
        override val centerX: Double,
        override val centerZ: Double,
        override val diameter: Double,
        override val damagePerSecond: Float,
    ) : ZoneState

    data class Ended(
        override val centerX: Double,
        override val centerZ: Double,
        override val diameter: Double,
    ) : ZoneState
}

private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
