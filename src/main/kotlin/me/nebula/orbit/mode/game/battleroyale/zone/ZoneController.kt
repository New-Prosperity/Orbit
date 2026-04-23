package me.nebula.orbit.mode.game.battleroyale.zone

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.event.GameEvent
import me.nebula.orbit.event.GameEventBus
import me.nebula.orbit.mode.game.battleroyale.script.BorderController
import me.nebula.orbit.mode.game.battleroyale.script.DeathmatchController
import net.minestom.server.coordinate.Pos
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

class ZoneController(
    private val eventBus: GameEventBus,
    private val zoneShrinkingEnabled: () -> Boolean,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pushIntervalTicks: Int = 10,
) : BorderController, DeathmatchController {

    private val logger = logger("ZoneController")
    private val stateRef = AtomicReference<ZoneState>(DEFAULT_WAITING)
    private var driver: ZoneBorderDriver? = null
    private var phaseCount = 0
    private var activeDamage = 0f
    private var initialDiameter = 0.0
    private var initialCenterX = 0.0
    private var initialCenterZ = 0.0
    private var lastPushMs = 0L

    val state: ZoneState get() = stateRef.get()
    val currentDamage: Float get() = state.damagePerSecond.takeIf { it > 0f } ?: activeDamage

    fun attach(
        driver: ZoneBorderDriver,
        initialDiameter: Double,
        centerX: Double,
        centerZ: Double,
        initialDamagePerSecond: Float,
    ) {
        this.initialDiameter = initialDiameter
        this.initialCenterX = centerX
        this.initialCenterZ = centerZ
        this.activeDamage = initialDamagePerSecond
        this.phaseCount = 0
        this.driver = driver
        driver.snapTo(centerX, centerZ, initialDiameter)
        transitionTo(ZoneState.Waiting(centerX, centerZ, initialDiameter))
    }

    override fun shrinkBorderTo(diameter: Double, durationSeconds: Double) {
        planShrink(diameter, durationSeconds, announceLeadSeconds = 0.0)
    }

    fun planShrink(targetDiameter: Double, durationSeconds: Double, announceLeadSeconds: Double) {
        if (!zoneShrinkingEnabled()) {
            logger.info { "ZONE_SHRINKING disabled — ignoring shrink to $targetDiameter over ${durationSeconds}s" }
            return
        }
        val d = driver ?: run {
            logger.warn { "planShrink called before driver attached" }
            return
        }
        val fromCenterX = d.currentCenterX
        val fromCenterZ = d.currentCenterZ
        val fromDiameter = d.currentDiameter
        val (toCenterX, toCenterZ) = pickInsideZone(fromCenterX, fromCenterZ, fromDiameter, targetDiameter)

        phaseCount += 1
        val now = clock()

        if (announceLeadSeconds > 0.0) {
            transitionTo(ZoneState.Announcing(
                phaseIndex = phaseCount,
                centerX = fromCenterX, centerZ = fromCenterZ, diameter = fromDiameter,
                nextCenterX = toCenterX, nextCenterZ = toCenterZ, nextDiameter = targetDiameter,
                shrinkStartsAtMs = now + (announceLeadSeconds * 1000).toLong(),
                shrinkDurationSeconds = durationSeconds,
                damagePerSecond = activeDamage,
            ))
            return
        }

        beginShrink(fromCenterX, fromCenterZ, fromDiameter, toCenterX, toCenterZ, targetDiameter, durationSeconds)
    }

    private fun beginShrink(
        fromCx: Double, fromCz: Double, fromD: Double,
        toCx: Double, toCz: Double, toD: Double,
        durationSeconds: Double,
    ) {
        val now = clock()
        lastPushMs = now
        transitionTo(ZoneState.Shrinking(
            phaseIndex = phaseCount.coerceAtLeast(1),
            fromCenterX = fromCx, fromCenterZ = fromCz, fromDiameter = fromD,
            toCenterX = toCx, toCenterZ = toCz, toDiameter = toD,
            startedAtMs = now, durationSeconds = durationSeconds,
            damagePerSecond = activeDamage,
        ))
    }

    override fun setBorderDamage(damagePerSecond: Double) {
        activeDamage = damagePerSecond.toFloat()
        when (val current = state) {
            is ZoneState.Shrinking -> transitionTo(current.copy(damagePerSecond = activeDamage))
            is ZoneState.Static -> transitionTo(current.copy(damagePerSecond = activeDamage))
            is ZoneState.Deathmatch -> transitionTo(current.copy(damagePerSecond = activeDamage))
            is ZoneState.Announcing -> transitionTo(current.copy(damagePerSecond = activeDamage))
            is ZoneState.Waiting, is ZoneState.Ended -> Unit
        }
    }

    override fun startDeathmatch() {
        val d = driver ?: return
        val diameter = d.currentDiameter
        val cx = d.currentCenterX
        val cz = d.currentCenterZ
        transitionTo(ZoneState.Deathmatch(cx, cz, diameter, activeDamage))
    }

    fun shrinkForDeathmatch(targetDiameter: Double, durationSeconds: Double) {
        val d = driver ?: return
        val fromCx = d.currentCenterX
        val fromCz = d.currentCenterZ
        val fromD = d.currentDiameter
        val (toCx, toCz) = pickInsideZone(fromCx, fromCz, fromD, targetDiameter)
        beginShrink(fromCx, fromCz, fromD, toCx, toCz, targetDiameter, durationSeconds)
        transitionTo(ZoneState.Deathmatch(toCx, toCz, targetDiameter, activeDamage))
    }

    fun tick(nowMs: Long) {
        when (val current = state) {
            is ZoneState.Announcing -> {
                if (nowMs >= current.shrinkStartsAtMs) {
                    beginShrink(
                        current.centerX, current.centerZ, current.diameter,
                        current.nextCenterX, current.nextCenterZ, current.nextDiameter,
                        current.shrinkDurationSeconds,
                    )
                }
            }
            is ZoneState.Shrinking -> {
                val d = driver ?: return
                val stepMs = pushIntervalTicks * 50L
                val needsPush = nowMs - lastPushMs >= stepMs
                if (needsPush || current.isComplete(nowMs)) {
                    lastPushMs = nowMs
                    val stepSeconds = (stepMs.toDouble() / 1000.0)
                    d.pushLerpStep(
                        current.currentCenterX(nowMs),
                        current.currentCenterZ(nowMs),
                        current.currentDiameter(nowMs),
                        stepSeconds,
                    )
                }
                if (current.isComplete(nowMs)) {
                    d.snapTo(current.toCenterX, current.toCenterZ, current.toDiameter)
                    transitionTo(ZoneState.Static(
                        current.phaseIndex, current.toCenterX, current.toCenterZ, current.toDiameter, current.damagePerSecond,
                    ))
                }
            }
            else -> Unit
        }
    }

    fun end() {
        val d = driver
        val cx = d?.currentCenterX ?: state.centerX
        val cz = d?.currentCenterZ ?: state.centerZ
        val diameter = d?.currentDiameter ?: state.diameter
        transitionTo(ZoneState.Ended(cx, cz, diameter))
    }

    fun reset() {
        driver?.snapTo(initialCenterX, initialCenterZ, initialDiameter)
        driver = null
        phaseCount = 0
        activeDamage = 0f
        transitionTo(DEFAULT_WAITING)
    }

    fun isOutside(pos: Pos): Boolean = driver?.isOutside(pos) ?: false

    internal fun pickInsideZone(
        fromCenterX: Double,
        fromCenterZ: Double,
        fromDiameter: Double,
        targetDiameter: Double,
    ): Pair<Double, Double> {
        if (targetDiameter >= fromDiameter) return fromCenterX to fromCenterZ
        val maxOffset = (fromDiameter - targetDiameter) / 2.0
        val dx = (random.nextDouble() * 2.0 - 1.0) * maxOffset
        val dz = (random.nextDouble() * 2.0 - 1.0) * maxOffset
        return (fromCenterX + dx) to (fromCenterZ + dz)
    }

    private fun transitionTo(next: ZoneState) {
        val previous = stateRef.getAndSet(next)
        if (previous == next) return
        eventBus.publish(GameEvent.ZoneTransition(previous, next))
    }

    companion object {
        private val DEFAULT_WAITING = ZoneState.Waiting(0.0, 0.0, 0.0)
    }
}
