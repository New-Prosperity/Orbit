package me.nebula.orbit.utils.mappool

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class GameMap(
    val name: String,
    val displayName: String,
    val authors: List<String> = emptyList(),
    val minPlayers: Int = 1,
    val maxPlayers: Int = Int.MAX_VALUE,
    val metadata: Map<String, String> = emptyMap(),
)

class MapPool(
    val name: String,
    maps: List<GameMap>,
    private val strategy: SelectionStrategy = SelectionStrategy.RANDOM,
    private val recentExclusion: Int = 2,
    private val recentWeight: Double = 0.0,
) {

    init {
        require(recentExclusion >= 0) { "recentExclusion must be >= 0 (got $recentExclusion)" }
        require(recentWeight in 0.0..1.0) { "recentWeight must be in 0.0..1.0 (got $recentWeight)" }
    }

    private val allMaps = maps.toMutableList()
    private val recentlyPlayed = ArrayDeque<String>(recentExclusion + 1)
    private val votes = ConcurrentHashMap<UUID, String>()
    private var rotationIndex = 0

    val maps: List<GameMap> get() = allMaps.toList()
    val size: Int get() = allMaps.size

    fun addMap(map: GameMap) {
        require(allMaps.none { it.name == map.name }) { "Map '${map.name}' already in pool" }
        allMaps.add(map)
    }

    fun removeMap(name: String): Boolean =
        allMaps.removeAll { it.name == name }

    fun getMap(name: String): GameMap? = allMaps.firstOrNull { it.name == name }

    fun selectNext(playerCount: Int = 0): GameMap {
        require(allMaps.isNotEmpty()) { "MapPool '$name' is empty" }

        val playerEligible = allMaps
            .filter { playerCount == 0 || playerCount in it.minPlayers..it.maxPlayers }
            .ifEmpty { allMaps }

        val selected = when (strategy) {
            SelectionStrategy.RANDOM -> selectWeighted(playerEligible)
            SelectionStrategy.ROTATION -> {
                val rotationCandidates = playerEligible.filter { it.name !in recentlyPlayed }.ifEmpty { playerEligible }
                rotationIndex %= rotationCandidates.size
                rotationCandidates[rotationIndex++]
            }
            SelectionStrategy.VOTE -> {
                val voteCandidates = playerEligible.filter { it.name !in recentlyPlayed }.ifEmpty { playerEligible }
                resolveVotes(voteCandidates)
            }
        }

        markPlayed(selected.name)
        return selected
    }

    private fun selectWeighted(candidates: List<GameMap>): GameMap {
        if (candidates.size == 1) return candidates[0]
        if (recentWeight == 0.0) {
            val nonRecent = candidates.filter { it.name !in recentlyPlayed }.ifEmpty { candidates }
            return nonRecent[Random.nextInt(nonRecent.size)]
        }
        val weights = DoubleArray(candidates.size)
        var total = 0.0
        for ((i, map) in candidates.withIndex()) {
            val w = if (map.name in recentlyPlayed) recentWeight else 1.0
            weights[i] = w
            total += w
        }
        if (total <= 0.0) return candidates[Random.nextInt(candidates.size)]
        var roll = Random.nextDouble(total)
        for ((i, w) in weights.withIndex()) {
            roll -= w
            if (roll <= 0.0) return candidates[i]
        }
        return candidates.last()
    }

    fun vote(voter: UUID, mapName: String): Boolean {
        if (allMaps.none { it.name == mapName }) return false
        votes[voter] = mapName
        return true
    }

    fun removeVote(voter: UUID) = votes.remove(voter)

    fun voteCount(mapName: String): Int = votes.values.count { it == mapName }

    fun voteTally(): Map<String, Int> =
        votes.values.groupingBy { it }.eachCount()

    fun clearVotes() = votes.clear()

    fun eligible(playerCount: Int = 0): List<GameMap> =
        allMaps
            .filter { it.name !in recentlyPlayed }
            .filter { playerCount == 0 || playerCount in it.minPlayers..it.maxPlayers }

    private fun markPlayed(name: String) {
        recentlyPlayed.addLast(name)
        while (recentlyPlayed.size > recentExclusion) recentlyPlayed.removeFirst()
        clearVotes()
    }

    private fun resolveVotes(eligible: List<GameMap>): GameMap {
        if (votes.isEmpty()) return eligible[Random.nextInt(eligible.size)]
        val tally = votes.values
            .filter { name -> eligible.any { it.name == name } }
            .groupingBy { it }
            .eachCount()
        val topVoted = tally.maxByOrNull { it.value }?.key
            ?: return eligible[Random.nextInt(eligible.size)]
        return eligible.first { it.name == topVoted }
    }
}

enum class SelectionStrategy { RANDOM, ROTATION, VOTE }

class MapPoolBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal val maps = mutableListOf<GameMap>()
    @PublishedApi internal var strategy = SelectionStrategy.RANDOM
    @PublishedApi internal var recentExclusion = 2
    @PublishedApi internal var recentWeight = 0.0

    fun map(name: String, displayName: String, block: GameMapBuilder.() -> Unit = {}) {
        maps.add(GameMapBuilder(name, displayName).apply(block).build())
    }

    fun strategy(strategy: SelectionStrategy) { this.strategy = strategy }
    fun recentExclusion(count: Int) { recentExclusion = count }
    fun recentWeight(weight: Double) { recentWeight = weight }

    @PublishedApi internal fun build(): MapPool = MapPool(name, maps, strategy, recentExclusion, recentWeight)
}

class GameMapBuilder @PublishedApi internal constructor(
    private val name: String,
    private val displayName: String,
) {
    @PublishedApi internal var authors = mutableListOf<String>()
    @PublishedApi internal var minPlayers = 1
    @PublishedApi internal var maxPlayers = Int.MAX_VALUE
    @PublishedApi internal val metadata = mutableMapOf<String, String>()

    fun author(name: String) { authors.add(name) }
    fun authors(vararg names: String) { authors.addAll(names) }
    fun minPlayers(min: Int) { minPlayers = min }
    fun maxPlayers(max: Int) { maxPlayers = max }
    fun meta(key: String, value: String) { metadata[key] = value }

    @PublishedApi internal fun build(): GameMap = GameMap(name, displayName, authors, minPlayers, maxPlayers, metadata)
}

inline fun mapPool(name: String, block: MapPoolBuilder.() -> Unit): MapPool =
    MapPoolBuilder(name).apply(block).build()
