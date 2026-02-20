package me.nebula.orbit.utils.scoreboard

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val miniMessage = MiniMessage.miniMessage()

sealed interface ScoreboardLine {

    data class Static(val content: String) : ScoreboardLine

    data class Animated(val frames: List<String>) : ScoreboardLine

    data class Dynamic(val provider: () -> String) : ScoreboardLine
}

class ManagedScoreboard @PublishedApi internal constructor(
    private val sidebar: Sidebar,
    private val lineIds: List<String>,
) {

    fun show(player: Player) = sidebar.addViewer(player)

    fun hide(player: Player) = sidebar.removeViewer(player)

    fun updateTitle(title: String) {
        sidebar.setTitle(miniMessage.deserialize(title))
    }

    fun updateLine(index: Int, content: String) {
        require(index in lineIds.indices) { "Line index $index out of bounds (0..${lineIds.lastIndex})" }
        sidebar.updateLineContent(lineIds[index], miniMessage.deserialize(content))
    }
}

class ScoreboardBuilder @PublishedApi internal constructor(private val title: String) {

    @PublishedApi internal val lines = mutableListOf<ScoreboardLine>()

    fun line(content: String) {
        lines += ScoreboardLine.Static(content)
    }

    fun animatedLine(frames: List<String>) {
        lines += ScoreboardLine.Animated(frames)
    }

    fun dynamicLine(provider: () -> String) {
        lines += ScoreboardLine.Dynamic(provider)
    }

    @PublishedApi internal fun build(): ManagedScoreboard {
        val sidebar = Sidebar(miniMessage.deserialize(title))
        val ids = lines.mapIndexed { index, line ->
            val id = "line_$index"
            val content = when (line) {
                is ScoreboardLine.Static -> line.content
                is ScoreboardLine.Animated -> line.frames.firstOrNull() ?: ""
                is ScoreboardLine.Dynamic -> line.provider()
            }
            sidebar.createLine(Sidebar.ScoreboardLine(id, miniMessage.deserialize(content), lines.size - index))
            id
        }
        return ManagedScoreboard(sidebar, ids)
    }
}

inline fun scoreboard(title: String, block: ScoreboardBuilder.() -> Unit): ManagedScoreboard =
    ScoreboardBuilder(title).apply(block).build()

fun Player.showScoreboard(scoreboard: ManagedScoreboard) = scoreboard.show(this)
fun Player.hideScoreboard(scoreboard: ManagedScoreboard) = scoreboard.hide(this)
fun Player.updateScoreboard(scoreboard: ManagedScoreboard, index: Int, content: String) = scoreboard.updateLine(index, content)

class PerPlayerScoreboard @PublishedApi internal constructor(
    private val titleTemplate: String,
    private val lineTemplates: List<String>,
) {

    private val sidebars = ConcurrentHashMap<UUID, Sidebar>()
    private val lineIds = lineTemplates.indices.map { "line_$it" }

    fun show(player: Player, placeholders: Map<String, String> = emptyMap()) {
        val resolved = resolvePlaceholders(titleTemplate, placeholders)
        val sidebar = Sidebar(miniMessage.deserialize(resolved))

        lineTemplates.forEachIndexed { index, template ->
            val content = resolvePlaceholders(template, placeholders)
            sidebar.createLine(Sidebar.ScoreboardLine(lineIds[index], miniMessage.deserialize(content), lineTemplates.size - index))
        }

        sidebar.addViewer(player)
        sidebars[player.uuid] = sidebar
    }

    fun update(player: Player, placeholders: Map<String, String>) {
        val sidebar = sidebars[player.uuid] ?: return
        lineTemplates.forEachIndexed { index, template ->
            val content = resolvePlaceholders(template, placeholders)
            sidebar.updateLineContent(lineIds[index], miniMessage.deserialize(content))
        }
    }

    fun hide(player: Player) {
        sidebars.remove(player.uuid)?.removeViewer(player)
    }

    fun hideAll() {
        sidebars.values.forEach { sb -> sb.viewers.toList().forEach(sb::removeViewer) }
        sidebars.clear()
    }

    private fun resolvePlaceholders(template: String, placeholders: Map<String, String>): String {
        var result = template
        for ((key, value) in placeholders) {
            result = result.replace("{$key}", value)
        }
        return result
    }
}

