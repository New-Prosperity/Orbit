package me.nebula.orbit.utils.combatarena

import me.nebula.orbit.utils.kit.Kit
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class CombatArenaState { WAITING, ACTIVE, ENDED }

data class PlayerStats(
    val uuid: UUID,
    val kills: AtomicInteger = AtomicInteger(0),
    val deaths: AtomicInteger = AtomicInteger(0),
    val damageDealt: java.util.concurrent.atomic.AtomicLong = java.util.concurrent.atomic.AtomicLong(0),
)

data class ArenaResult(
    val arenaName: String,
    val winner: UUID?,
    val stats: Map<UUID, PlayerStats>,
    val durationMs: Long,
)

class CombatArena @PublishedApi internal constructor(
    val name: String,
    val instance: Instance,
    val spawnPoints: List<Pos>,
    val maxPlayers: Int,
    val kit: Kit?,
    val durationTicks: Int,
    private val onKillHandler: ((Player, Player) -> Unit)?,
    private val onEndHandler: ((ArenaResult) -> Unit)?,
) {

    @Volatile var state: CombatArenaState = CombatArenaState.WAITING
        private set

    private val players = ConcurrentHashMap<UUID, PlayerStats>()
    private var spawnIndex = 0
    private var timerTask: Task? = null
    private var startTime: Long = 0
    private var ticksElapsed = 0

    val playerCount: Int get() = players.size
    val isActive: Boolean get() = state == CombatArenaState.ACTIVE

    fun join(player: Player): Boolean {
        if (state != CombatArenaState.WAITING) return false
        if (players.size >= maxPlayers) return false
        players[player.uuid] = PlayerStats(player.uuid)
        val spawn = spawnPoints[spawnIndex % spawnPoints.size]
        spawnIndex++
        player.setInstance(instance, spawn)
        kit?.apply(player)
        return true
    }

    fun leave(player: Player) {
        players.remove(player.uuid)
        if (state == CombatArenaState.ACTIVE && players.size <= 1) {
            end()
        }
    }

    fun start() {
        require(state == CombatArenaState.WAITING) { "Arena '$name' is not in WAITING state" }
        state = CombatArenaState.ACTIVE
        startTime = System.currentTimeMillis()
        ticksElapsed = 0

        if (durationTicks > 0) {
            timerTask = MinecraftServer.getSchedulerManager()
                .buildTask {
                    ticksElapsed++
                    if (ticksElapsed >= durationTicks) end()
                }
                .repeat(TaskSchedule.tick(1))
                .schedule()
        }
    }

    fun recordKill(killer: Player, victim: Player) {
        if (state != CombatArenaState.ACTIVE) return
        players[killer.uuid]?.kills?.incrementAndGet()
        players[victim.uuid]?.deaths?.incrementAndGet()
        onKillHandler?.invoke(killer, victim)

        val alivePlayers = onlinePlayers().filter { it.uuid != victim.uuid }
        if (maxPlayers == 2 && alivePlayers.size <= 1) {
            end()
        }
    }

    fun recordDamage(attacker: Player, amount: Double) {
        if (state != CombatArenaState.ACTIVE) return
        players[attacker.uuid]?.damageDealt?.addAndGet(amount.toLong())
    }

    fun end() {
        if (state == CombatArenaState.ENDED) return
        state = CombatArenaState.ENDED
        timerTask?.cancel()
        timerTask = null

        val result = buildResult()
        onEndHandler?.invoke(result)
    }

    fun reset() {
        timerTask?.cancel()
        timerTask = null
        players.clear()
        spawnIndex = 0
        state = CombatArenaState.WAITING
        ticksElapsed = 0
    }

    fun onlinePlayers(): List<Player> =
        instance.players.filter { players.containsKey(it.uuid) }

    fun isParticipant(player: Player): Boolean = players.containsKey(player.uuid)

    fun statsOf(player: Player): PlayerStats? = players[player.uuid]

    private fun buildResult(): ArenaResult {
        val winner = players.entries
            .maxByOrNull { it.value.kills.get() }
            ?.key
        return ArenaResult(
            arenaName = name,
            winner = winner,
            stats = players.toMap(),
            durationMs = System.currentTimeMillis() - startTime,
        )
    }
}

class CombatArenaBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var instance: Instance? = null
    @PublishedApi internal val spawnPoints = mutableListOf<Pos>()
    @PublishedApi internal var maxPlayers: Int = 2
    @PublishedApi internal var kit: Kit? = null
    @PublishedApi internal var durationTicks: Int = 0
    @PublishedApi internal var onKillHandler: ((Player, Player) -> Unit)? = null
    @PublishedApi internal var onEndHandler: ((ArenaResult) -> Unit)? = null

    fun instance(inst: Instance) { instance = inst }
    fun spawnPoints(points: List<Pos>) { spawnPoints.addAll(points) }
    fun spawnPoint(pos: Pos) { spawnPoints.add(pos) }
    fun maxPlayers(count: Int) { maxPlayers = count }
    fun kit(kit: Kit) { this.kit = kit }
    fun duration(dur: Duration) { durationTicks = (dur.inWholeMilliseconds / 50).toInt() }
    fun onKill(handler: (killer: Player, victim: Player) -> Unit) { onKillHandler = handler }
    fun onEnd(handler: (ArenaResult) -> Unit) { onEndHandler = handler }

    @PublishedApi internal fun build(): CombatArena = CombatArena(
        name = name,
        instance = requireNotNull(instance) { "CombatArena '$name' requires an instance" },
        spawnPoints = spawnPoints.toList(),
        maxPlayers = maxPlayers,
        kit = kit,
        durationTicks = durationTicks,
        onKillHandler = onKillHandler,
        onEndHandler = onEndHandler,
    )
}

inline fun combatArena(name: String, block: CombatArenaBuilder.() -> Unit): CombatArena =
    CombatArenaBuilder(name).apply(block).build()

object CombatArenaManager {

    private val arenas = ConcurrentHashMap<String, CombatArena>()

    fun register(arena: CombatArena) {
        require(!arenas.containsKey(arena.name)) { "CombatArena '${arena.name}' already registered" }
        arenas[arena.name] = arena
    }

    fun unregister(name: String) {
        arenas.remove(name)?.reset()
    }

    operator fun get(name: String): CombatArena? = arenas[name]
    fun require(name: String): CombatArena = requireNotNull(arenas[name]) { "CombatArena '$name' not found" }

    fun all(): Map<String, CombatArena> = arenas.toMap()
    fun active(): List<CombatArena> = arenas.values.filter { it.isActive }
    fun waiting(): List<CombatArena> = arenas.values.filter { it.state == CombatArenaState.WAITING }

    fun findArena(player: Player): CombatArena? =
        arenas.values.firstOrNull { it.isParticipant(player) }

    fun join(arenaName: String, player: Player): Boolean =
        arenas[arenaName]?.join(player) ?: false

    fun leave(player: Player) {
        findArena(player)?.leave(player)
    }

    fun start(arenaName: String) {
        require(arenaName).start()
    }

    fun end(arenaName: String) {
        require(arenaName).end()
    }

    fun clear() {
        arenas.values.forEach { it.reset() }
        arenas.clear()
    }
}
