package me.nebula.orbit.utils.botai

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.utils.fakeplayer.BOT_TAG
import me.nebula.orbit.utils.fakeplayer.BotProfile
import me.nebula.orbit.utils.fakeplayer.FakePlayerManager
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import net.minestom.server.entity.GameMode as MinecraftGameMode

data class FillerConfig(
    val targetPlayerCount: Int,
    val minRealPlayers: Int = 1,
    val fillDelay: Duration = 10.seconds,
    val staggerInterval: Duration = 3.seconds,
    val skillRange: ClosedFloatingPointRange<Float> = 0.3f..0.7f,
    val preset: String = "survival",
    val removeOnRealJoin: Boolean = true,
)

private data class FilledGameState(
    val gameId: String,
    val gameMode: GameMode,
    val config: FillerConfig,
    val fillerBots: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),
    var fillTask: Task? = null,
    var delayTask: Task? = null,
    var spawnIndex: Int = 0,
    var eventNode: EventNode<*>? = null,
)

object BotLobbyFiller {

    private val logger = logger("BotLobbyFiller")
    private val filledGames = ConcurrentHashMap<String, FilledGameState>()

    fun startFilling(gameId: String, gameMode: GameMode, config: FillerConfig) {
        stopFilling(gameId)
        val state = FilledGameState(gameId, gameMode, config)
        filledGames[gameId] = state

        logger.info { "Filling $gameId to ${config.targetPlayerCount} players" }

        installEventNode(state)

        val realCount = countRealPlayers(gameMode)
        if (realCount >= config.minRealPlayers) {
            scheduleFillDelay(gameId, state)
        }
    }

    fun stopFilling(gameId: String) {
        val state = filledGames.remove(gameId) ?: return
        state.fillTask?.cancel()
        state.delayTask?.cancel()
        uninstallEventNode(state)
        val count = state.fillerBots.size
        state.fillerBots.toList().forEach { uuid ->
            BotAI.detach(FakePlayerManager.get(uuid) ?: return@forEach)
            FakePlayerManager.remove(uuid)
        }
        state.fillerBots.clear()
        logger.info { "Cleaned up $count filler bots for $gameId" }
    }

    fun onRealPlayerJoin(gameId: String, player: Player) {
        val state = filledGames[gameId] ?: return
        val realCount = countRealPlayers(state.gameMode)

        if (realCount >= state.config.minRealPlayers && state.fillTask == null && state.delayTask == null) {
            scheduleFillDelay(gameId, state)
        }

        if (!state.config.removeOnRealJoin) return
        val totalPlayers = state.gameMode.activeInstance.players.size
        if (totalPlayers <= state.config.targetPlayerCount) return

        val worstBot = state.fillerBots
            .mapNotNull { uuid -> FakePlayerManager.get(uuid)?.let { uuid to it } }
            .minByOrNull { (uuid, _) -> BotAI.skillLevels[uuid]?.aimAccuracy ?: 0f }
        if (worstBot != null) {
            val (uuid, bot) = worstBot
            state.fillerBots.remove(uuid)
            BotAI.detach(bot)
            FakePlayerManager.remove(uuid)
            logger.info { "Removed filler ${bot.username} for real player ${player.username}" }
        }
    }

    fun onRealPlayerLeave(gameId: String, player: Player) {
        val state = filledGames[gameId] ?: return
        if (state.gameMode.phase != GamePhase.WAITING && state.gameMode.phase != GamePhase.STARTING) return
        val totalPlayers = state.gameMode.activeInstance.players.size
        if (totalPlayers < state.config.targetPlayerCount && state.fillerBots.size < state.config.targetPlayerCount) {
            spawnOneBot(state)
        }
    }

    fun getFillerBots(gameId: String): Set<UUID> =
        filledGames[gameId]?.fillerBots?.toSet() ?: emptySet()

    fun isFillerBot(uuid: UUID): Boolean =
        filledGames.values.any { uuid in it.fillerBots }

    fun getStatus(gameId: String): String? {
        val state = filledGames[gameId] ?: return null
        val bots = state.fillerBots.size
        val target = state.config.targetPlayerCount
        val total = state.gameMode.activeInstance.players.size
        return "Filler: $bots bots, $total/$target total players, preset=${state.config.preset}, skill=${state.config.skillRange}"
    }

