package me.nebula.orbit.utils.ceremony

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.matchresult.MatchResult
import me.nebula.orbit.utils.podium.PodiumDisplay
import me.nebula.orbit.utils.podium.podium
import me.nebula.orbit.utils.stattracker.StatTracker
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import java.time.Duration
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class Ceremony @PublishedApi internal constructor(
    private val instance: Instance,
    private val result: MatchResult,
    private val podiumPositions: Map<Int, Pos>,
    private val fireworkInterval: Int,
    private val maxFireworks: Int,
    private val showPersonalStats: Boolean,
    private val personalStatsKeys: List<String>,
    private val personalStatsDelay: Int,
    private val spectateWinner: Boolean,
    private val winnerTitleKey: String?,
    private val loserTitleKey: String?,
    private val drawTitleKey: String?,
    private val winnerSubtitleKey: String?,
    private val loserSubtitleKey: String?,
    private val winnerSoundEvent: SoundEvent,
    private val loserSoundEvent: SoundEvent,
) {

    private var podiumDisplay: PodiumDisplay? = null
    private var fireworkTask: Task? = null
    private val fireworkEntities = mutableListOf<Entity>()
    private var statsTask: Task? = null
    private var fireworkCount = 0

    fun start(players: Collection<Player>) {
        showTitles(players)
        playSounds(players)
        buildPodium(players)

        if (spectateWinner && !result.isDraw) {
            spectateWinner(players)
        }

        if (fireworkInterval > 0) {
            startFireworks()
        }

        if (showPersonalStats && personalStatsKeys.isNotEmpty()) {
            schedulePersonalStats(players)
        }
    }

    fun stop() {
        podiumDisplay?.cleanup()
        podiumDisplay = null
        fireworkTask?.cancel()
        fireworkTask = null
        statsTask?.cancel()
        statsTask = null
        fireworkEntities.forEach { if (!it.isRemoved) it.remove() }
        fireworkEntities.clear()
    }

    private fun showTitles(players: Collection<Player>) {
        val times = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(800))

        for (player in players) {
            val (titleKey, subtitleKey) = when {
                result.isDraw -> drawTitleKey to null
                result.winner?.first == player.uuid -> winnerTitleKey to winnerSubtitleKey
                else -> loserTitleKey to loserSubtitleKey
            }

            val titleComponent = if (titleKey != null) {
                val winnerName = result.winner?.second ?: ""
                player.translate(titleKey, "name" to winnerName)
            } else {
                Component.empty()
            }

            val subtitleComponent = if (subtitleKey != null) {
                val winnerName = result.winner?.second ?: ""
                player.translate(subtitleKey, "name" to winnerName)
            } else {
                Component.empty()
            }

            player.showTitle(Title.title(titleComponent, subtitleComponent, times))
        }
    }

    private fun playSounds(players: Collection<Player>) {
        for (player in players) {
            val event = when {
                result.isDraw -> loserSoundEvent
                result.winner?.first == player.uuid -> winnerSoundEvent
                else -> loserSoundEvent
            }
            player.playSound(Sound.sound(event.key(), Sound.Source.PLAYER, 1f, 1f))
        }
    }

    private fun buildPodium(players: Collection<Player>) {
        if (podiumPositions.isEmpty()) return
        if (result.isDraw) return

        val winnerUuid = result.winner?.first ?: return
        val winnerPlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(winnerUuid) ?: return

        val topKillers = players
            .filter { it.uuid != winnerUuid }
            .sortedByDescending { StatTracker.get(it.uuid, "kills") }

        podiumDisplay = podium(instance) {
            displayDuration(30.seconds)

            podiumPositions[1]?.let { pos -> first(winnerPlayer, pos) }
            podiumPositions[2]?.let { pos ->
                topKillers.getOrNull(0)?.let { second(it, pos) }
            }
            podiumPositions[3]?.let { pos ->
                topKillers.getOrNull(1)?.let { third(it, pos) }
            }
        }
        podiumDisplay!!.show()
    }

    private fun spectateWinner(players: Collection<Player>) {
        val winnerUuid = result.winner?.first ?: return
        val winnerPlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(winnerUuid) ?: return

        for (player in players) {
            if (player.uuid == winnerUuid) continue
            player.spectate(winnerPlayer)
        }
    }

    private fun startFireworks() {
        val winnerUuid = result.winner?.first
        fireworkTask = repeat(fireworkInterval) {
            if (maxFireworks > 0 && fireworkCount >= maxFireworks) {
                fireworkTask?.cancel()
                fireworkTask = null
                return@repeat
            }

            val position = if (winnerUuid != null) {
                MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(winnerUuid)?.position
            } else null

            val pos = position ?: podiumPositions[1] ?: return@repeat

            val offsets = listOf(
                Vec(1.5, 0.0, 0.0), Vec(-1.5, 0.0, 0.0),
                Vec(0.0, 0.0, 1.5), Vec(0.0, 0.0, -1.5),
                Vec(1.0, 0.0, 1.0), Vec(-1.0, 0.0, -1.0),
            )
            val offset = offsets[fireworkCount % offsets.size]

            val entity = Entity(EntityType.FIREWORK_ROCKET)
            entity.velocity = Vec(offset.x() * 2, 25.0 + (Math.random() * 5), offset.z() * 2)
            entity.setInstance(instance, pos.add(offset.x(), 0.0, offset.z()))
            fireworkEntities.add(entity)

            delay(25 + (Math.random() * 10).toInt()) { if (!entity.isRemoved) entity.remove() }

            fireworkCount++
        }
    }

    private fun schedulePersonalStats(players: Collection<Player>) {
        statsTask = delay(personalStatsDelay) {
            for (player in players) {
                if (!player.isOnline) continue
                sendPersonalStats(player)
            }
        }
    }

    private fun sendPersonalStats(player: Player) {
        player.sendMessage(Component.empty())
        player.sendMessage(player.translate("orbit.ceremony.stats_header"))

        for (key in personalStatsKeys) {
            val value = StatTracker.get(player.uuid, key)
            player.sendMessage(player.translate("orbit.ceremony.stat_entry",
                "stat" to key,
                "value" to value.toString(),
            ))
        }

        val placement = result.losers.indexOfFirst { it.first == player.uuid }
        val place = when {
            result.winner?.first == player.uuid -> 1
            placement >= 0 -> placement + 2
            else -> 0
        }
        if (place > 0) {
            player.sendMessage(player.translate("orbit.ceremony.placement",
                "place" to "#$place",
            ))
        }

        player.sendMessage(Component.empty())
    }
}

