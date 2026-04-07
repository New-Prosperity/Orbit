package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Point
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class MemoryEntry<T>(val value: T, val expiresAt: Long)

data class ThreatEntry(
    val uuid: UUID,
    var totalDamage: Float,
    var lastDamageTime: Long,
    var encounters: Int,
)

data class PlayerMemoryEntry(
    val uuid: UUID,
    val position: Point,
    val lastSeen: Long,
    val isThreat: Boolean,
)

private const val CLEANUP_INTERVAL = 20
private const val MAX_CATEGORIES = 32
private const val MAX_ENTRIES_PER_CATEGORY = 50
private const val MAX_PLAYER_SIGHTINGS = 30
private const val MAX_THREATS = 20

private fun <V> lruMap(maxSize: Int): LinkedHashMap<String, V> =
    object : LinkedHashMap<String, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>): Boolean = size > maxSize
    }

class BotMemory {

    private val knownLocations = lruMap<ArrayList<MemoryEntry<Point>>>(MAX_CATEGORIES)
    private val threats = HashMap<UUID, ThreatEntry>()
    private val resources = lruMap<ArrayList<MemoryEntry<Point>>>(MAX_CATEGORIES)
    private val lastSeenPlayers = HashMap<UUID, PlayerMemoryEntry>()
    private var cleanupCounter = 0

    fun rememberLocation(category: String, pos: Point, ttl: Duration = 5.minutes) {
        val entries = knownLocations.getOrPut(category) { ArrayList() }
        val expiresAt = System.currentTimeMillis() + ttl.inWholeMilliseconds
        entries.removeAll { it.value.sameBlock(pos) }
        if (entries.size >= MAX_ENTRIES_PER_CATEGORY) entries.removeFirst()
        entries.add(MemoryEntry(pos, expiresAt))
    }

    fun recallLocations(category: String): List<Point> {
        val now = System.currentTimeMillis()
        val entries = knownLocations[category] ?: return emptyList()
        return entries.filter { it.expiresAt >= now }.map { it.value }
    }

    fun nearestRecalled(category: String, from: Point): Point? =
        recallLocations(category).minByOrNull { it.distanceSquared(from) }

    fun forgetCategory(category: String) {
        knownLocations.remove(category)
    }

    fun rememberThreat(attacker: UUID, damage: Float) {
        val now = System.currentTimeMillis()
        val existing = threats[attacker]
        if (existing != null) {
            existing.totalDamage += damage
            existing.lastDamageTime = now
            existing.encounters++
        } else {
            if (threats.size >= MAX_THREATS) {
                val weakest = threats.entries.minByOrNull { getThreatLevel(it.key) }
                if (weakest != null) threats.remove(weakest.key)
            }
            threats[attacker] = ThreatEntry(attacker, damage, now, 1)
        }
    }

    fun getThreatLevel(uuid: UUID): Float {
        val entry = threats[uuid] ?: return 0f
        val elapsed = (System.currentTimeMillis() - entry.lastDamageTime) / 1000f
        val decayFactor = (1f - elapsed / 60f).coerceIn(0f, 1f)
        return entry.totalDamage * decayFactor
    }

    fun getHighestThreat(): UUID? =
        threats.keys.maxByOrNull { getThreatLevel(it) }
            ?.takeIf { getThreatLevel(it) > 0f }

    fun isKnownThreat(uuid: UUID): Boolean = getThreatLevel(uuid) > 0f

    fun forgetThreat(uuid: UUID) {
        threats.remove(uuid)
    }

    fun rememberResource(type: String, pos: Point) {
        val entries = resources.getOrPut(type) { ArrayList() }
        val expiresAt = System.currentTimeMillis() + 5.minutes.inWholeMilliseconds
        entries.removeAll { it.value.sameBlock(pos) }
        if (entries.size >= MAX_ENTRIES_PER_CATEGORY) entries.removeFirst()
        entries.add(MemoryEntry(pos, expiresAt))
    }

    fun nearestResource(type: String, from: Point): Point? {
        val now = System.currentTimeMillis()
        val entries = resources[type] ?: return null
        var nearest: Point? = null
        var nearestDist = Double.MAX_VALUE
        for (entry in entries) {
            if (entry.expiresAt < now) continue
            val dist = entry.value.distanceSquared(from)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = entry.value
            }
        }
        return nearest
    }

    fun forgetResource(type: String) {
        resources.remove(type)
    }

    fun forgetResourceAt(type: String, pos: Point) {
        val entries = resources[type] ?: return
        entries.removeAll { it.value.sameBlock(pos) }
    }

    fun updatePlayerSighting(uuid: UUID, pos: Point, isThreat: Boolean) {
        if (lastSeenPlayers.size >= MAX_PLAYER_SIGHTINGS && !lastSeenPlayers.containsKey(uuid)) {
            val oldest = lastSeenPlayers.entries.minByOrNull { it.value.lastSeen }
            if (oldest != null) lastSeenPlayers.remove(oldest.key)
        }
        lastSeenPlayers[uuid] = PlayerMemoryEntry(uuid, pos, System.currentTimeMillis(), isThreat)
    }

    fun lastSeen(uuid: UUID): PlayerMemoryEntry? = lastSeenPlayers[uuid]

    fun nearbyThreats(from: Point, radius: Double): List<PlayerMemoryEntry> {
        val radiusSq = radius * radius
        val now = System.currentTimeMillis()
        val staleThreshold = 30_000L
        return lastSeenPlayers.values.filter { entry ->
            entry.isThreat &&
                entry.position.distanceSquared(from) <= radiusSq &&
                now - entry.lastSeen < staleThreshold
        }
    }

    fun tick() {
        if (++cleanupCounter % CLEANUP_INTERVAL != 0) return
        val now = System.currentTimeMillis()
        knownLocations.values.forEach { it.removeAll { e -> e.expiresAt < now } }
        knownLocations.entries.removeIf { it.value.isEmpty() }

        threats.entries.removeIf { getThreatLevel(it.key) <= 0f }

        resources.values.forEach { it.removeAll { e -> e.expiresAt < now } }
        resources.entries.removeIf { it.value.isEmpty() }

        val staleThreshold = 60_000L
        lastSeenPlayers.entries.removeIf { now - it.value.lastSeen > staleThreshold }
    }

    fun clear() {
        knownLocations.clear()
        threats.clear()
        resources.clear()
        lastSeenPlayers.clear()
    }
}

private fun Point.sameBlock(other: Point): Boolean =
    blockX() == other.blockX() && blockY() == other.blockY() && blockZ() == other.blockZ()