class PerPlayerScoreboardBuilder @PublishedApi internal constructor(private val title: String) {

    @PublishedApi internal val lines = mutableListOf<String>()

    fun line(content: String) {
        lines += content
    }
}

inline fun perPlayerScoreboard(title: String, block: PerPlayerScoreboardBuilder.() -> Unit): PerPlayerScoreboard {
    val builder = PerPlayerScoreboardBuilder(title).apply(block)
    return PerPlayerScoreboard(title, builder.lines.toList())
}

class AnimatedScoreboard(
    private val title: String,
    private val frameIntervalTicks: Int = 20,
) {

    private val lines = mutableMapOf<Int, MutableList<String>>()
    private var currentFrame = 0
    private var sidebar: Sidebar? = null
    private var tickTask: Task? = null
    private val viewers = mutableSetOf<Player>()

    fun addFrames(line: Int, frames: List<String>) {
        lines.getOrPut(line) { mutableListOf() }.addAll(frames)
    }

    fun addStaticLine(line: Int, text: String) {
        lines[line] = mutableListOf(text)
    }

    fun show(player: Player) {
        if (sidebar == null) start()
        sidebar?.addViewer(player)
        viewers.add(player)
    }

    fun hide(player: Player) {
        sidebar?.removeViewer(player)
        viewers.remove(player)
    }

    fun destroy() {
        tickTask?.cancel()
        tickTask = null
        viewers.forEach { sidebar?.removeViewer(it) }
        viewers.clear()
        sidebar = null
    }

    private fun start() {
        val sb = Sidebar(miniMessage.deserialize(title))
        lines.keys.sorted().forEachIndexed { index, line ->
            val frames = lines[line] ?: return@forEachIndexed
            val text = frames.first()
            sb.createLine(Sidebar.ScoreboardLine(
                "line-$line",
                miniMessage.deserialize(text),
                lines.size - index,
            ))
        }
        sidebar = sb

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::advanceFrame)
            .repeat(TaskSchedule.tick(frameIntervalTicks))
            .schedule()
    }

    private fun advanceFrame() {
        currentFrame++
        val sb = sidebar ?: return
        lines.forEach { (line, frames) ->
            if (frames.size <= 1) return@forEach
            val frameIndex = currentFrame % frames.size
            sb.updateLineContent("line-$line", miniMessage.deserialize(frames[frameIndex]))
        }
    }
}

class AnimatedScoreboardBuilder @PublishedApi internal constructor(
    private val title: String,
    private val intervalTicks: Int,
) {
    @PublishedApi internal val scoreboard = AnimatedScoreboard(title, intervalTicks)

    fun line(index: Int, text: String) = scoreboard.addStaticLine(index, text)
    fun animatedLine(index: Int, frames: List<String>) = scoreboard.addFrames(index, frames)

    @PublishedApi internal fun build(): AnimatedScoreboard = scoreboard
}

inline fun animatedScoreboard(title: String, intervalTicks: Int = 20, block: AnimatedScoreboardBuilder.() -> Unit): AnimatedScoreboard =
    AnimatedScoreboardBuilder(title, intervalTicks).apply(block).build()