class CeremonyBuilder @PublishedApi internal constructor(
    @PublishedApi internal val instance: Instance,
    @PublishedApi internal val result: MatchResult,
) {

    @PublishedApi internal val podiumPositions = mutableMapOf<Int, Pos>()
    @PublishedApi internal var fireworkInterval: Int = 15
    @PublishedApi internal var maxFireworks: Int = 20
    @PublishedApi internal var showPersonalStats: Boolean = true
    @PublishedApi internal val personalStatsKeys = mutableListOf<String>()
    @PublishedApi internal var personalStatsDelay: Int = 40
    @PublishedApi internal var spectateWinner: Boolean = true
    @PublishedApi internal var winnerTitleKey: String? = "orbit.ceremony.title.winner"
    @PublishedApi internal var loserTitleKey: String? = "orbit.ceremony.title.loser"
    @PublishedApi internal var drawTitleKey: String? = "orbit.ceremony.title.draw"
    @PublishedApi internal var winnerSubtitleKey: String? = "orbit.ceremony.subtitle.winner"
    @PublishedApi internal var loserSubtitleKey: String? = "orbit.ceremony.subtitle.loser"
    @PublishedApi internal var winnerSoundEvent: SoundEvent = SoundEvent.UI_TOAST_CHALLENGE_COMPLETE
    @PublishedApi internal var loserSoundEvent: SoundEvent = SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP

    fun podiumPosition(place: Int, position: Pos) {
        require(place in 1..3) { "Podium place must be 1-3" }
        podiumPositions[place] = position
    }

    fun fireworks(interval: Int = 15, max: Int = 20) {
        fireworkInterval = interval
        maxFireworks = max
    }

    fun noFireworks() {
        fireworkInterval = 0
    }

    fun personalStats(vararg keys: String) {
        personalStatsKeys.clear()
        personalStatsKeys.addAll(keys)
    }

    fun personalStatsDelay(ticks: Int) {
        personalStatsDelay = ticks
    }

    fun noPersonalStats() {
        showPersonalStats = false
    }

    fun spectateWinner(enabled: Boolean = true) {
        spectateWinner = enabled
    }

    fun winnerTitle(titleKey: String, subtitleKey: String? = null) {
        winnerTitleKey = titleKey
        winnerSubtitleKey = subtitleKey
    }

    fun loserTitle(titleKey: String, subtitleKey: String? = null) {
        loserTitleKey = titleKey
        loserSubtitleKey = subtitleKey
    }

    fun drawTitle(titleKey: String) {
        drawTitleKey = titleKey
    }

    fun winnerSound(event: SoundEvent) { winnerSoundEvent = event }
    fun loserSound(event: SoundEvent) { loserSoundEvent = event }

    @PublishedApi internal fun build(): Ceremony = Ceremony(
        instance = instance,
        result = result,
        podiumPositions = podiumPositions.toMap(),
        fireworkInterval = fireworkInterval,
        maxFireworks = maxFireworks,
        showPersonalStats = showPersonalStats,
        personalStatsKeys = personalStatsKeys.toList(),
        personalStatsDelay = personalStatsDelay,
        spectateWinner = spectateWinner,
        winnerTitleKey = winnerTitleKey,
        loserTitleKey = loserTitleKey,
        drawTitleKey = drawTitleKey,
        winnerSubtitleKey = winnerSubtitleKey,
        loserSubtitleKey = loserSubtitleKey,
        winnerSoundEvent = winnerSoundEvent,
        loserSoundEvent = loserSoundEvent,
    )
}

inline fun ceremony(
    instance: Instance,
    result: MatchResult,
    block: CeremonyBuilder.() -> Unit,
): Ceremony = CeremonyBuilder(instance, result).apply(block).build()
