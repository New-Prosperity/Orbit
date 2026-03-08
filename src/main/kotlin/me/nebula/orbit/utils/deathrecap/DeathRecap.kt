package me.nebula.orbit.utils.deathrecap

import me.nebula.orbit.translation.translate
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DamageEntry(
    val attackerUuid: UUID?,
    val attackerName: String,
    val amount: Float,
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
)

class DeathRecapTracker {

    private val damageHistory = ConcurrentHashMap<UUID, MutableList<DamageEntry>>()
    private val maxEntries = 10
    private val windowMillis = 30_000L

    fun recordDamage(victim: UUID, entry: DamageEntry) {
        val list = damageHistory.computeIfAbsent(victim) { mutableListOf() }
        synchronized(list) {
            list.add(entry)
            if (list.size > maxEntries) list.removeFirst()
        }
    }

    fun buildRecap(victim: Player): DeathRecap? {
        val list = damageHistory[victim.uuid] ?: return null
        val cutoff = System.currentTimeMillis() - windowMillis
        val recent: List<DamageEntry>
        synchronized(list) {
            recent = list.filter { it.timestamp >= cutoff }.toList()
        }
        if (recent.isEmpty()) return null

        val totalDamage = recent.sumOf { it.amount.toDouble() }.toFloat()
        val topDamager = recent.groupBy { it.attackerUuid }
            .maxByOrNull { (_, entries) -> entries.sumOf { it.amount.toDouble() } }
        val topDamagerUuid = topDamager?.key
        val topDamagerName = topDamager?.value?.firstOrNull()?.attackerName ?: "?"
        val topDamagerAmount = topDamager?.value?.sumOf { it.amount.toDouble() }?.toFloat() ?: 0f

        val killerEntry = recent.lastOrNull { it.attackerUuid != null }
        val killerUuid = killerEntry?.attackerUuid
        val killerPlayer = killerUuid?.let { MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it) }
        val killerHealth = killerPlayer?.health

        val assists = recent.mapNotNull { it.attackerUuid }
            .filter { it != killerUuid && it != victim.uuid }
            .distinct()
            .mapNotNull { uuid ->
                val name = recent.firstOrNull { it.attackerUuid == uuid }?.attackerName ?: return@mapNotNull null
                val damage = recent.filter { it.attackerUuid == uuid }.sumOf { it.amount.toDouble() }.toFloat()
                AssistInfo(uuid, name, damage)
            }

        return DeathRecap(
            entries = recent,
            totalDamage = totalDamage,
            killerUuid = killerUuid,
            killerName = killerEntry?.attackerName,
            killerHealth = killerHealth,
            topDamagerUuid = topDamagerUuid,
            topDamagerName = topDamagerName,
            topDamagerAmount = topDamagerAmount,
            assists = assists,
        )
    }

    fun sendRecap(victim: Player) {
        val recap = buildRecap(victim) ?: return

        victim.sendMessage(Component.empty())
        victim.sendMessage(victim.translate("orbit.deathrecap.header"))

        if (recap.killerName != null) {
            victim.sendMessage(victim.translate("orbit.deathrecap.killer",
                "killer" to recap.killerName,
                "health" to formatHealth(recap.killerHealth),
            ))
        }

        victim.sendMessage(victim.translate("orbit.deathrecap.total_damage",
            "damage" to "%.1f".format(recap.totalDamage),
        ))

        for (entry in recap.entries.takeLast(5)) {
            victim.sendMessage(victim.translate("orbit.deathrecap.entry",
                "attacker" to entry.attackerName,
                "damage" to "%.1f".format(entry.amount),
                "source" to entry.source,
            ))
        }

        if (recap.assists.isNotEmpty()) {
            val assistNames = recap.assists.joinToString(", ") { "${it.name} (${" %.1f".format(it.damage).trim()})" }
            victim.sendMessage(victim.translate("orbit.deathrecap.assists",
                "assists" to assistNames,
            ))
        }

        victim.sendMessage(Component.empty())
    }

    fun clearPlayer(uuid: UUID) {
        damageHistory.remove(uuid)
    }

    fun clear() {
        damageHistory.clear()
    }

    private fun formatHealth(health: Float?): String =
        if (health != null) "%.1f".format(health / 2) + "\u2764" else "?"
}

data class DeathRecap(
    val entries: List<DamageEntry>,
    val totalDamage: Float,
    val killerUuid: UUID?,
    val killerName: String?,
    val killerHealth: Float?,
    val topDamagerUuid: UUID?,
    val topDamagerName: String,
    val topDamagerAmount: Float,
    val assists: List<AssistInfo>,
)

data class AssistInfo(
    val uuid: UUID,
    val name: String,
    val damage: Float,
)
