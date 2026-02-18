package me.nebula.orbit.utils.arena

import me.nebula.orbit.utils.region.Region
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SpawnPoint(val position: Pos, val team: String? = null)

class Arena(
    val name: String,
    val instance: Instance,
    val region: Region,
    val spawns: List<SpawnPoint>,
    val spectatorSpawn: Pos,
) {

    private val players = ConcurrentHashMap.newKeySet<UUID>()
    private val spectators = ConcurrentHashMap.newKeySet<UUID>()
    private var spawnIndex = 0

    val playerCount: Int get() = players.size
    val spectatorCount: Int get() = spectators.size
    val allParticipants: Set<UUID> get() = players + spectators

    fun addPlayer(player: Player) {
        players.add(player.uuid)
        val spawn = nextSpawn()
        player.setInstance(instance, spawn.position)
    }

    fun addPlayerToTeam(player: Player, team: String) {
        players.add(player.uuid)
        val spawn = spawns.firstOrNull { it.team == team } ?: nextSpawn()
        player.setInstance(instance, spawn.position)
    }

    fun removePlayer(player: Player) {
        players.remove(player.uuid)
        spectators.remove(player.uuid)
    }

    fun addSpectator(player: Player) {
        spectators.add(player.uuid)
        player.setInstance(instance, spectatorSpawn)
    }

    fun isPlayer(uuid: UUID): Boolean = players.contains(uuid)
    fun isSpectator(uuid: UUID): Boolean = spectators.contains(uuid)
    fun isParticipant(uuid: UUID): Boolean = players.contains(uuid) || spectators.contains(uuid)

    fun onlinePlayers(): List<Player> =
        instance.players.filter { players.contains(it.uuid) }

    fun onlineSpectators(): List<Player> =
        instance.players.filter { spectators.contains(it.uuid) }

    fun respawn(player: Player) {
        val spawn = nextSpawn()
        player.teleport(spawn.position)
    }

    fun respawnToTeam(player: Player, team: String) {
        val spawn = spawns.firstOrNull { it.team == team } ?: nextSpawn()
        player.teleport(spawn.position)
    }

    fun containsPosition(pos: Pos): Boolean = region.contains(pos)

    fun reset() {
        players.clear()
        spectators.clear()
        spawnIndex = 0
    }

    private fun nextSpawn(): SpawnPoint {
        if (spawns.isEmpty()) return SpawnPoint(spectatorSpawn)
        val spawn = spawns[spawnIndex % spawns.size]
        spawnIndex++
        return spawn
    }
}

class ArenaBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var instance: Instance? = null
    @PublishedApi internal var region: Region? = null
    @PublishedApi internal val spawns = mutableListOf<SpawnPoint>()
    @PublishedApi internal var spectatorSpawn = Pos(0.0, 64.0, 0.0)

    fun instance(instance: Instance) { this.instance = instance }
    fun region(region: Region) { this.region = region }
    fun spawn(pos: Pos, team: String? = null) { spawns.add(SpawnPoint(pos, team)) }
    fun spectatorSpawn(pos: Pos) { spectatorSpawn = pos }

    @PublishedApi internal fun build(): Arena = Arena(
        name = name,
        instance = requireNotNull(instance) { "Arena '$name' requires an instance" },
        region = requireNotNull(region) { "Arena '$name' requires a region" },
        spawns = spawns.toList(),
        spectatorSpawn = spectatorSpawn,
    )
}

inline fun arena(name: String, block: ArenaBuilder.() -> Unit): Arena =
    ArenaBuilder(name).apply(block).build()

object ArenaRegistry {

    private val arenas = ConcurrentHashMap<String, Arena>()

    fun register(arena: Arena) {
        require(!arenas.containsKey(arena.name)) { "Arena '${arena.name}' already registered" }
        arenas[arena.name] = arena
    }

    fun unregister(name: String): Arena? = arenas.remove(name)

    operator fun get(name: String): Arena? = arenas[name]
    fun require(name: String): Arena = requireNotNull(arenas[name]) { "Arena '$name' not found" }

    fun all(): Map<String, Arena> = arenas.toMap()
    fun names(): Set<String> = arenas.keys.toSet()
    fun clear() = arenas.clear()
}
