package me.nebula.orbit.mode.game

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.reconnection.ReconnectionData
import me.nebula.gravity.reconnection.ReconnectionStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.ServerMode
import me.nebula.orbit.mode.config.PlaceholderResolver
import me.nebula.orbit.utils.anvilloader.AnvilWorldLoader
import me.nebula.orbit.utils.countdown.Countdown
import me.nebula.orbit.utils.countdown.countdown
import me.nebula.orbit.utils.gamestate.GameStateMachine
import me.nebula.orbit.utils.gamestate.gameStateMachine
import me.nebula.orbit.utils.graceperiod.GracePeriodManager
import me.nebula.orbit.utils.graceperiod.gracePeriod
import me.nebula.orbit.utils.hotbar.Hotbar
import me.nebula.orbit.utils.lobby.Lobby
import me.nebula.orbit.utils.lobby.lobby
import me.nebula.orbit.utils.matchresult.MatchResult
import me.nebula.orbit.utils.matchresult.MatchResultDisplay
import me.nebula.orbit.utils.matchresult.matchResult
import me.nebula.orbit.utils.minigametimer.MinigameTimer
import me.nebula.orbit.utils.minigametimer.minigameTimer
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.scoreboard.LiveScoreboard
import me.nebula.orbit.utils.scoreboard.liveScoreboard
import me.nebula.orbit.utils.tablist.LiveTabList
import me.nebula.orbit.utils.tablist.liveTabList
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.timer.Task
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

abstract class GameMode : ServerMode {

    private val logger = logger("GameMode")

    abstract val settings: GameSettings
    abstract fun buildPlaceholderResolver(): PlaceholderResolver
    abstract fun onGameSetup(players: List<Player>)
    abstract fun checkWinCondition(): MatchResult?

    open fun onWaitingStart() {}
    open fun onPlayerJoinWaiting(player: Player) {}
    open fun onPlayerLeaveWaiting(player: Player) {}
    open fun onCountdownTick(remaining: Duration) {}
    open fun onPlayingStart() {}
    open fun onPlayerEliminated(player: Player) {}
    open fun onPlayerDisconnected(player: Player) {}
    open fun onPlayerReconnected(player: Player) {}
    open fun onEndingStart(result: MatchResult) {}
    open fun onEndingComplete() {}
    open fun onGameReset() {}
    open fun buildLobbyHotbar(): Hotbar? = null
    open fun buildTimeExpiredResult(): MatchResult = matchResult {
        draw()
        duration((System.currentTimeMillis() - gameStartTime).milliseconds.let {
            java.time.Duration.ofMillis(it.inWholeMilliseconds)
        })
    }

    val phase: GamePhase get() = stateMachine.current
    val tracker = PlayerTracker()
    protected var gameStartTime: Long = 0L
        private set

    private val disconnectTimers = ConcurrentHashMap<UUID, Task>()
    private var reconnectWindowTask: Task? = null
    @Volatile private var reconnectWindowExpired = false

    override val spawnPoint: Pos by lazy { settings.spawn.toPos() }

    override val defaultInstance: InstanceContainer by lazy {
        val worldPath = java.nio.file.Path.of(settings.worldPath)
        worldPath.parent?.let { Files.createDirectories(it) }
        val centerX = spawnPoint.blockX() shr 4
        val centerZ = spawnPoint.blockZ() shr 4
        val (instance, future) = AnvilWorldLoader.loadAndPreload(
            "game-${this::class.simpleName?.lowercase()}", worldPath,
            centerX, centerZ, settings.preloadRadius,
        )
        future.join()
        instance
    }

    private val resolver: PlaceholderResolver by lazy { buildPlaceholderResolver() }

    private val stateMachine: GameStateMachine<GamePhase> by lazy {
        gameStateMachine(GamePhase.WAITING) {
            allow(GamePhase.WAITING, GamePhase.STARTING)
            allow(GamePhase.STARTING, GamePhase.PLAYING, GamePhase.WAITING)
            allow(GamePhase.PLAYING, GamePhase.ENDING)
            allow(GamePhase.ENDING, GamePhase.WAITING)

            onEnter(GamePhase.WAITING) { enterWaiting() }
            onEnter(GamePhase.STARTING) { enterStarting() }
            onEnter(GamePhase.PLAYING) { enterPlaying() }
            onEnter(GamePhase.ENDING) { enterEnding() }
        }
    }

    private var currentLobby: Lobby? = null
    private var currentHotbar: Hotbar? = null
    private var startingCountdown: Countdown? = null
    private var gameTimer: MinigameTimer? = null
    private var endingCountdown: Countdown? = null
    private lateinit var scoreboard: LiveScoreboard
    private lateinit var tabList: LiveTabList
    private var lastEndResult: MatchResult? = null