    fun stopAll() {
        filledGames.keys.toList().forEach { stopFilling(it) }
    }

    fun activeGameIds(): Set<String> = filledGames.keys.toSet()

    private fun installEventNode(state: FilledGameState) {
        val node = EventNode.all("filler-${state.gameId}")

        node.addListener(PlayerSpawnEvent::class.java) { event ->
            if (!event.isFirstSpawn) return@addListener
            val player = event.player
            if (player.getTag(BOT_TAG)) return@addListener
            if (player.instance != state.gameMode.activeInstance) return@addListener
            onRealPlayerJoin(state.gameId, player)
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            val player = event.player
            if (player.getTag(BOT_TAG)) return@addListener
            if (player.instance != state.gameMode.activeInstance) return@addListener
            onRealPlayerLeave(state.gameId, player)
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        state.eventNode = node
    }

    private fun uninstallEventNode(state: FilledGameState) {
        val node = state.eventNode ?: return
        MinecraftServer.getGlobalEventHandler().removeChild(node)
        state.eventNode = null
    }

    private fun scheduleFillDelay(gameId: String, state: FilledGameState) {
        val delayTicks = (state.config.fillDelay.inWholeMilliseconds / 50).toInt().coerceAtLeast(1)
        state.delayTask = delay(delayTicks) {
            state.delayTask = null
            beginStaggeredSpawning(gameId, state)
        }
    }

    private fun beginStaggeredSpawning(gameId: String, state: FilledGameState) {
        val intervalTicks = (state.config.staggerInterval.inWholeMilliseconds / 50).toInt().coerceAtLeast(1)
        state.fillTask = repeat(intervalTicks) {
            if (!filledGames.containsKey(gameId)) {
                state.fillTask?.cancel()
                return@repeat
            }
            val totalPlayers = state.gameMode.activeInstance.players.size
            if (totalPlayers >= state.config.targetPlayerCount) {
                state.fillTask?.cancel()
                state.fillTask = null
                return@repeat
            }
            if (state.gameMode.phase != GamePhase.WAITING && state.gameMode.phase != GamePhase.STARTING) {
                state.fillTask?.cancel()
                state.fillTask = null
                return@repeat
            }
            spawnOneBot(state)
        }
    }

    private fun spawnOneBot(state: FilledGameState) {
        val config = state.config
        val skillRating = Random.nextFloat() * (config.skillRange.endInclusive - config.skillRange.start) + config.skillRange.start
        val skill = BotSkillLevels.forRating(skillRating)
        val personality = BotPersonalities.random()

        val instance = state.gameMode.activeInstance
        val spawnPos = state.gameMode.activeSpawnPoint

        FakePlayerManager.spawn(
            instance = instance,
            spawnPos = spawnPos,
            count = 1,
            profile = BotProfile(
                gameMode = MinecraftGameMode.SURVIVAL,
                onReady = { player ->
                    attachPreset(player, config.preset, personality, skill)
                    state.fillerBots.add(player.uuid)
                    logger.debug { "Spawned filler bot ${player.username} (skill=${skill.aimAccuracy})" }
                },
            ),
            staggerDelayTicks = 2,
        )
    }

    private fun attachPreset(player: Player, preset: String, personality: BotPersonality, skill: BotSkillLevel) {
        when (preset.lowercase()) {
            "survival" -> BotAI.attachSurvivalAI(player, personality, skill)
            "combat" -> BotAI.attachCombatAI(player, personality, skill)
            "pvp" -> BotAI.attachPvPAI(player, personality, skill)
            "miner" -> BotAI.attachMinerAI(player, personality, skill)
            "gatherer" -> BotAI.attachGathererAI(player, personality, skill)
            "passive" -> BotAI.attachPassiveAI(player, personality, skill)
            else -> BotAI.attachSurvivalAI(player, personality, skill)
        }
    }

    private fun countRealPlayers(gameMode: GameMode): Int =
        gameMode.activeInstance.players.count { !it.getTag(BOT_TAG) }
}
