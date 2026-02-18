package me.nebula.orbit.utils.minigametimer

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private val miniMessage = MiniMessage.miniMessage()

enum class DisplayMode { BOSS_BAR, ACTION_BAR, TITLE }

data class MilestoneCallback(
    val thresholdTicks: Int,
    val action: (Int) -> Unit,
    var fired: Boolean = false,
)

class MinigameTimer @PublishedApi internal constructor(
    val name: String,
    private val totalDurationTicks: Int,
    private val displayMode: DisplayMode,
    private val displayFormat: (Int) -> String,
    private val onTick: (Int) -> Unit,
    private val onEnd: () -> Unit,
    private val milestones: List<MilestoneCallback>,
) {

    private var task: Task? = null
    @Volatile private var remainingTicks: Int = totalDurationTicks
    @Volatile private var paused: Boolean = false
    private val viewers = ConcurrentHashMap.newKeySet<UUID>()
    private var bossBar: BossBar? = null

    val remaining: Int get() = remainingTicks
    val remainingDuration: Duration get() = Duration.ofMillis(remainingTicks * 50L)
    val elapsed: Int get() = totalDurationTicks - remainingTicks
    val isRunning: Boolean get() = task != null && !paused
    val isPaused: Boolean get() = paused

    fun start() {
        require(task == null) { "Timer '$name' already running" }
        remainingTicks = totalDurationTicks
        paused = false
        milestones.forEach { it.fired = false }

        if (displayMode == DisplayMode.BOSS_BAR) {
            bossBar = BossBar.bossBar(
                Component.empty(),
                1f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS,
            )
        }

        task = MinecraftServer.getSchedulerManager()
            .buildTask {
                if (paused) return@buildTask
                if (remainingTicks <= 0) {
                    stop()
                    onEnd()
                    return@buildTask
                }
                onTick(remainingTicks)
                checkMilestones()
                updateDisplay()
                remainingTicks--
            }
            .repeat(TaskSchedule.tick(1))
            .schedule()
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun stop() {
        task?.cancel()
        task = null
        paused = false
        bossBar?.let { bar ->
            viewers.forEach { uuid ->
                MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
                    ?.hideBossBar(bar)
            }
        }
        bossBar = null
    }

    fun addTime(ticks: Int) {
        remainingTicks += ticks
    }

    fun removeTime(ticks: Int) {
        remainingTicks = (remainingTicks - ticks).coerceAtLeast(0)
    }

    fun addViewer(player: Player) {
        viewers.add(player.uuid)
        bossBar?.let { player.showBossBar(it) }
    }

    fun removeViewer(player: Player) {
        viewers.remove(player.uuid)
        bossBar?.let { player.hideBossBar(it) }
    }

    fun addAllViewers(players: Collection<Player>) {
        players.forEach { addViewer(it) }
    }

    private fun checkMilestones() {
        milestones.forEach { milestone ->
            if (!milestone.fired && remainingTicks <= milestone.thresholdTicks) {
                milestone.fired = true
                milestone.action(remainingTicks)
            }
        }
    }

    private fun updateDisplay() {
        val text = displayFormat(remainingTicks)
        val component = miniMessage.deserialize(text)

        when (displayMode) {
            DisplayMode.BOSS_BAR -> {
                val bar = bossBar ?: return
                bar.name(component)
                val progress = if (totalDurationTicks > 0) {
                    remainingTicks.toFloat() / totalDurationTicks.toFloat()
                } else 0f
                bar.progress(progress.coerceIn(0f, 1f))
                bar.color(
                    when {
                        progress > 0.5f -> BossBar.Color.GREEN
                        progress > 0.25f -> BossBar.Color.YELLOW
                        else -> BossBar.Color.RED
                    }
                )
            }
            DisplayMode.ACTION_BAR -> {
                viewers.forEach { uuid ->
                    MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
                        ?.sendActionBar(component)
                }
            }
            DisplayMode.TITLE -> {
                viewers.forEach { uuid ->
                    MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.let { player ->
                        val seconds = remainingTicks / 20
                        if (remainingTicks % 20 == 0 && seconds <= 10) {
                            player.showTitle(
                                net.kyori.adventure.title.Title.title(
                                    component,
                                    Component.empty(),
                                    net.kyori.adventure.title.Title.Times.times(
                                        Duration.ZERO,
                                        Duration.ofMillis(1100),
                                        Duration.ofMillis(200),
                                    ),
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

class MinigameTimerBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var durationTicks: Int = 2400
    @PublishedApi internal var displayMode: DisplayMode = DisplayMode.BOSS_BAR
    @PublishedApi internal var displayFormat: (Int) -> String = { ticks ->
        val s = ticks / 20
        val m = s / 60
        val sec = s % 60
        if (m > 0) "<white>${m}m ${sec}s" else "<white>${sec}s"
    }
    @PublishedApi internal var onTick: (Int) -> Unit = {}
    @PublishedApi internal var onEnd: () -> Unit = {}
    @PublishedApi internal val milestones = mutableListOf<MilestoneCallback>()

    fun duration(duration: Duration) { durationTicks = (duration.toMillis() / 50).toInt() }
    fun durationTicks(ticks: Int) { durationTicks = ticks }
    fun display(mode: DisplayMode) { displayMode = mode }
    fun displayFormat(format: (Int) -> String) { displayFormat = format }
    fun onTick(handler: (remaining: Int) -> Unit) { onTick = handler }
    fun onEnd(handler: () -> Unit) { onEnd = handler }

    fun onHalf(handler: () -> Unit) {
        milestones.add(MilestoneCallback(durationTicks / 2, { handler() }))
    }

    fun onQuarter(handler: () -> Unit) {
        milestones.add(MilestoneCallback(durationTicks / 4, { handler() }))
    }

    fun milestone(remainingTicks: Int, handler: (Int) -> Unit) {
        milestones.add(MilestoneCallback(remainingTicks, handler))
    }

    fun milestone(remaining: Duration, handler: (Int) -> Unit) {
        milestones.add(MilestoneCallback((remaining.toMillis() / 50).toInt(), handler))
    }

    @PublishedApi internal fun build(): MinigameTimer = MinigameTimer(
        name = name,
        totalDurationTicks = durationTicks,
        displayMode = displayMode,
        displayFormat = displayFormat,
        onTick = onTick,
        onEnd = onEnd,
        milestones = milestones.toList(),
    )
}

inline fun minigameTimer(name: String, block: MinigameTimerBuilder.() -> Unit): MinigameTimer =
    MinigameTimerBuilder(name).apply(block).build()
