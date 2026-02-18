package me.nebula.orbit.utils.combatlog

import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private data class CombatEntry(val taggedAt: Long, val taggedBy: UUID?)

object CombatTracker {

    private val combatants = ConcurrentHashMap<UUID, CombatEntry>()
    private var combatDurationMs = 15_000L

    fun setCombatDuration(ms: Long) {
        combatDurationMs = ms
    }

    fun tag(player: Player, attacker: Player? = null) {
        combatants[player.uuid] = CombatEntry(System.currentTimeMillis(), attacker?.uuid)
    }

    fun isInCombat(player: Player): Boolean {
        val entry = combatants[player.uuid] ?: return false
        return System.currentTimeMillis() - entry.taggedAt < combatDurationMs
    }

    fun remainingMs(player: Player): Long {
        val entry = combatants[player.uuid] ?: return 0
        val remaining = combatDurationMs - (System.currentTimeMillis() - entry.taggedAt)
        return remaining.coerceAtLeast(0)
    }

    fun lastAttacker(player: Player): UUID? = combatants[player.uuid]?.taggedBy

    fun clear(player: Player) {
        combatants.remove(player.uuid)
    }

    fun clearExpired() {
        val now = System.currentTimeMillis()
        combatants.entries.removeIf { now - it.value.taggedAt >= combatDurationMs }
    }
}

val Player.isInCombat: Boolean get() = CombatTracker.isInCombat(this)

fun Player.tagCombat(attacker: Player? = null) = CombatTracker.tag(this, attacker)

fun Player.clearCombat() = CombatTracker.clear(this)
