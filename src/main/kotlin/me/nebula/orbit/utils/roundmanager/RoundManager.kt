package me.nebula.orbit.utils.roundmanager

import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class RoundState {
    IDLE,
    ROUND_ACTIVE,
    INTERMISSION,
    FINISHED,
}

data class RoundData(
    val round: Int,
    val scores: ConcurrentHashMap<UUID, Int> = ConcurrentHashMap(),
    var startTime: Long = 0,
    var endTime: Long = 0,
)

class RoundManager(
    val name: String,
    private val totalRounds: Int,
    private val intermissionDuration: Duration,
    private val roundDuration: Duration,
    private val onRoundStartHandler: ((Int) -> Unit)?,
    private val onRoundEndHandler: ((Int) -> Unit)?,
    private val onIntermissionHandler: ((Int, Duration) -> Unit)?,
    private val onGameEndHandler: (() -> Unit)?,
    private val onRoundTickHandler: ((Int, Duration) -> Unit)?,
) {

    @Volatile var state: RoundState = RoundState.IDLE
        private set

    @Volatile var currentRound: Int = 0
        private set

    private val rounds = ConcurrentHashMap<Int, RoundData>()
    private val globalScores = ConcurrentHashMap<UUID, Int>()
    private var activeTask: Task? = null

    fun start() {
        require(state == RoundState.IDLE) { "Game already started" }
        currentRound = 0
        rounds.clear()
        globalScores.clear()
        nextRound()
    }

    fun nextRound() {
        require(state != RoundState.FINISHED) { "Game already finished" }
        cancelTask()
        currentRound++
        if (currentRound > totalRounds) {
            endGame()
            return
        }

        val data = RoundData(currentRound, startTime = System.currentTimeMillis())
        rounds[currentRound] = data
        state = RoundState.ROUND_ACTIVE
        onRoundStartHandler?.invoke(currentRound)

        val roundMs = roundDuration.toMillis()
        val startTime = System.currentTimeMillis()

        activeTask = MinecraftServer.getSchedulerManager().buildTask {
            val elapsed = System.currentTimeMillis() - startTime
            val remaining = roundMs - elapsed
            if (remaining <= 0) {
                endRound()
            } else {
                onRoundTickHandler?.invoke(currentRound, Duration.ofMillis(remaining))
            }
        }.repeat(TaskSchedule.tick(1)).schedule()
    }

    fun endRound() {
        if (state != RoundState.ROUND_ACTIVE) return
        cancelTask()
        rounds[currentRound]?.endTime = System.currentTimeMillis()
        onRoundEndHandler?.invoke(currentRound)

        if (currentRound >= totalRounds) {
            endGame()
            return
        }

        state = RoundState.INTERMISSION
        val intermissionMs = intermissionDuration.toMillis()
        val startTime = System.currentTimeMillis()

        activeTask = MinecraftServer.getSchedulerManager().buildTask {
            val elapsed = System.currentTimeMillis() - startTime
            val remaining = intermissionMs - elapsed
            if (remaining <= 0) {
                nextRound()
            } else {
                onIntermissionHandler?.invoke(currentRound + 1, Duration.ofMillis(remaining))
            }
        }.repeat(TaskSchedule.tick(1)).schedule()
    }

    fun endGame() {
        cancelTask()
        state = RoundState.FINISHED
        onGameEndHandler?.invoke()
    }

    fun addScore(uuid: UUID, points: Int = 1) {
        rounds[currentRound]?.scores?.compute(uuid) { _, current -> (current ?: 0) + points }
        globalScores.compute(uuid) { _, current -> (current ?: 0) + points }
    }

    fun getScore(uuid: UUID): Int = globalScores[uuid] ?: 0

    fun getRoundScore(round: Int, uuid: UUID): Int = rounds[round]?.scores?.get(uuid) ?: 0

    fun getRoundData(round: Int): RoundData? = rounds[round]

    fun allScores(): Map<UUID, Int> = globalScores.toMap()

    fun leaderboard(limit: Int = 10): List<Pair<UUID, Int>> =
        globalScores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }

    fun roundDurationElapsed(): Duration {
        val data = rounds[currentRound] ?: return Duration.ZERO
        return Duration.ofMillis(System.currentTimeMillis() - data.startTime)
    }

    fun destroy() {
        cancelTask()
        rounds.clear()
        globalScores.clear()
        state = RoundState.IDLE
    }

    private fun cancelTask() {
        activeTask?.cancel()
        activeTask = null
    }
}

class RoundManagerBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var totalRounds: Int = 5
    @PublishedApi internal var intermissionDuration: Duration = Duration.ofSeconds(5)
    @PublishedApi internal var roundDuration: Duration = Duration.ofSeconds(60)
    @PublishedApi internal var onRoundStartHandler: ((Int) -> Unit)? = null
    @PublishedApi internal var onRoundEndHandler: ((Int) -> Unit)? = null
    @PublishedApi internal var onIntermissionHandler: ((Int, Duration) -> Unit)? = null
    @PublishedApi internal var onGameEndHandler: (() -> Unit)? = null
    @PublishedApi internal var onRoundTickHandler: ((Int, Duration) -> Unit)? = null

    fun rounds(count: Int) { totalRounds = count }
    fun intermissionDuration(duration: Duration) { intermissionDuration = duration }
    fun roundDuration(duration: Duration) { roundDuration = duration }
    fun onRoundStart(handler: (Int) -> Unit) { onRoundStartHandler = handler }
    fun onRoundEnd(handler: (Int) -> Unit) { onRoundEndHandler = handler }
    fun onIntermission(handler: (nextRound: Int, remaining: Duration) -> Unit) { onIntermissionHandler = handler }
    fun onGameEnd(handler: () -> Unit) { onGameEndHandler = handler }
    fun onRoundTick(handler: (Int, Duration) -> Unit) { onRoundTickHandler = handler }

    @PublishedApi internal fun build(): RoundManager = RoundManager(
        name = name,
        totalRounds = totalRounds,
        intermissionDuration = intermissionDuration,
        roundDuration = roundDuration,
        onRoundStartHandler = onRoundStartHandler,
        onRoundEndHandler = onRoundEndHandler,
        onIntermissionHandler = onIntermissionHandler,
        onGameEndHandler = onGameEndHandler,
        onRoundTickHandler = onRoundTickHandler,
    )
}

inline fun roundManager(name: String, block: RoundManagerBuilder.() -> Unit): RoundManager =
    RoundManagerBuilder(name).apply(block).build()