    override fun install(handler: GlobalEventHandler) {
        logger.info { "Installing game mode: ${this::class.simpleName}" }

        scoreboard = buildLiveScoreboard()
        tabList = buildLiveTabList()

        handler.addListener(PlayerSpawnEvent::class.java) { event ->
            if (!event.isFirstSpawn) return@addListener
            handlePlayerJoin(event.player)
        }

        handler.addListener(PlayerDisconnectEvent::class.java) { event ->
            handlePlayerDisconnect(event.player)
        }

        stateMachine.forceTransition(GamePhase.WAITING)
        logger.info { "Game mode installed: ${this::class.simpleName}" }
    }

    override fun shutdown() {
        startingCountdown?.stop()
        gameTimer?.stop()
        endingCountdown?.stop()
        currentLobby?.uninstall()
        currentHotbar?.uninstall()
        scoreboard.uninstall()
        tabList.uninstall()
        GracePeriodManager.uninstall()
        cleanupReconnectionState()
        stateMachine.destroy()
    }

    fun eliminate(player: Player) {
        require(phase == GamePhase.PLAYING) { "Can only eliminate during PLAYING phase" }
        require(tracker.isAlive(player.uuid)) { "Player ${player.username} is not alive" }

        tracker.eliminate(player.uuid)
        player.gameMode = net.minestom.server.entity.GameMode.SPECTATOR
        player.teleport(spawnPoint)
        onPlayerEliminated(player)

        val result = checkWinCondition()
        if (result != null) {
            forceEnd(result)
        }
    }

    fun forceEnd(result: MatchResult) {
        lastEndResult = result
        stateMachine.transition(GamePhase.ENDING)
    }

    private fun handlePlayerJoin(player: Player) {
        when (phase) {
            GamePhase.WAITING -> {
                tracker.join(player.uuid)
                currentHotbar?.apply(player)
                onPlayerJoinWaiting(player)
                checkMinPlayersThreshold()
            }
            GamePhase.STARTING -> {
                tracker.join(player.uuid)
                currentHotbar?.apply(player)
                onPlayerJoinWaiting(player)
            }
            GamePhase.PLAYING -> {
                if (settings.timing.allowReconnect && player.uuid in tracker && tracker.isDisconnected(player.uuid)) {
                    disconnectTimers.remove(player.uuid)?.cancel()
                    ReconnectionStore.delete(player.uuid)
                    tracker.reconnect(player.uuid)
                    onPlayerReconnected(player)
                } else {
                    player.gameMode = net.minestom.server.entity.GameMode.SPECTATOR
                }
            }
            GamePhase.ENDING -> {
                player.gameMode = net.minestom.server.entity.GameMode.SPECTATOR
            }
        }
    }

    private fun handlePlayerDisconnect(player: Player) {
        when (phase) {
            GamePhase.WAITING -> {
                tracker.remove(player.uuid)
                currentHotbar?.remove(player)
                onPlayerLeaveWaiting(player)
            }
            GamePhase.STARTING -> {
                tracker.remove(player.uuid)
                currentHotbar?.remove(player)
                onPlayerLeaveWaiting(player)
                if (tracker.aliveCount < settings.timing.minPlayers) {
                    startingCountdown?.stop()
                    startingCountdown = null
                    stateMachine.transition(GamePhase.WAITING)
                }
            }
            GamePhase.PLAYING -> {
                if (tracker.isAlive(player.uuid)) {
                    val canReconnect = settings.timing.allowReconnect && !reconnectWindowExpired
                    if (canReconnect) {
                        tracker.disconnect(player.uuid)
                        ReconnectionStore.save(player.uuid, ReconnectionData(
                            serverName = Orbit.serverName,
                            gameMode = Orbit.gameMode ?: "",
                            disconnectedAt = System.currentTimeMillis(),
                        ))
                        val eliminationSeconds = settings.timing.disconnectEliminationSeconds
                        if (eliminationSeconds > 0) {
                            disconnectTimers[player.uuid] = delay(eliminationSeconds * 20) {
                                autoEliminateDisconnected(player.uuid)
                            }
                        }
                        onPlayerDisconnected(player)
                    } else {
                        tracker.eliminate(player.uuid)
                        onPlayerEliminated(player)
                    }
                    val result = checkWinCondition()
                    if (result != null) {
                        forceEnd(result)
                    }
                }
            }
            GamePhase.ENDING -> {
                tracker.remove(player.uuid)
            }
        }
    }

    private fun autoEliminateDisconnected(uuid: UUID) {
        disconnectTimers.remove(uuid)
        if (!tracker.isDisconnected(uuid)) return
        tracker.eliminate(uuid)
        ReconnectionStore.delete(uuid)
        val result = checkWinCondition()
        if (result != null) forceEnd(result)
    }

    private fun cleanupReconnectionState() {
        reconnectWindowTask?.cancel()
        reconnectWindowTask = null
        reconnectWindowExpired = false
        disconnectTimers.values.forEach { it.cancel() }
        disconnectTimers.clear()
        for (uuid in tracker.disconnected) {
            ReconnectionStore.delete(uuid)
        }
    }