class TeamScoreboard @PublishedApi internal constructor(
    val teamName: String,
    private val titleTemplate: String,
    private val lineProviders: List<() -> String>,
) {

    private val viewers = ConcurrentHashMap.newKeySet<UUID>()
    private val sidebars = ConcurrentHashMap<UUID, Sidebar>()
    private val lineIds = lineProviders.indices.map { "line_$it" }

    fun show(player: Player) {
        viewers.add(player.uuid)
        val sidebar = buildSidebar()
        sidebar.addViewer(player)
        sidebars[player.uuid] = sidebar
    }

    fun hide(player: Player) {
        viewers.remove(player.uuid)
        sidebars.remove(player.uuid)?.removeViewer(player)
    }

    fun update(player: Player) {
        val sidebar = sidebars[player.uuid] ?: return
        lineProviders.forEachIndexed { index, provider ->
            sidebar.updateLineContent(lineIds[index], miniMessage.deserialize(provider()))
        }
    }

    fun updateAll(players: Collection<Player>) {
        players.filter { viewers.contains(it.uuid) }.forEach { update(it) }
    }

    fun hideAll() {
        sidebars.values.forEach { sb -> sb.viewers.toList().forEach(sb::removeViewer) }
        sidebars.clear()
        viewers.clear()
    }

    fun isViewing(player: Player): Boolean = viewers.contains(player.uuid)

    private fun buildSidebar(): Sidebar {
        val sidebar = Sidebar(miniMessage.deserialize(titleTemplate))
        lineProviders.forEachIndexed { index, provider ->
            sidebar.createLine(
                Sidebar.ScoreboardLine(
                    lineIds[index],
                    miniMessage.deserialize(provider()),
                    lineProviders.size - index,
                )
            )
        }
        return sidebar
    }
}

class TeamScoreboardBuilder @PublishedApi internal constructor(private val teamName: String) {

    @PublishedApi internal var title: String = teamName
    @PublishedApi internal val lineProviders = mutableListOf<() -> String>()

    fun title(text: String) { title = text }

    fun line(provider: () -> String) {
        lineProviders.add(provider)
    }

    fun staticLine(text: String) {
        lineProviders.add { text }
    }

    @PublishedApi internal fun build(): TeamScoreboard = TeamScoreboard(
        teamName = teamName,
        titleTemplate = title,
        lineProviders = lineProviders.toList(),
    )
}

inline fun teamScoreboard(teamName: String, block: TeamScoreboardBuilder.() -> Unit): TeamScoreboard =
    TeamScoreboardBuilder(teamName).apply(block).build()

object TeamScoreboardManager {

    private val scoreboards = ConcurrentHashMap<String, TeamScoreboard>()
    private val playerTeams = ConcurrentHashMap<UUID, String>()

    fun register(scoreboard: TeamScoreboard) {
        require(!scoreboards.containsKey(scoreboard.teamName)) { "TeamScoreboard '${scoreboard.teamName}' already registered" }
        scoreboards[scoreboard.teamName] = scoreboard
    }

    fun unregister(teamName: String) {
        scoreboards.remove(teamName)?.hideAll()
    }

    operator fun get(teamName: String): TeamScoreboard? = scoreboards[teamName]
    fun require(teamName: String): TeamScoreboard = requireNotNull(scoreboards[teamName]) { "TeamScoreboard '$teamName' not found" }
    fun all(): Map<String, TeamScoreboard> = scoreboards.toMap()

    fun assignPlayer(player: Player, teamName: String) {
        val previous = playerTeams.put(player.uuid, teamName)
        if (previous != null && previous != teamName) {
            scoreboards[previous]?.hide(player)
        }
        scoreboards[teamName]?.show(player)
    }

    fun removePlayer(player: Player) {
        val teamName = playerTeams.remove(player.uuid) ?: return
        scoreboards[teamName]?.hide(player)
    }

    fun playerTeam(player: Player): String? = playerTeams[player.uuid]

    fun updateAll() {
        scoreboards.values.forEach { sb ->
            val viewers = MinecraftServer.getConnectionManager().onlinePlayers
                .filter { playerTeams[it.uuid] == sb.teamName }
            sb.updateAll(viewers)
        }
    }

    fun clear() {
        scoreboards.values.forEach { it.hideAll() }
        scoreboards.clear()
        playerTeams.clear()
    }
}

enum class ObjectiveDisplay { SIDEBAR, BELOW_NAME, TAB_LIST }

data class ObjectiveConfig(
    val name: String,
    val displayName: Component,
    val displays: Set<ObjectiveDisplay>,
)

object ObjectiveTracker {

    private val scores = ConcurrentHashMap<String, ConcurrentHashMap<UUID, Int>>()
    private val configs = ConcurrentHashMap<String, ObjectiveConfig>()
    private val sidebarCache = ConcurrentHashMap<String, ConcurrentHashMap<UUID, Sidebar>>()

