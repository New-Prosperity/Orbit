package me.nebula.orbit.utils.matchresult

import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateDefault
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import net.minestom.server.entity.Player
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private val miniMessage = MiniMessage.miniMessage()

data class PlayerStat(val uuid: UUID, val name: String, val value: Double)

data class MatchResult(
    val winner: Pair<UUID, String>?,
    val losers: List<Pair<UUID, String>>,
    val isDraw: Boolean,
    val mvp: Pair<UUID, String>?,
    val stats: Map<String, List<PlayerStat>>,
    val duration: Duration,
    val timestamp: Long,
    val metadata: Map<String, String>,
)

class StatEntryBuilder @PublishedApi internal constructor(private val statName: String) {

    @PublishedApi internal val entries = mutableListOf<PlayerStat>()

    fun player(uuid: UUID, name: String, value: Double) {
        entries.add(PlayerStat(uuid, name, value))
    }

    fun player(player: Player, value: Double) {
        entries.add(PlayerStat(player.uuid, player.username, value))
    }
}

class MatchResultBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var winner: Pair<UUID, String>? = null
    @PublishedApi internal val losers = mutableListOf<Pair<UUID, String>>()
    @PublishedApi internal var isDraw: Boolean = false
    @PublishedApi internal var mvp: Pair<UUID, String>? = null
    @PublishedApi internal val stats = mutableMapOf<String, List<PlayerStat>>()
    @PublishedApi internal var duration: Duration = Duration.ZERO
    @PublishedApi internal val metadata = mutableMapOf<String, String>()

    fun winner(player: Player) { winner = player.uuid to player.username }
    fun winner(uuid: UUID, name: String) { winner = uuid to name }
    fun loser(player: Player) { losers.add(player.uuid to player.username) }
    fun loser(uuid: UUID, name: String) { losers.add(uuid to name) }
    fun losers(players: Collection<Player>) { players.forEach { losers.add(it.uuid to it.username) } }
    fun draw(value: Boolean = true) { isDraw = value }
    fun mvp(player: Player) { mvp = player.uuid to player.username }
    fun mvp(uuid: UUID, name: String) { mvp = uuid to name }
    fun duration(dur: Duration) { duration = dur }
    fun metadata(key: String, value: String) { metadata[key] = value }

    inline fun stat(name: String, block: StatEntryBuilder.() -> Unit) {
        stats[name] = StatEntryBuilder(name).apply(block).entries.toList()
    }

    @PublishedApi internal fun build(): MatchResult = MatchResult(
        winner = winner,
        losers = losers.toList(),
        isDraw = isDraw,
        mvp = mvp,
        stats = stats.toMap(),
        duration = duration,
        timestamp = System.currentTimeMillis(),
        metadata = metadata.toMap(),
    )
}

inline fun matchResult(block: MatchResultBuilder.() -> Unit): MatchResult =
    MatchResultBuilder().apply(block).build()

object MatchResultDisplay {

    fun render(result: MatchResult): List<Component> = buildList {
        add(Component.empty())
        add(Component.text("=".repeat(40), NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH))
        add(Component.empty())

        if (result.isDraw) {
            add(translateDefault("orbit.util.match_result.draw").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
        } else {
            result.winner?.let { (_, name) ->
                add(translateDefault("orbit.util.match_result.winner", "name" to name).color(NamedTextColor.GRAY))
            }
        }

        add(Component.empty())

        result.mvp?.let { (_, name) ->
            add(translateDefault("orbit.util.match_result.mvp", "name" to name).color(NamedTextColor.GRAY))
            add(Component.empty())
        }

        if (result.stats.isNotEmpty()) {
            add(translateDefault("orbit.util.match_result.stats").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))

            result.stats.forEach { (statName, entries) ->
                add(Component.empty())
                add(Component.text("  $statName:", NamedTextColor.YELLOW))
                entries.sortedByDescending { it.value }.take(3).forEachIndexed { index, stat ->
                    val prefix = when (index) {
                        0 -> "    1st"
                        1 -> "    2nd"
                        2 -> "    3rd"
                        else -> "    ${index + 1}th"
                    }
                    val color = when (index) {
                        0 -> NamedTextColor.GOLD
                        1 -> NamedTextColor.GRAY
                        2 -> NamedTextColor.DARK_RED
                        else -> NamedTextColor.WHITE
                    }
                    add(
                        Component.text("$prefix ", color)
                            .append(Component.text(stat.name, NamedTextColor.WHITE))
                            .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(formatValue(stat.value), NamedTextColor.YELLOW))
                    )
                }
            }
        }

