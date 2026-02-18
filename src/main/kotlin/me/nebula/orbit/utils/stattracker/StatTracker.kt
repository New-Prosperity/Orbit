package me.nebula.orbit.utils.stattracker

import me.nebula.orbit.translation.translateDefault
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StatTracker {

    private val stats = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    private val derivedStats = ConcurrentHashMap<String, DerivedStat>()

    fun increment(player: Player, stat: String, amount: Long = 1) =
        increment(player.uuid, stat, amount)

    fun increment(uuid: UUID, stat: String, amount: Long = 1) {
        playerStats(uuid).compute(stat) { _, current -> (current ?: 0) + amount }
    }

    fun decrement(player: Player, stat: String, amount: Long = 1) =
        decrement(player.uuid, stat, amount)

    fun decrement(uuid: UUID, stat: String, amount: Long = 1) {
        playerStats(uuid).compute(stat) { _, current -> ((current ?: 0) - amount).coerceAtLeast(0) }
    }

    fun set(player: Player, stat: String, value: Long) = set(player.uuid, stat, value)

    fun set(uuid: UUID, stat: String, value: Long) {
        playerStats(uuid)[stat] = value
    }

    fun get(player: Player, stat: String): Long = get(player.uuid, stat)

    fun get(uuid: UUID, stat: String): Long {
        derivedStats[stat]?.let { derived ->
            val playerMap = stats[uuid] ?: return 0
            return derived.compute(playerMap).toLong()
        }
        return stats[uuid]?.get(stat) ?: 0
    }

    fun getDouble(uuid: UUID, stat: String): Double {
        derivedStats[stat]?.let { derived ->
            val playerMap = stats[uuid] ?: return 0.0
            return derived.compute(playerMap)
        }
        return (stats[uuid]?.get(stat) ?: 0).toDouble()
    }

    fun getAll(player: Player): Map<String, Long> = getAll(player.uuid)

    fun getAll(uuid: UUID): Map<String, Long> =
        stats[uuid]?.toMap() ?: emptyMap()

    fun reset(player: Player, stat: String) = reset(player.uuid, stat)

    fun reset(uuid: UUID, stat: String) {
        stats[uuid]?.remove(stat)
    }

    fun resetAll(player: Player) = resetAll(player.uuid)

    fun resetAll(uuid: UUID) {
        stats.remove(uuid)
    }

    fun resetStat(stat: String) {
        stats.values.forEach { it.remove(stat) }
    }

    fun clear() {
        stats.clear()
        derivedStats.clear()
    }

    fun top(stat: String, limit: Int = 10): List<Pair<UUID, Long>> {
        if (derivedStats.containsKey(stat)) {
            return stats.entries
                .map { (uuid, playerMap) -> uuid to derivedStats[stat]!!.compute(playerMap).toLong() }
                .sortedByDescending { it.second }
                .take(limit)
        }
        return stats.entries
            .mapNotNull { (uuid, playerMap) ->
                val value = playerMap[stat] ?: return@mapNotNull null
                uuid to value
            }
            .sortedByDescending { it.second }
            .take(limit)
    }

    fun topDouble(stat: String, limit: Int = 10): List<Pair<UUID, Double>> {
        if (derivedStats.containsKey(stat)) {
            return stats.entries
                .map { (uuid, playerMap) -> uuid to derivedStats[stat]!!.compute(playerMap) }
                .sortedByDescending { it.second }
                .take(limit)
        }
        return stats.entries
            .mapNotNull { (uuid, playerMap) ->
                val value = playerMap[stat] ?: return@mapNotNull null
                uuid to value.toDouble()
            }
            .sortedByDescending { it.second }
            .take(limit)
    }

    fun renderLeaderboard(stat: String, limit: Int = 10, nameResolver: (UUID) -> String = { it.toString().take(8) }): List<Component> = buildList {
        top(stat, limit).forEachIndexed { index, (uuid, score) ->
            add(
                translateDefault(
                    "orbit.util.leaderboard.entry",
                    "rank" to "${index + 1}",
                    "name" to nameResolver(uuid),
                    "score" to "$score",
                ),
            )
        }
    }

    fun registerDerived(name: String, inputs: List<String>, compute: (Map<String, Long>) -> Double) {
        derivedStats[name] = DerivedStat(inputs, compute)
    }

    fun players(): Set<UUID> = stats.keys.toSet()

    private fun playerStats(uuid: UUID): ConcurrentHashMap<String, Long> =
        stats.getOrPut(uuid) { ConcurrentHashMap() }
}

private class DerivedStat(
    val inputs: List<String>,
    val computeFn: (Map<String, Long>) -> Double,
) {
    fun compute(playerMap: ConcurrentHashMap<String, Long>): Double {
        val inputValues = inputs.associateWith { playerMap[it] ?: 0L }
        return computeFn(inputValues)
    }
}

class StatTrackerBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val statNames = mutableListOf<String>()
    @PublishedApi internal val derived = mutableListOf<DerivedStatDef>()

    fun stat(name: String) { statNames.add(name) }

    fun derived(name: String, vararg inputs: String, compute: (Map<String, Long>) -> Double) {
        derived.add(DerivedStatDef(name, inputs.toList(), compute))
    }

    @PublishedApi internal fun build() {
        derived.forEach { def ->
            StatTracker.registerDerived(def.name, def.inputs, def.compute)
        }
    }
}

@PublishedApi internal data class DerivedStatDef(
    val name: String,
    val inputs: List<String>,
    val compute: (Map<String, Long>) -> Double,
)

inline fun statTracker(block: StatTrackerBuilder.() -> Unit) {
    StatTrackerBuilder().apply(block).build()
}

fun Player.sendLeaderboard(stat: String, limit: Int = 10, nameResolver: (UUID) -> String = { it.toString().take(8) }) {
    StatTracker.renderLeaderboard(stat, limit, nameResolver).forEach { sendMessage(it) }
}

data class LeaderboardEntry(
    val uuid: UUID,
    val name: String,
    val score: Double,
)

class Leaderboard(
    val name: String,
    val displayName: Component = Component.text(name),
    val maxEntries: Int = 10,
    val ascending: Boolean = false,
) {
    private val scores = ConcurrentHashMap<UUID, Pair<String, Double>>()

    fun setScore(uuid: UUID, name: String, score: Double) {
        scores[uuid] = name to score
    }

    fun addScore(uuid: UUID, name: String, amount: Double) {
        scores.compute(uuid) { _, current ->
            val existing = current?.second ?: 0.0
            name to (existing + amount)
        }
    }

    fun getScore(uuid: UUID): Double = scores[uuid]?.second ?: 0.0

    fun removePlayer(uuid: UUID) = scores.remove(uuid)

    fun top(limit: Int = maxEntries): List<LeaderboardEntry> =
        scores.entries
            .map { LeaderboardEntry(it.key, it.value.first, it.value.second) }
            .let { entries ->
                if (ascending) entries.sortedBy { it.score }
                else entries.sortedByDescending { it.score }
            }
            .take(limit)

    fun rank(uuid: UUID): Int {
        val sorted = if (ascending) {
            scores.entries.sortedBy { it.value.second }
        } else {
            scores.entries.sortedByDescending { it.value.second }
        }
        return sorted.indexOfFirst { it.key == uuid } + 1
    }

    fun render(limit: Int = maxEntries): List<Component> = buildList {
        add(displayName)
        add(Component.empty())
        top(limit).forEachIndexed { index, entry ->
            add(
                translateDefault(
                    "orbit.util.leaderboard.entry",
                    "rank" to "${index + 1}",
                    "name" to entry.name,
                    "score" to "%.1f".format(entry.score),
                ),
            )
        }
    }

    fun sendTo(player: Player, limit: Int = maxEntries) {
        render(limit).forEach { player.sendMessage(it) }
    }

    fun clear() = scores.clear()

    val size: Int get() = scores.size
}

object LeaderboardRegistry {

    private val leaderboards = ConcurrentHashMap<String, Leaderboard>()

    fun register(leaderboard: Leaderboard) {
        leaderboards[leaderboard.name] = leaderboard
    }

    fun unregister(name: String) = leaderboards.remove(name)

    operator fun get(name: String): Leaderboard? = leaderboards[name]

    fun all(): Map<String, Leaderboard> = leaderboards.toMap()

    fun clear() = leaderboards.clear()
}

class LeaderboardBuilder @PublishedApi internal constructor() {

    @PublishedApi internal lateinit var name: String
    @PublishedApi internal var displayName: Component? = null
    @PublishedApi internal var maxEntries: Int = 10
    @PublishedApi internal var ascending: Boolean = false

    fun displayName(component: Component) { displayName = component }
    fun maxEntries(max: Int) { maxEntries = max }
    fun ascending(asc: Boolean = true) { ascending = asc }

    @PublishedApi internal fun build(): Leaderboard = Leaderboard(
        name = name,
        displayName = displayName ?: Component.text(name),
        maxEntries = maxEntries,
        ascending = ascending,
    )
}

inline fun leaderboard(name: String, block: LeaderboardBuilder.() -> Unit = {}): Leaderboard {
    val builder = LeaderboardBuilder().apply { this.name = name }.apply(block)
    val lb = builder.build()
    LeaderboardRegistry.register(lb)
    return lb
}
