package me.nebula.orbit.mode.game.battleroyale.downed

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.mode.game.battleroyale.BattleRoyaleTeamConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DownedRecord(
    val uuid: UUID,
    val attackerUuid: UUID?,
    val knockedAtMs: Long,
    val bleedoutHp: Int,
    val reviverUuid: UUID? = null,
    val reviveTicks: Int = 0,
)

enum class FinalReason { BLEEDOUT, EXECUTED, TIMEOUT, MANUAL }

enum class KnockRejection { ALREADY_DOWNED, DISABLED }

class DownedPlayerController(
    private val config: BattleRoyaleTeamConfig,
    private val onFinalEliminate: (UUID, FinalReason) -> Unit,
    private val onRevived: (reviver: UUID, revived: UUID) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val logger = logger("DownedPlayerController")
    private val records = ConcurrentHashMap<UUID, DownedRecord>()

    fun isDowned(uuid: UUID): Boolean = records.containsKey(uuid)

    fun record(uuid: UUID): DownedRecord? = records[uuid]

    fun activeCount(): Int = records.size

    fun activeUuids(): Set<UUID> = records.keys.toSet()

    fun reviverOf(uuid: UUID): UUID? = records[uuid]?.reviverUuid

    fun knock(uuid: UUID, attackerUuid: UUID?): Result<DownedRecord> {
        if (!config.reviveEnabled) return Result.failure(IllegalStateException(KnockRejection.DISABLED.name))
        if (records.containsKey(uuid)) return Result.failure(IllegalStateException(KnockRejection.ALREADY_DOWNED.name))
        val rec = DownedRecord(
            uuid = uuid,
            attackerUuid = attackerUuid,
            knockedAtMs = clock(),
            bleedoutHp = config.bleedoutHp,
        )
        records[uuid] = rec
        return Result.success(rec)
    }

    fun absorbDamage(uuid: UUID, amount: Int): Boolean {
        val current = records[uuid] ?: return false
        val remaining = current.bleedoutHp - amount
        if (remaining <= 0) {
            records.remove(uuid)
            onFinalEliminate(uuid, FinalReason.EXECUTED)
            return true
        }
        records[uuid] = current.copy(bleedoutHp = remaining)
        return false
    }

    fun startRevive(reviverUuid: UUID, downedUuid: UUID): Boolean {
        val current = records[downedUuid] ?: return false
        if (reviverUuid == downedUuid) return false
        if (records.containsKey(reviverUuid)) return false
        val existing = current.reviverUuid
        if (existing != null && existing != reviverUuid) return false
        records[downedUuid] = current.copy(reviverUuid = reviverUuid, reviveTicks = current.reviveTicks)
        return true
    }

    fun cancelRevive(reviverUuid: UUID, downedUuid: UUID): Boolean {
        val current = records[downedUuid] ?: return false
        if (current.reviverUuid != reviverUuid) return false
        records[downedUuid] = current.copy(reviverUuid = null, reviveTicks = 0)
        return true
    }

    fun tick() {
        if (records.isEmpty()) return
        val now = clock()
        for ((uuid, rec) in records.toMap()) {
            if (rec.reviverUuid != null) {
                val nextTicks = rec.reviveTicks + 1
                val target = config.reviveTimeSeconds * 20
                if (nextTicks >= target) {
                    records.remove(uuid)
                    onRevived(rec.reviverUuid, uuid)
                } else {
                    records[uuid] = rec.copy(reviveTicks = nextTicks)
                }
                continue
            }
            val elapsedMs = now - rec.knockedAtMs
            if (elapsedMs >= config.bleedoutSeconds * 1000L) {
                records.remove(uuid)
                onFinalEliminate(uuid, FinalReason.BLEEDOUT)
            }
        }
    }

    fun rise(uuid: UUID): DownedRecord? = records.remove(uuid)

    fun forceFinal(uuid: UUID, reason: FinalReason) {
        if (records.remove(uuid) == null) return
        onFinalEliminate(uuid, reason)
    }

    fun clearAll() { records.clear() }

    fun reviveProgress(uuid: UUID): Double {
        val rec = records[uuid] ?: return 0.0
        val target = (config.reviveTimeSeconds * 20).coerceAtLeast(1)
        return (rec.reviveTicks.toDouble() / target).coerceIn(0.0, 1.0)
    }

    fun bleedoutRemainingSeconds(uuid: UUID, nowMs: Long = clock()): Int {
        val rec = records[uuid] ?: return 0
        val elapsedSec = ((nowMs - rec.knockedAtMs) / 1000L).toInt()
        return (config.bleedoutSeconds - elapsedSec).coerceAtLeast(0)
    }
}