    private fun enterWaiting() {
        cleanupReconnectionState()
        tracker.clear()
        lastEndResult = null
        gameStartTime = 0L

        currentLobby = lobby {
            instance = defaultInstance
            spawnPoint = this@GameMode.spawnPoint
            gameMode = net.minestom.server.entity.GameMode.valueOf(settings.lobby.gameMode)
            protectBlocks = settings.lobby.protectBlocks
            disableDamage = settings.lobby.disableDamage
            disableHunger = settings.lobby.disableHunger
            lockInventory = settings.lobby.lockInventory
            voidTeleportY = settings.lobby.voidTeleportY
        }
        currentLobby!!.install()

        currentHotbar = buildLobbyHotbar()
        currentHotbar?.install()

        for (player in defaultInstance.players) {
            tracker.join(player.uuid)
            player.gameMode = net.minestom.server.entity.GameMode.valueOf(settings.lobby.gameMode)
            player.teleport(spawnPoint)
            currentHotbar?.apply(player)
        }

        onWaitingStart()
        onGameReset()
        checkMinPlayersThreshold()
    }

    private fun enterStarting() {
        startingCountdown = countdown(settings.timing.countdownSeconds.seconds) {
            onTick { remaining ->
                onCountdownTick(remaining)
            }
            onComplete {
                startingCountdown = null
                stateMachine.transition(GamePhase.PLAYING)
            }
        }
        startingCountdown!!.start()
    }

    private fun enterPlaying() {
        currentLobby?.uninstall()
        currentLobby = null
        currentHotbar?.uninstall()
        currentHotbar = null
        startingCountdown?.stop()
        startingCountdown = null

        gameStartTime = System.currentTimeMillis()

        val alivePlayers = tracker.alive.mapNotNull { uuid ->
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
        }

        onGameSetup(alivePlayers)

        if (settings.timing.gracePeriodSeconds > 0) {
            val config = gracePeriod("game-grace") {
                duration(settings.timing.gracePeriodSeconds.seconds)
            }
            alivePlayers.forEach { GracePeriodManager.apply(it, config.name) }
        }

        if (settings.timing.gameDurationSeconds > 0) {
            gameTimer = minigameTimer("game-timer") {
                duration(settings.timing.gameDurationSeconds.seconds)
                onEnd {
                    gameTimer = null
                    val result = buildTimeExpiredResult()
                    forceEnd(result)
                }
            }
            gameTimer!!.addAllViewers(alivePlayers)
            gameTimer!!.start()
        }

        reconnectWindowExpired = false
        val reconnectWindowSeconds = settings.timing.reconnectWindowSeconds
        if (reconnectWindowSeconds > 0) {
            reconnectWindowTask = delay(reconnectWindowSeconds * 20) {
                reconnectWindowExpired = true
                for (uuid in tracker.disconnected.toSet()) {
                    autoEliminateDisconnected(uuid)
                }
            }
        }

        onPlayingStart()
    }

    private fun enterEnding() {
        gameTimer?.stop()
        gameTimer = null
        GracePeriodManager.clearAll()
        cleanupReconnectionState()

        val result = lastEndResult ?: matchResult { draw() }

        val allPlayers = defaultInstance.players.toList()
        MatchResultDisplay.broadcast(allPlayers, result)

        onEndingStart(result)

        endingCountdown = countdown(settings.timing.endingDurationSeconds.seconds) {
            onComplete {
                endingCountdown = null
                onEndingComplete()
                stateMachine.transition(GamePhase.WAITING)
            }
        }
        endingCountdown!!.start()
    }

    private fun checkMinPlayersThreshold() {
        if (phase == GamePhase.WAITING && tracker.aliveCount >= settings.timing.minPlayers) {
            stateMachine.transition(GamePhase.STARTING)
        }
    }

    private fun buildLiveScoreboard(): LiveScoreboard = liveScoreboard {
        val cfg = settings.scoreboard
        if (resolver.hasPlaceholders(cfg.title)) {
            title { player -> resolver.resolve(cfg.title, player) }
        } else {
            title(cfg.title)
        }
        refreshEvery(cfg.refreshSeconds.seconds)
        for (line in cfg.lines) {
            if (resolver.hasPlaceholders(line)) {
                line { player -> resolver.resolve(line, player) }
            } else {
                line(line)
            }
        }
    }

    private fun buildLiveTabList(): LiveTabList = liveTabList {
        val cfg = settings.tabList
        refreshEvery(cfg.refreshSeconds.seconds)
        if (resolver.hasPlaceholders(cfg.header)) {
            header { player -> resolver.resolve(cfg.header, player) }
        } else {
            header(cfg.header)
        }
        if (resolver.hasPlaceholders(cfg.footer)) {
            footer { player -> resolver.resolve(cfg.footer, player) }
        } else {
            footer(cfg.footer)
        }
    }
}
