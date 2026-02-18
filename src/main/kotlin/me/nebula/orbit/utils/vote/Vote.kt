package me.nebula.orbit.utils.vote

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class Poll<T>(
    val question: String,
    val options: List<T>,
    val durationTicks: Int = 600,
    private val onComplete: (PollResult<T>) -> Unit,
    private val displayName: (T) -> String = { it.toString() },
) {

    private val votes = ConcurrentHashMap<UUID, Int>()
    private var task: Task? = null

    @Volatile
    var isActive = false
        private set

    @Volatile
    var ticksRemaining = durationTicks
        private set

    fun start() {
        require(!isActive) { "Poll already active" }
        isActive = true
        ticksRemaining = durationTicks
        votes.clear()

        task = MinecraftServer.getSchedulerManager()
            .buildTask {
                ticksRemaining--
                if (ticksRemaining <= 0) end()
            }
            .repeat(TaskSchedule.tick(1))
            .schedule()
    }

    fun vote(player: Player, optionIndex: Int): Boolean {
        if (!isActive) return false
        if (optionIndex !in options.indices) return false
        votes[player.uuid] = optionIndex
        return true
    }

    fun vote(uuid: UUID, optionIndex: Int): Boolean {
        if (!isActive) return false
        if (optionIndex !in options.indices) return false
        votes[uuid] = optionIndex
        return true
    }

    fun removeVote(uuid: UUID) = votes.remove(uuid)

    fun hasVoted(uuid: UUID): Boolean = votes.containsKey(uuid)

    fun tally(): Map<Int, Int> =
        votes.values.groupingBy { it }.eachCount()

    fun tallyNamed(): Map<String, Int> =
        tally().mapKeys { (index, _) -> displayName(options[index]) }

    fun end() {
        if (!isActive) return
        isActive = false
        task?.cancel()
        task = null

        val tally = tally()
        val winnerIndex = tally.maxByOrNull { it.value }?.key
        val winner = winnerIndex?.let { options[it] }
        onComplete(PollResult(winner, tally, votes.size, options))
    }

    fun cancel() {
        isActive = false
        task?.cancel()
        task = null
        votes.clear()
    }

    fun optionDisplay(index: Int): String = displayName(options[index])

    val totalVotes: Int get() = votes.size
}

data class PollResult<T>(
    val winner: T?,
    val tally: Map<Int, Int>,
    val totalVotes: Int,
    val options: List<T>,
)

class PollBuilder<T> @PublishedApi internal constructor(private val question: String) {

    @PublishedApi internal val options = mutableListOf<T>()
    @PublishedApi internal var durationTicks = 600
    @PublishedApi internal var onComplete: ((PollResult<T>) -> Unit)? = null
    @PublishedApi internal var displayName: (T) -> String = { it.toString() }

    fun option(value: T) { options.add(value) }
    fun options(vararg values: T) { options.addAll(values) }
    fun durationTicks(ticks: Int) { durationTicks = ticks }
    fun durationSeconds(seconds: Int) { durationTicks = seconds * 20 }
    fun displayName(fn: (T) -> String) { displayName = fn }
    fun onComplete(action: (PollResult<T>) -> Unit) { onComplete = action }

    @PublishedApi internal fun build(): Poll<T> = Poll(
        question = question,
        options = options.toList(),
        durationTicks = durationTicks,
        onComplete = requireNotNull(onComplete) { "Poll requires an onComplete handler" },
        displayName = displayName,
    )
}

inline fun <reified T> poll(question: String, block: PollBuilder<T>.() -> Unit): Poll<T> =
    PollBuilder<T>(question).apply(block).build()
