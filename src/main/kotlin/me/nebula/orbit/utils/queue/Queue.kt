package me.nebula.orbit.utils.queue

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue

class GameQueue(
    val name: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val countdownTicks: Int = 200,
    private val onStart: (List<UUID>) -> Unit,
    private val onCountdownTick: ((Int) -> Unit)? = null,
    private val onJoin: ((UUID) -> Unit)? = null,
    private val onLeave: ((UUID) -> Unit)? = null,
) {

    private val queue = ConcurrentLinkedDeque<UUID>()
    private var countdownTask: Task? = null

    @Volatile
    private var countdown = 0

    @Volatile
    var state: QueueState = QueueState.WAITING
        private set

    val size: Int get() = queue.size
    val isFull: Boolean get() = queue.size >= maxPlayers
    val canStart: Boolean get() = queue.size >= minPlayers
    val players: List<UUID> get() = queue.toList()
    val countdownRemaining: Int get() = countdown

    fun join(player: Player): JoinResult = join(player.uuid)

    fun join(uuid: UUID): JoinResult {
        if (queue.contains(uuid)) return JoinResult.ALREADY_QUEUED
        if (isFull) return JoinResult.FULL
        if (state == QueueState.STARTING) return JoinResult.STARTING
        queue.add(uuid)
        onJoin?.invoke(uuid)
        if (canStart && state == QueueState.WAITING) startCountdown()
        return JoinResult.SUCCESS
    }

    fun leave(player: Player): Boolean = leave(player.uuid)

    fun leave(uuid: UUID): Boolean {
        if (!queue.remove(uuid)) return false
        onLeave?.invoke(uuid)
        if (!canStart && state == QueueState.COUNTDOWN) cancelCountdown()
        return true
    }

    fun contains(uuid: UUID): Boolean = queue.contains(uuid)

    fun reset() {
        cancelCountdown()
        queue.clear()
        state = QueueState.WAITING
    }

    fun forceStart() {
        if (queue.isEmpty()) return
        cancelCountdown()
        state = QueueState.STARTING
        val participants = queue.toList().take(maxPlayers)
        participants.forEach { queue.remove(it) }
        state = QueueState.WAITING
        onStart(participants)
    }

    private fun startCountdown() {
        state = QueueState.COUNTDOWN
        countdown = countdownTicks
        countdownTask = MinecraftServer.getSchedulerManager()
            .buildTask {
                countdown--
                onCountdownTick?.invoke(countdown)
                if (countdown <= 0) {
                    forceStart()
                }
            }
            .repeat(TaskSchedule.tick(1))
            .schedule()
    }

    private fun cancelCountdown() {
        countdownTask?.cancel()
        countdownTask = null
        if (state == QueueState.COUNTDOWN) state = QueueState.WAITING
    }
}

enum class QueueState { WAITING, COUNTDOWN, STARTING }
enum class JoinResult { SUCCESS, ALREADY_QUEUED, FULL, STARTING }

class GameQueueBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var minPlayers = 2
    @PublishedApi internal var maxPlayers = 16
    @PublishedApi internal var countdownTicks = 200
    @PublishedApi internal var onStart: ((List<UUID>) -> Unit)? = null
    @PublishedApi internal var onCountdownTick: ((Int) -> Unit)? = null
    @PublishedApi internal var onJoin: ((UUID) -> Unit)? = null
    @PublishedApi internal var onLeave: ((UUID) -> Unit)? = null

    fun minPlayers(min: Int) { minPlayers = min }
    fun maxPlayers(max: Int) { maxPlayers = max }
    fun countdownTicks(ticks: Int) { countdownTicks = ticks }
    fun onStart(action: (List<UUID>) -> Unit) { onStart = action }
    fun onCountdownTick(action: (Int) -> Unit) { onCountdownTick = action }
    fun onJoin(action: (UUID) -> Unit) { onJoin = action }
    fun onLeave(action: (UUID) -> Unit) { onLeave = action }

    @PublishedApi internal fun build(): GameQueue = GameQueue(
        name = name,
        minPlayers = minPlayers,
        maxPlayers = maxPlayers,
        countdownTicks = countdownTicks,
        onStart = requireNotNull(onStart) { "GameQueue '$name' requires an onStart handler" },
        onCountdownTick = onCountdownTick,
        onJoin = onJoin,
        onLeave = onLeave,
    )
}

