package me.nebula.orbit.mode.game.battleroyale.spawn

import me.nebula.ether.utils.logging.logger
import net.minestom.server.coordinate.Pos
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SpawnImmunityController(
    private val defaultTimeoutMs: Long = 10_000L,
    private val movementThresholdBlocks: Double = 3.0,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val logger = logger("SpawnImmunityController")
    private val immunities = ConcurrentHashMap<UUID, Immunity>()

    data class Immunity(
        val grantedAtMs: Long,
        val expiresAtMs: Long,
        val origin: Pos,
    )

    enum class BreakReason { MOVEMENT, TIMEOUT, DAMAGE_TAKEN, MANUAL }

    fun grant(uuid: UUID, origin: Pos, timeoutMs: Long = defaultTimeoutMs): Immunity {
        val now = clock()
        val record = Immunity(now, now + timeoutMs, origin)
        immunities[uuid] = record
        return record
    }

    fun grantAll(players: Map<UUID, Pos>, timeoutMs: Long = defaultTimeoutMs) {
        for ((uuid, pos) in players) grant(uuid, pos, timeoutMs)
    }

    fun isImmune(uuid: UUID): Boolean {
        val record = immunities[uuid] ?: return false
        if (clock() >= record.expiresAtMs) {
            release(uuid, BreakReason.TIMEOUT)
            return false
        }
        return true
    }

    fun checkMovement(uuid: UUID, position: Pos) {
        val record = immunities[uuid] ?: return
        val horizontalDistance = Math.hypot(position.x() - record.origin.x(), position.z() - record.origin.z())
        if (horizontalDistance >= movementThresholdBlocks) {
            release(uuid, BreakReason.MOVEMENT)
        }
    }

    fun onDamageTaken(uuid: UUID): Boolean {
        if (immunities[uuid] == null) return false
        release(uuid, BreakReason.DAMAGE_TAKEN)
        return true
    }

    fun release(uuid: UUID, reason: BreakReason) {
        val record = immunities.remove(uuid) ?: return
        logger.debug { "Immunity released for $uuid after ${clock() - record.grantedAtMs}ms (reason=$reason)" }
    }

    fun releaseAll(reason: BreakReason = BreakReason.MANUAL) {
        val keys = immunities.keys.toList()
        for (key in keys) release(key, reason)
    }

    fun size(): Int = immunities.size

    fun activeUuids(): Set<UUID> = immunities.keys.toSet()
}
