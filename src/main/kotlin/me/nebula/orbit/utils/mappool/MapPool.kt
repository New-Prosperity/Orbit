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
) {

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

        val eligible = allMaps
            .filter { it.name !in recentlyPlayed }
            .filter { playerCount == 0 || playerCount in it.minPlayers..it.maxPlayers }
            .ifEmpty { allMaps }

        val selected = when (strategy) {
            SelectionStrategy.RANDOM -> eligible[Random.nextInt(eligible.size)]
            SelectionStrategy.ROTATION -> {
                rotationIndex %= eligible.size
                eligible[rotationIndex++]
            }
            SelectionStrategy.VOTE -> resolveVotes(eligible)
        }

        markPlayed(selected.name)
        return selected
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
        if (tally.isEmpty()) return eligible[Random.nextInt(eligible.size)]
        val topVoted = tally.maxByOrNull { it.value }!!.key
        return eligible.first { it.name == topVoted }
    }
}

enum class SelectionStrategy { RANDOM, ROTATION, VOTE }

class MapPoolBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal val maps = mutableListOf<GameMap>()
    @PublishedApi internal var strategy = SelectionStrategy.RANDOM
    @PublishedApi internal var recentExclusion = 2

    fun map(name: String, displayName: String, block: GameMapBuilder.() -> Unit = {}) {
        maps.add(GameMapBuilder(name, displayName).apply(block).build())
    }

    fun strategy(strategy: SelectionStrategy) { this.strategy = strategy }
    fun recentExclusion(count: Int) { recentExclusion = count }

    @PublishedApi internal fun build(): MapPool = MapPool(name, maps, strategy, recentExclusion)
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