inline fun gameQueue(name: String, block: GameQueueBuilder.() -> Unit): GameQueue =
    GameQueueBuilder(name).apply(block).build()

class SimpleQueue(
    val name: String,
    val maxSize: Int = Int.MAX_VALUE,
    private val onJoin: (Player, Int) -> Unit = { _, _ -> },
    private val onLeave: (Player) -> Unit = {},
    private val onReady: (List<Player>) -> Unit = {},
    val requiredPlayers: Int = 1,
) {
    private val queue = ConcurrentLinkedQueue<UUID>()
    private val playerMap = ConcurrentHashMap<UUID, Player>()

    val size: Int get() = queue.size
    val isEmpty: Boolean get() = queue.isEmpty()

    fun join(player: Player): Boolean {
        if (queue.size >= maxSize) return false
        if (queue.contains(player.uuid)) return false
        queue.offer(player.uuid)
        playerMap[player.uuid] = player
        onJoin(player, queue.size)
        if (queue.size >= requiredPlayers) {
            val ready = queue.take(requiredPlayers).mapNotNull { playerMap[it] }
            if (ready.size >= requiredPlayers) {
                ready.forEach { leave(it) }
                onReady(ready)
            }
        }
        return true
    }

    fun leave(player: Player): Boolean {
        if (!queue.remove(player.uuid)) return false
        playerMap.remove(player.uuid)
        onLeave(player)
        return true
    }

    fun contains(player: Player): Boolean = queue.contains(player.uuid)

    fun players(): List<Player> = queue.mapNotNull { playerMap[it] }

    fun clear() {
        queue.clear()
        playerMap.clear()
    }

    fun position(player: Player): Int {
        var pos = 1
        for (uuid in queue) {
            if (uuid == player.uuid) return pos
            pos++
        }
        return -1
    }
}

class SimpleQueueBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var maxSize: Int = Int.MAX_VALUE
    @PublishedApi internal var requiredPlayers: Int = 1
    @PublishedApi internal var onJoin: (Player, Int) -> Unit = { _, _ -> }
    @PublishedApi internal var onLeave: (Player) -> Unit = {}
    @PublishedApi internal var onReady: (List<Player>) -> Unit = {}

    fun maxSize(size: Int) { maxSize = size }
    fun requiredPlayers(count: Int) { requiredPlayers = count }
    fun onJoin(handler: (Player, Int) -> Unit) { onJoin = handler }
    fun onLeave(handler: (Player) -> Unit) { onLeave = handler }
    fun onReady(handler: (List<Player>) -> Unit) { onReady = handler }

    @PublishedApi internal fun build(): SimpleQueue = SimpleQueue(name, maxSize, onJoin, onLeave, onReady, requiredPlayers)
}

inline fun simpleQueue(name: String, block: SimpleQueueBuilder.() -> Unit): SimpleQueue =
    SimpleQueueBuilder(name).apply(block).build()

object QueueRegistry {

    private val queues = ConcurrentHashMap<String, GameQueue>()

    fun register(queue: GameQueue) {
        require(!queues.containsKey(queue.name)) { "Queue '${queue.name}' already registered" }
        queues[queue.name] = queue
    }

    fun unregister(name: String): GameQueue? = queues.remove(name)

    operator fun get(name: String): GameQueue? = queues[name]
    fun require(name: String): GameQueue = requireNotNull(queues[name]) { "Queue '$name' not found" }
    fun all(): Map<String, GameQueue> = queues.toMap()
    fun clear() { queues.values.forEach { it.reset() }; queues.clear() }
}

fun Player.joinQueue(name: String): JoinResult =
    QueueRegistry.require(name).join(this)

fun Player.leaveQueue(name: String): Boolean =
    QueueRegistry[name]?.leave(this) ?: false

fun Player.queuePosition(name: String): Int? {
    val queue = QueueRegistry[name] ?: return null
    val index = queue.players.indexOf(uuid)
    return if (index >= 0) index + 1 else null
}
