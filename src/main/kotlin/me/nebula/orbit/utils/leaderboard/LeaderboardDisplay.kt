package me.nebula.orbit.utils.leaderboard

import me.nebula.gravity.ranking.Periodicity
import me.nebula.gravity.ranking.RankedPlayer
import me.nebula.gravity.ranking.RankingStore
import me.nebula.gravity.ranking.rankingKey
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import java.util.UUID

data class LeaderboardColumn(
    val statKey: String,
    val displayKey: String,
    val material: Material = Material.DIAMOND,
)

class LeaderboardDisplay @PublishedApi internal constructor(
    private val columns: List<LeaderboardColumn>,
    private val defaultPeriodicity: Periodicity,
    private val entriesPerPage: Int,
    private val guiTitleKey: String,
) {

    fun query(statKey: String, periodicity: Periodicity = defaultPeriodicity): List<RankedPlayer> =
        RankingStore.load(rankingKey(statKey, periodicity)) ?: emptyList()

    fun sendChat(player: Player, statKey: String, periodicity: Periodicity = defaultPeriodicity, limit: Int = 10) {
        val entries = query(statKey, periodicity)

        player.sendMessage(Component.empty())
        player.sendMessage(
            player.translate("orbit.leaderboard.header", "stat" to statKey, "period" to periodicity.name)
        )

        if (entries.isEmpty()) {
            player.sendMessage(player.translate("orbit.leaderboard.empty"))
        } else {
            for (entry in entries.take(limit)) {
                val rank = entry.position + 1
                val color = when (rank) {
                    1 -> NamedTextColor.GOLD
                    2 -> NamedTextColor.GRAY
                    3 -> NamedTextColor.DARK_RED
                    else -> NamedTextColor.WHITE
                }
                player.sendMessage(
                    Component.text("#$rank ", color)
                        .append(Component.text(entry.name, NamedTextColor.WHITE))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(formatScore(entry.score), NamedTextColor.YELLOW))
                )
            }
        }
        player.sendMessage(Component.empty())
    }

    fun openGui(player: Player, statKey: String, periodicity: Periodicity = defaultPeriodicity) {
        val entries = query(statKey, periodicity)

        val gui = paginatedGui(title = "<gold>Leaderboard - $statKey", rows = 6) {
            border(Material.BLACK_STAINED_GLASS_PANE)

            for (entry in entries) {
                val rank = entry.position + 1
                val headMaterial = when (rank) {
                    1 -> Material.GOLD_BLOCK
                    2 -> Material.IRON_BLOCK
                    3 -> Material.COPPER_BLOCK
                    else -> Material.PLAYER_HEAD
                }

                item(itemStack(headMaterial) {
                    name("<white>${entry.name}")
                    lore("<gray>Rank: <white>#$rank")
                    lore("<gray>Score: <yellow>${formatScore(entry.score)}")
                    clean()
                })
            }

            for ((colIndex, col) in columns.withIndex()) {
                if (col.statKey == statKey) continue
                val slotIndex = 49 - columns.size + colIndex + 1
                if (slotIndex !in 45..53) continue

                staticSlot(slotIndex, itemStack(col.material) {
                    name("<yellow>${col.statKey}")
                    clean()
                }) { p -> openGui(p, col.statKey, periodicity) }
            }
        }

        gui.open(player)
    }

    fun openCategorySelector(player: Player, periodicity: Periodicity = defaultPeriodicity) {
        val gui = paginatedGui(title = "<gold>Leaderboards", rows = 3) {
            border(Material.BLACK_STAINED_GLASS_PANE)

            for (col in columns) {
                val entries = query(col.statKey, periodicity)
                val topName = entries.firstOrNull()?.name ?: "---"
                val topScore = entries.firstOrNull()?.let { formatScore(it.score) } ?: "0"

                item(itemStack(col.material) {
                    name("<gold>${col.statKey}")
                    lore("<gray>Top: <gold>$topName <dark_gray>(<yellow>$topScore<dark_gray>)")
                    emptyLoreLine()
                    lore("<yellow>Click to view")
                    clean()
                }) { p -> openGui(p, col.statKey, periodicity) }
            }
        }

        gui.open(player)
    }

    fun playerRank(playerUuid: UUID, statKey: String, periodicity: Periodicity = defaultPeriodicity): RankedPlayer? =
        query(statKey, periodicity).firstOrNull { it.uuid == playerUuid }
}

class LeaderboardDisplayBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val columns = mutableListOf<LeaderboardColumn>()
    @PublishedApi internal var defaultPeriodicity: Periodicity = Periodicity.ALL_TIME
    @PublishedApi internal var entriesPerPage: Int = 28
    @PublishedApi internal var guiTitleKey: String = "orbit.leaderboard.title"

    fun column(statKey: String, displayKey: String, material: Material = Material.DIAMOND) {
        columns += LeaderboardColumn(statKey, displayKey, material)
    }

    fun defaultPeriod(periodicity: Periodicity) {
        defaultPeriodicity = periodicity
    }

    fun entriesPerPage(count: Int) {
        entriesPerPage = count
    }

    fun guiTitle(key: String) {
        guiTitleKey = key
    }

    @PublishedApi internal fun build(): LeaderboardDisplay = LeaderboardDisplay(
        columns = columns.toList(),
        defaultPeriodicity = defaultPeriodicity,
        entriesPerPage = entriesPerPage,
        guiTitleKey = guiTitleKey,
    )
}

inline fun leaderboardDisplay(block: LeaderboardDisplayBuilder.() -> Unit): LeaderboardDisplay =
    LeaderboardDisplayBuilder().apply(block).build()

private fun formatScore(score: Double): String =
    if (score == score.toLong().toDouble()) score.toLong().toString()
    else "%.2f".format(score)