        if (result.duration != Duration.ZERO) {
            add(Component.empty())
            val minutes = result.duration.toMinutes()
            val seconds = result.duration.seconds % 60
            add(translateDefault("orbit.util.match_result.duration", "time" to "${minutes}m ${seconds}s").color(NamedTextColor.GRAY))
        }

        add(Component.empty())
        add(Component.text("=".repeat(40), NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH))
    }

    fun sendTo(player: Player, result: MatchResult) {
        render(result).forEach { player.sendMessage(it) }

        if (result.isDraw) {
            player.showTitle(Title.title(
                player.translate("orbit.util.match_result.draw_title").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500)),
            ))
        } else {
            val isWinner = result.winner?.first == player.uuid
            val titleText = if (isWinner) "VICTORY" else "DEFEAT"
            val color = if (isWinner) NamedTextColor.GOLD else NamedTextColor.RED
            val subtitle = result.winner?.second?.let {
                player.translate("orbit.util.match_result.winner_subtitle", "name" to it).color(NamedTextColor.GRAY)
            } ?: Component.empty()

            player.showTitle(Title.title(
                Component.text(titleText, color, TextDecoration.BOLD),
                subtitle,
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500)),
            ))
        }
    }

    fun broadcast(players: Collection<Player>, result: MatchResult) {
        players.forEach { sendTo(it, result) }
    }

    private fun formatValue(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else "%.2f".format(value)
}

object MatchResultManager {

    private val history = CopyOnWriteArrayList<MatchResult>()
    private val playerHistory = ConcurrentHashMap<UUID, MutableList<MatchResult>>()
    private val maxHistory = 100

    fun store(result: MatchResult) {
        history.add(result)
        if (history.size > maxHistory) history.removeAt(0)

        val allPlayers = buildList {
            result.winner?.first?.let { add(it) }
            addAll(result.losers.map { it.first })
            result.mvp?.first?.let { add(it) }
        }.distinct()

        allPlayers.forEach { uuid ->
            playerHistory.getOrPut(uuid) { mutableListOf() }.let { list ->
                list.add(result)
                if (list.size > maxHistory) list.removeAt(0)
            }
        }
    }

    fun storeAndDisplay(result: MatchResult, players: Collection<Player>) {
        store(result)
        MatchResultDisplay.broadcast(players, result)
    }

    fun recentMatches(limit: Int = 10): List<MatchResult> =
        history.takeLast(limit).reversed()

    fun playerMatches(uuid: UUID, limit: Int = 10): List<MatchResult> =
        (playerHistory[uuid] ?: emptyList()).takeLast(limit).reversed()

    fun playerWins(uuid: UUID): Int =
        (playerHistory[uuid] ?: emptyList()).count { it.winner?.first == uuid }

    fun playerLosses(uuid: UUID): Int =
        (playerHistory[uuid] ?: emptyList()).count {
            !it.isDraw && it.winner?.first != uuid && it.losers.any { l -> l.first == uuid }
        }

    fun playerDraws(uuid: UUID): Int =
        (playerHistory[uuid] ?: emptyList()).count { it.isDraw }

    fun playerMvps(uuid: UUID): Int =
        (playerHistory[uuid] ?: emptyList()).count { it.mvp?.first == uuid }

    fun clear() {
        history.clear()
        playerHistory.clear()
    }
}