    fun register(config: ObjectiveConfig) {
        configs[config.name] = config
        scores.putIfAbsent(config.name, ConcurrentHashMap())
    }

    fun addScore(player: Player, objective: String, amount: Int = 1) {
        val objectiveScores = scores[objective] ?: return
        objectiveScores.merge(player.uuid, amount) { old, new -> old + new }
        updateDisplay(player, objective)
    }

    fun setScore(player: Player, objective: String, value: Int) {
        val objectiveScores = scores[objective] ?: return
        objectiveScores[player.uuid] = value
        updateDisplay(player, objective)
    }

    fun getScore(player: Player, objective: String): Int =
        scores[objective]?.get(player.uuid) ?: 0

    fun getScore(uuid: UUID, objective: String): Int =
        scores[objective]?.get(uuid) ?: 0

    fun resetScore(player: Player, objective: String) {
        scores[objective]?.remove(player.uuid)
        updateDisplay(player, objective)
    }

    fun resetAll(objective: String) {
        scores[objective]?.clear()
        sidebarCache[objective]?.values?.forEach { sidebar ->
            sidebar.viewers.toList().forEach { sidebar.removeViewer(it) }
        }
        sidebarCache.remove(objective)
    }

    fun leaderboard(objective: String, limit: Int = 10): List<Pair<UUID, Int>> =
        scores[objective]
            ?.entries
            ?.sortedByDescending { it.value }
            ?.take(limit)
            ?.map { it.key to it.value }
            ?: emptyList()

    fun allScores(objective: String): Map<UUID, Int> =
        scores[objective]?.toMap() ?: emptyMap()

    fun rank(uuid: UUID, objective: String): Int {
        val objectiveScores = scores[objective] ?: return -1
        val playerScore = objectiveScores[uuid] ?: return -1
        return objectiveScores.values.count { it > playerScore } + 1
    }

    fun showSidebar(player: Player, objective: String) {
        val config = configs[objective] ?: return
        if (ObjectiveDisplay.SIDEBAR !in config.displays) return
        val sidebar = buildSidebar(objective, config)
        sidebarCache.getOrPut(objective) { ConcurrentHashMap() }[player.uuid] = sidebar
        sidebar.addViewer(player)
    }

    fun hideSidebar(player: Player, objective: String) {
        sidebarCache[objective]?.remove(player.uuid)?.removeViewer(player)
    }

    private fun buildSidebar(objective: String, config: ObjectiveConfig): Sidebar {
        val sidebar = Sidebar(config.displayName)
        val entries = leaderboard(objective, 15)
        entries.forEachIndexed { index, (uuid, score) ->
            val name = MinecraftServer.getConnectionManager().onlinePlayers
                .firstOrNull { it.uuid == uuid }?.username ?: uuid.toString().take(8)
            sidebar.createLine(
                Sidebar.ScoreboardLine(
                    "obj_$index",
                    miniMessage.deserialize("<gray>$name: <white>$score"),
                    entries.size - index,
                )
            )
        }
        return sidebar
    }

    private fun updateDisplay(player: Player, objective: String) {
        val config = configs[objective] ?: return
        if (ObjectiveDisplay.SIDEBAR in config.displays) {
            sidebarCache[objective]?.forEach { (uuid, sidebar) ->
                val viewer = MinecraftServer.getConnectionManager().onlinePlayers
                    .firstOrNull { it.uuid == uuid } ?: return@forEach
                sidebar.removeViewer(viewer)
                val newSidebar = buildSidebar(objective, config)
                sidebarCache[objective]?.put(uuid, newSidebar)
                newSidebar.addViewer(viewer)
            }
        }

        if (ObjectiveDisplay.BELOW_NAME in config.displays) {
            val score = getScore(player, objective)
            player.customName = miniMessage.deserialize(
                "${player.username} <gray>[<white>$score<gray>]"
            )
            player.isCustomNameVisible = true
        }

        if (ObjectiveDisplay.TAB_LIST in config.displays) {
            val score = getScore(player, objective)
            player.sendPlayerListHeaderAndFooter(
                Component.empty(),
                miniMessage.deserialize("<gray>${config.name}: <white>$score"),
            )
        }
    }
}

class ObjectiveBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var displayName: String = name
    @PublishedApi internal val displays = mutableSetOf<ObjectiveDisplay>()

    fun displayName(name: String) { displayName = name }
    fun sidebar() { displays.add(ObjectiveDisplay.SIDEBAR) }
    fun belowName() { displays.add(ObjectiveDisplay.BELOW_NAME) }
    fun tabList() { displays.add(ObjectiveDisplay.TAB_LIST) }

    @PublishedApi internal fun build(): ObjectiveConfig = ObjectiveConfig(
        name = name,
        displayName = miniMessage.deserialize(displayName),
        displays = displays.toSet(),
    )
}

inline fun objective(name: String, block: ObjectiveBuilder.() -> Unit): ObjectiveConfig {
    val config = ObjectiveBuilder(name).apply(block).build()
    ObjectiveTracker.register(config)
    return config
}

sealed interface LiveLine {

    data class Static(val content: String) : LiveLine

    data class Dynamic(val provider: (Player) -> String) : LiveLine
}

class LiveScoreboard @PublishedApi internal constructor(
    private val titleProvider: (Player) -> String,
    private val lines: List<LiveLine>,
    refreshInterval: Duration,
) {

    private val sidebars = ConcurrentHashMap<UUID, Sidebar>()
    private val lineIds = lines.indices.map { "line_$it" }
    private val eventNode = EventNode.all("live-scoreboard-${System.nanoTime()}")
    private val refreshTask: Task

    init {
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (event.isFirstSpawn) show(event.player)
        }
        eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            hide(event.player)
        }
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        refreshTask = MinecraftServer.getSchedulerManager()
            .buildTask(::refreshAll)
            .repeat(TaskSchedule.millis(refreshInterval.inWholeMilliseconds))
            .schedule()
    }

    fun show(player: Player) {
        sidebars.remove(player.uuid)?.removeViewer(player)
        val sidebar = Sidebar(miniMessage.deserialize(titleProvider(player)))
        lines.forEachIndexed { index, line ->
            val content = when (line) {
                is LiveLine.Static -> line.content
                is LiveLine.Dynamic -> line.provider(player)
            }
            sidebar.createLine(
                Sidebar.ScoreboardLine(lineIds[index], miniMessage.deserialize(content), lines.size - index)
            )
        }
        sidebar.addViewer(player)
        sidebars[player.uuid] = sidebar
    }

    fun hide(player: Player) {
        sidebars.remove(player.uuid)?.removeViewer(player)
    }

    fun refresh(player: Player) {
        val sidebar = sidebars[player.uuid] ?: return
        sidebar.setTitle(miniMessage.deserialize(titleProvider(player)))
        lines.forEachIndexed { index, line ->
            if (line is LiveLine.Dynamic) {
                sidebar.updateLineContent(lineIds[index], miniMessage.deserialize(line.provider(player)))
            }
        }
    }

    fun refreshAll() {
        for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
            if (sidebars.containsKey(player.uuid)) refresh(player)
        }
    }

    fun uninstall() {
        refreshTask.cancel()
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
        sidebars.values.forEach { sb -> sb.viewers.toList().forEach(sb::removeViewer) }
        sidebars.clear()
    }
}

class LiveScoreboardBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var titleProvider: (Player) -> String = { "" }
    @PublishedApi internal val lines = mutableListOf<LiveLine>()
    @PublishedApi internal var refreshInterval: Duration = 5.seconds

    fun title(text: String) { titleProvider = { text } }

    fun title(provider: (Player) -> String) { titleProvider = provider }

    fun line(content: String) { lines += LiveLine.Static(content) }

    fun line(provider: (Player) -> String) { lines += LiveLine.Dynamic(provider) }

    fun refreshEvery(duration: Duration) { refreshInterval = duration }
}

inline fun liveScoreboard(block: LiveScoreboardBuilder.() -> Unit): LiveScoreboard {
    val builder = LiveScoreboardBuilder().apply(block)
    return LiveScoreboard(builder.titleProvider, builder.lines.toList(), builder.refreshInterval)
}
