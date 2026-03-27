package me.nebula.orbit.mode.game

import me.nebula.orbit.utils.vanish.VanishManager
import net.minestom.server.MinecraftServer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

sealed interface PlayerState {
    data object Alive : PlayerState
    data object Spectating : PlayerState
    data object Respawning : PlayerState
    data class Disconnected(val since: Long = System.currentTimeMillis(), val wasRespawning: Boolean = false) : PlayerState
}

class PlayerTracker {

    private val players = ConcurrentHashMap<UUID, PlayerState>()
    private val teams = ConcurrentHashMap<UUID, String>()
    private val lives = ConcurrentHashMap<UUID, Int>()
    private val kills = ConcurrentHashMap<UUID, Int>()
    private val deaths = ConcurrentHashMap<UUID, Int>()
    private val streaks = ConcurrentHashMap<UUID, Int>()
    private val assists = ConcurrentHashMap<UUID, Int>()
    private val scores = ConcurrentHashMap<UUID, Double>()
    private val recentDamagers = ConcurrentHashMap<UUID, MutableList<DamageRecord>>()
    private val lastCombatTime = ConcurrentHashMap<UUID, Long>()
    private val lastActivityTime = ConcurrentHashMap<UUID, Long>()
    private val eliminationOrder = ConcurrentHashMap<UUID, Int>()
    private val eliminationCounter = AtomicInteger(0)

    val alive: Set<UUID> get() = players.entries.filter { it.value is PlayerState.Alive }.mapTo(HashSet()) { it.key }
    val spectating: Set<UUID> get() = players.entries.filter { it.value is PlayerState.Spectating }.mapTo(HashSet()) { it.key }
    val disconnected: Set<UUID> get() = players.entries.filter { it.value is PlayerState.Disconnected }.mapTo(HashSet()) { it.key }
    val respawning: Set<UUID> get() = players.entries.filter { it.value is PlayerState.Respawning }.mapTo(HashSet()) { it.key }
    val all: Set<UUID> get() = players.keys.toSet()

    val aliveCount: Int get() = players.values.count { it is PlayerState.Alive }
    val effectiveAliveCount: Int get() = players.values.count { it !is PlayerState.Spectating }

    fun visibleAliveCount(): Int = alive.count { uuid ->
        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: return@count true
        !VanishManager.isVanished(player)
    }
    val size: Int get() = players.size

    fun join(uuid: UUID) {
        players[uuid] = PlayerState.Alive
        lastActivityTime[uuid] = System.currentTimeMillis()
    }

    fun eliminate(uuid: UUID) {
        players[uuid] = PlayerState.Spectating
        eliminationOrder.putIfAbsent(uuid, eliminationCounter.incrementAndGet())
    }

    fun revive(uuid: UUID) {
        val current = players[uuid] ?: return
        when (current) {
            is PlayerState.Spectating, is PlayerState.Respawning -> players[uuid] = PlayerState.Alive
            else -> {}
        }
    }

    fun markRespawning(uuid: UUID) {
        players[uuid] = PlayerState.Respawning
    }

    fun disconnect(uuid: UUID) {
        val current = players[uuid] ?: return
        when (current) {
            is PlayerState.Alive -> players[uuid] = PlayerState.Disconnected()
            is PlayerState.Respawning -> players[uuid] = PlayerState.Disconnected(wasRespawning = true)
            else -> {}
        }
    }

    fun reconnect(uuid: UUID) {
        val current = players[uuid] ?: return
        if (current is PlayerState.Disconnected) {
            players[uuid] = PlayerState.Alive
            lastActivityTime[uuid] = System.currentTimeMillis()
        }
    }

    fun remove(uuid: UUID) {
        players.remove(uuid)
        teams.remove(uuid)
        lives.remove(uuid)
        kills.remove(uuid)
        deaths.remove(uuid)
        streaks.remove(uuid)
        assists.remove(uuid)
        scores.remove(uuid)
        eliminationOrder.remove(uuid)
        lastCombatTime.remove(uuid)
        lastActivityTime.remove(uuid)
        recentDamagers.remove(uuid)
    }

    fun stateOf(uuid: UUID): PlayerState? = players[uuid]

    fun isAlive(uuid: UUID): Boolean = players[uuid] is PlayerState.Alive
    fun isSpectating(uuid: UUID): Boolean = players[uuid] is PlayerState.Spectating
    fun isDisconnected(uuid: UUID): Boolean = players[uuid] is PlayerState.Disconnected
    fun isRespawning(uuid: UUID): Boolean = players[uuid] is PlayerState.Respawning
    fun isActive(uuid: UUID): Boolean = stateOf(uuid).let { it != null && it !is PlayerState.Spectating }

    operator fun contains(uuid: UUID): Boolean = players.containsKey(uuid)

    fun assignTeam(uuid: UUID, team: String) { teams[uuid] = team }
    fun teamOf(uuid: UUID): String? = teams[uuid]

    fun teamMembers(team: String): Set<UUID> =
        teams.entries.filter { it.value == team }.mapTo(HashSet()) { it.key }

