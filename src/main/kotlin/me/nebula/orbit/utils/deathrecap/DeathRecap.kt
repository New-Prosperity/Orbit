package me.nebula.orbit.utils.deathrecap

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.vanish.VanishManager
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.item.Material
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DamageEntry(
    val attackerUuid: UUID?,
    val attackerName: String,
    val amount: Float,
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
    val weapon: Material? = null,
    val distance: Double? = null,
)

class DeathRecapTracker {

    private val damageHistory = ConcurrentHashMap<UUID, MutableList<DamageEntry>>()
    private val lastRecaps = ConcurrentHashMap<UUID, List<Component>>()
    private val maxEntries = 10
    private val windowMillis = 30_000L
    var gameStartTime: Long = System.currentTimeMillis()

    fun recordDamage(victim: UUID, entry: DamageEntry) {
        val list = damageHistory.computeIfAbsent(victim) { mutableListOf() }
        list.add(entry)
        if (list.size > maxEntries) list.removeFirst()
    }

    fun buildRecap(victim: Player): DeathRecap? {
        val list = damageHistory[victim.uuid] ?: return null
        val cutoff = System.currentTimeMillis() - windowMillis
        val recent = list.filter { it.timestamp >= cutoff }
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

        val survivalTimeMs = if (gameStartTime > 0L) System.currentTimeMillis() - gameStartTime else null

        return DeathRecap(
            entries = recent,
            totalDamage = totalDamage,
            killerUuid = killerUuid,
            killerName = killerEntry?.attackerName,
            killerHealth = killerHealth,
            killerWeapon = killerEntry?.weapon,
            killerDistance = killerEntry?.distance,
            topDamagerUuid = topDamagerUuid,
            topDamagerName = topDamagerName,
            topDamagerAmount = topDamagerAmount,
            assists = assists,
            survivalTimeMs = survivalTimeMs,
        )
    }

    fun sendRecap(victim: Player) {
        val recap = buildRecap(victim) ?: return
        val lines = mutableListOf<Component>()

        lines += Component.empty()
        lines += victim.translate("orbit.deathrecap.header")

        if (recap.killerName != null) {
            val killerVisible = recap.killerUuid?.let { !VanishManager.isVanished(it) } ?: true
            val weaponStr = recap.killerWeapon?.let { formatMaterialName(it) }
            val distStr = recap.killerDistance?.let { "%.1f".format(it) }
            if (weaponStr != null || distStr != null) {
                lines += victim.translate("orbit.deathrecap.killer_with_weapon",
                    "killer" to if (killerVisible) recap.killerName else "?",
                    "weapon" to (weaponStr ?: "?"),
                    "distance" to (distStr ?: "?"),
                    "health" to if (killerVisible) formatHealth(recap.killerHealth) else "?",
                )
            } else {
                lines += victim.translate("orbit.deathrecap.killer",
                    "killer" to if (killerVisible) recap.killerName else "?",
                    "health" to if (killerVisible) formatHealth(recap.killerHealth) else "?",
                )
            }
        }

        if (recap.survivalTimeMs != null) {
            lines += victim.translate("orbit.deathrecap.survived",
                "time" to formatDuration(recap.survivalTimeMs),
            )
        }

        lines += victim.translate("orbit.deathrecap.total_damage",
            "damage" to "%.1f".format(recap.totalDamage),
        )

        for (entry in recap.entries.takeLast(5)) {
            val attackerVisible = entry.attackerUuid?.let { !VanishManager.isVanished(it) } ?: true
            val weaponStr = entry.weapon?.let { formatMaterialName(it) }
            val distStr = entry.distance?.let { "%.1f".format(it) }
            if (entry.attackerUuid != null && (weaponStr != null || distStr != null)) {
                lines += victim.translate("orbit.deathrecap.entry_detailed",
                    "attacker" to if (attackerVisible) entry.attackerName else "?",
                    "damage" to "%.1f".format(entry.amount),
                    "weapon" to (weaponStr ?: "?"),
                    "distance" to (distStr ?: "?"),
                )
            } else {
                lines += victim.translate("orbit.deathrecap.entry_environment",
                    "source" to if (attackerVisible) entry.attackerName else "?",
                    "damage" to "%.1f".format(entry.amount),
                )
            }
        }

        if (recap.assists.isNotEmpty()) {
            val visibleAssists = recap.assists.filter { !VanishManager.isVanished(it.uuid) }
            if (visibleAssists.isNotEmpty()) {
                val assistNames = visibleAssists.joinToString(", ") { "${it.name} (${" %.1f".format(it.damage).trim()})" }
                lines += victim.translate("orbit.deathrecap.assists",
                    "assists" to assistNames,
                )
            }
        }

        lines += Component.empty()

        lines.forEach(victim::sendMessage)
        lastRecaps[victim.uuid] = lines
    }

    fun getLastRecap(uuid: UUID): List<Component>? = lastRecaps[uuid]

    fun clearPlayer(uuid: UUID) {
        damageHistory.remove(uuid)
        lastRecaps.remove(uuid)
    }

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            clearPlayer(event.player.uuid)
        }
    }

    fun clear() {
        damageHistory.clear()
        lastRecaps.clear()
    }

    private fun formatHealth(health: Float?): String =
        if (health != null) "%.1f".format(health / 2) + "\u2764" else "?"

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "%d:%02d".format(minutes, seconds) else "${seconds}s"
    }

    private fun formatMaterialName(material: Material): String? {
        if (material == Material.AIR) return null
        return material.key().value()
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
    }
}

data class DeathRecap(
    val entries: List<DamageEntry>,
    val totalDamage: Float,
    val killerUuid: UUID?,
    val killerName: String?,
    val killerHealth: Float?,
    val killerWeapon: Material?,
    val killerDistance: Double?,
    val topDamagerUuid: UUID?,
    val topDamagerName: String,
    val topDamagerAmount: Float,
    val assists: List<AssistInfo>,
    val survivalTimeMs: Long?,
)

data class AssistInfo(
    val uuid: UUID,
    val name: String,
    val damage: Float,
)