    fun aliveInTeam(team: String): Set<UUID> =
        teamMembers(team).filterTo(HashSet()) { isAlive(it) }

    fun activeInTeam(team: String): Set<UUID> =
        teamMembers(team).filterTo(HashSet()) { isActive(it) }

    fun isTeamEliminated(team: String): Boolean =
        teamMembers(team).none { isActive(it) }

    fun aliveTeams(): Set<String> =
        teams.entries.filter { isActive(it.key) }.mapTo(HashSet()) { it.value }

    fun allTeams(): Set<String> = teams.values.toSet()

    fun teamSizes(): Map<String, Int> =
        teams.values.groupingBy { it }.eachCount()

    fun areTeammates(a: UUID, b: UUID): Boolean {
        val teamA = teams[a] ?: return false
        return teamA == teams[b]
    }

    fun setLives(uuid: UUID, count: Int) { lives[uuid] = count }
    fun livesOf(uuid: UUID): Int = lives[uuid] ?: 0

    fun decrementLives(uuid: UUID): Int {
        val remaining = (lives[uuid] ?: 1) - 1
        lives[uuid] = remaining.coerceAtLeast(0)
        return remaining
    }

    fun hasLivesRemaining(uuid: UUID): Boolean = (lives[uuid] ?: 0) > 0

    fun recordKill(killer: UUID) {
        kills.merge(killer, 1, Int::plus)
        streaks.merge(killer, 1, Int::plus)
    }

    fun recordDeath(victim: UUID) {
        deaths.merge(victim, 1, Int::plus)
        streaks[victim] = 0
    }

    fun killsOf(uuid: UUID): Int = kills[uuid] ?: 0
    fun deathsOf(uuid: UUID): Int = deaths[uuid] ?: 0

    fun streakOf(uuid: UUID): Int = streaks[uuid] ?: 0

    fun recordAssist(uuid: UUID) { assists.merge(uuid, 1, Int::plus) }
    fun assistsOf(uuid: UUID): Int = assists[uuid] ?: 0

    fun addScore(uuid: UUID, amount: Double) { scores.merge(uuid, amount, Double::plus) }
    fun scoreOf(uuid: UUID): Double = scores[uuid] ?: 0.0

    fun teamScoreOf(team: String): Double =
        teamMembers(team).sumOf { scoreOf(it) }

    fun scoreboard(): List<Pair<UUID, Double>> =
        scores.entries.sortedByDescending { it.value }.map { it.key to it.value }

    fun teamScoreboard(): List<Pair<String, Double>> =
        allTeams().map { it to teamScoreOf(it) }.sortedByDescending { it.second }

    fun recordDamage(attacker: UUID, victim: UUID) {
        val now = System.currentTimeMillis()
        lastCombatTime[attacker] = now
        lastCombatTime[victim] = now
        val records = recentDamagers.computeIfAbsent(victim) { mutableListOf() }
        synchronized(records) {
            records.removeAll { it.attacker == attacker }
            records.add(DamageRecord(attacker, now))
        }
    }

    fun recentDamagersOf(victim: UUID, windowMillis: Long): List<UUID> {
        val records = recentDamagers[victim] ?: return emptyList()
        val cutoff = System.currentTimeMillis() - windowMillis
        synchronized(records) {
            return records.filter { it.timestamp >= cutoff }.map { it.attacker }
        }
    }

    fun lastCombatTimeOf(uuid: UUID): Long = lastCombatTime[uuid] ?: 0L

    fun isInCombat(uuid: UUID, windowMillis: Long): Boolean {
        val last = lastCombatTime[uuid] ?: return false
        return System.currentTimeMillis() - last <= windowMillis
    }

    fun markActivity(uuid: UUID) {
        lastActivityTime[uuid] = System.currentTimeMillis()
    }

    fun lastActivityOf(uuid: UUID): Long = lastActivityTime[uuid] ?: 0L

    fun isAfk(uuid: UUID, thresholdMillis: Long): Boolean {
        val last = lastActivityTime[uuid] ?: return false
        return System.currentTimeMillis() - last >= thresholdMillis
    }

    fun eliminationOrderOf(uuid: UUID): Int = eliminationOrder[uuid] ?: 0

    fun placementOf(uuid: UUID, totalPlayers: Int): Int {
        val elimOrder = eliminationOrder[uuid] ?: return 1
        return totalPlayers - elimOrder + 1
    }

    fun eliminationOrderedList(): List<UUID> =
        eliminationOrder.entries.sortedBy { it.value }.map { it.key }

    fun clear() {
        players.clear()
        teams.clear()
        lives.clear()
        kills.clear()
        deaths.clear()
        streaks.clear()
        assists.clear()
        scores.clear()
        recentDamagers.clear()
        lastCombatTime.clear()
        lastActivityTime.clear()
        eliminationOrder.clear()
        eliminationCounter.set(0)
    }
}

data class DamageRecord(val attacker: UUID, val timestamp: Long)
