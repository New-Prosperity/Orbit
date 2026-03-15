package me.nebula.orbit.mode.game

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.host.ConsumeTicketProcessor
import me.nebula.gravity.host.HostTicketStore
import me.nebula.gravity.rank.RankManager
import me.nebula.gravity.reconnection.ReconnectionData
import me.nebula.gravity.reconnection.ReconnectionStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.ServerMode
import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.mode.config.PlaceholderResolver
import me.nebula.orbit.utils.anvilloader.AnvilWorldLoader
import me.nebula.orbit.utils.ceremony.Ceremony
import me.nebula.orbit.utils.deathrecap.DeathRecapTracker
import me.nebula.orbit.utils.gamechat.GameChatPipeline
import me.nebula.orbit.utils.replay.ReplayMetadata
import me.nebula.orbit.utils.replay.ReplayRecorder
import me.nebula.orbit.utils.replay.ReplayStorage
import me.nebula.orbit.utils.killfeed.KillFeed
import me.nebula.orbit.utils.rewards.RewardDistributor
import me.nebula.orbit.utils.spectatortoolkit.SpectatorToolkit
import me.nebula.orbit.utils.countdown.Countdown
import me.nebula.orbit.utils.countdown.countdown
import me.nebula.orbit.utils.gamestate.GameStateMachine
import me.nebula.orbit.utils.gamestate.gameStateMachine
import me.nebula.orbit.utils.graceperiod.GracePeriodManager
import me.nebula.orbit.utils.graceperiod.gracePeriod
import me.nebula.orbit.utils.hotbar.Hotbar
import me.nebula.orbit.utils.kit.Kit
import me.nebula.orbit.utils.lobby.Lobby
import me.nebula.orbit.utils.lobby.lobby
import me.nebula.orbit.utils.matchresult.MatchResult
import me.nebula.orbit.utils.matchresult.MatchResultDisplay
import me.nebula.orbit.utils.matchresult.MatchResultManager
import me.nebula.orbit.utils.matchresult.matchResult
import me.nebula.orbit.utils.minigametimer.MinigameTimer
import me.nebula.orbit.utils.minigametimer.minigameTimer
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.scheduler.repeat
import me.nebula.orbit.utils.scoreboard.LiveScoreboard
import me.nebula.orbit.utils.scoreboard.liveScoreboard
import me.nebula.orbit.utils.tablist.LiveTabList
import me.nebula.orbit.utils.tablist.liveTabList
import me.nebula.orbit.translation.resolveTranslated
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.EventNode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.timer.Task
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

abstract class GameMode : ServerMode {

    private val logger = logger("GameMode")

    abstract val settings: GameSettings

    override val maxPlayers: Int get() = settings.timing.maxPlayers

    override val cosmeticConfig: CosmeticConfig get() = settings.cosmetics ?: CosmeticConfig()

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
    open fun onPlayerDeath(player: Player, killer: Player?) {}
    open fun onPlayerRespawn(player: Player) {}
    open fun onTeamsAssigned(assignments: Map<String, List<UUID>>) {}
    open fun onLateJoin(player: Player) {}
    open fun onKillStreak(player: Player, streak: Int) {}
    open fun onOvertimeStart() {}
    open fun onOvertimeEnd() {}
    open fun onAfkEliminated(player: Player) {}
    open fun onCombatLog(player: Player) {}

    open fun onAllPlayersEliminated() {
        forceEnd(matchResult { draw() })
    }

    open fun onPlayerDamaged(victim: Player, attacker: Player?, amount: Float, event: EntityDamageEvent): Boolean = true

    open fun buildLobbyHotbar(): Hotbar? = null
    open fun buildRespawnKit(): Kit? = null
    open fun buildRespawnPosition(player: Player): Pos = spawnPoint
    open fun buildChatPipeline(): GameChatPipeline? = null
    open fun buildSpectatorToolkit(): SpectatorToolkit? = null
    open fun buildKillFeed(): KillFeed? = null
    open fun buildDeathRecapTracker(): DeathRecapTracker? = null
    open fun buildRewardDistributor(): RewardDistributor? = null
    open fun buildCeremony(result: MatchResult): Ceremony? = null

    open fun resolveGameDuration(): Int = settings.timing.gameDurationSeconds

    open fun buildTimeExpiredResult(): MatchResult = matchResult {
        draw()
        duration(java.time.Duration.ofMillis(System.currentTimeMillis() - gameStartTime))
    }

    open fun buildOvertimeResult(): MatchResult = matchResult {
        draw()
        duration(java.time.Duration.ofMillis(System.currentTimeMillis() - gameStartTime))
    }

    protected open fun assignTeams(players: List<UUID>): Map<UUID, String> {
        val config = settings.teams ?: return emptyMap()
        if (config.teamCount <= 0) return emptyMap()
        val names = config.teamNames.takeIf { it.size >= config.teamCount }
            ?: (1..config.teamCount).map { "team_$it" }
        val shuffled = players.shuffled()
        return shuffled.mapIndexed { i, uuid -> uuid to names[i % names.size] }.toMap()
    }

    protected open fun persistGameStats(result: MatchResult) {}

    val phase: GamePhase get() = stateMachine.current
    val tracker = PlayerTracker()
    protected var gameStartTime: Long = 0L
        private set
    var initialPlayerCount: Int = 0
        private set

    val isTeamMode: Boolean get() = settings.teams?.let { it.teamCount > 0 } ?: false
    val isFriendlyFireEnabled: Boolean get() = settings.teams?.friendlyFire ?: true
    val isOvertime: Boolean get() = _isOvertime
    val isSuddenDeath: Boolean get() = _isSuddenDeath

    fun areTeammates(a: UUID, b: UUID): Boolean = tracker.areTeammates(a, b)

    private val disconnectTimers = ConcurrentHashMap<UUID, Task>()
    private val respawnTimers = ConcurrentHashMap<UUID, Task>()
    private val gameEvents = ConcurrentHashMap<String, Task>()
    private val spectatorTargets = ConcurrentHashMap<UUID, UUID>()
    @Volatile private var reconnectWindowTask: Task? = null
    @Volatile private var lateJoinWindowTask: Task? = null
    @Volatile private var afkCheckTask: Task? = null
    @Volatile private var overtimeTask: Task? = null
    @Volatile private var voidCheckTask: Task? = null
    @Volatile private var freezeEventNode: EventNode<*>? = null
    @Volatile private var gameMechanicsNode: EventNode<*>? = null
    @Volatile private var reconnectWindowExpired = false
    @Volatile private var lateJoinWindowExpired = false
    private val lateJoinCount = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var _isOvertime = false
    @Volatile private var _isSuddenDeath = false
    @Volatile private var chatPipeline: GameChatPipeline? = null
    @Volatile private var spectatorToolkit: SpectatorToolkit? = null
    @Volatile private var killFeed: KillFeed? = null
    @Volatile private var deathRecapTracker: DeathRecapTracker? = null
    @Volatile private var rewardDistributor: RewardDistributor? = null
    @Volatile private var ceremony: Ceremony? = null
    private val replayRecorder = ReplayRecorder()

    override val spawnPoint: Pos by lazy { settings.spawn.toPos() }

    val isDualInstance: Boolean get() = settings.lobbyWorld != null

    val lobbyInstance: InstanceContainer by lazy {
        val lobbyWorld = settings.lobbyWorld ?: return@lazy gameInstance
        val worldPath = resolveWorldPath(lobbyWorld.worldPath)
        val lobbySpawn = lobbyWorld.spawn.toPos()
        val centerX = lobbySpawn.blockX() shr 4
        val centerZ = lobbySpawn.blockZ() shr 4
        val (instance, future) = AnvilWorldLoader.loadAndPreload(
            "lobby-${this::class.simpleName?.lowercase()}", worldPath,
            centerX, centerZ, lobbyWorld.preloadRadius,
        )
        future.join()
        instance
    }

    val lobbySpawnPoint: Pos by lazy {
        settings.lobbyWorld?.spawn?.toPos() ?: spawnPoint
    }

    override val activeInstance: InstanceContainer
        get() = when (phase) {
            GamePhase.WAITING, GamePhase.STARTING -> lobbyInstance
            GamePhase.PLAYING, GamePhase.ENDING -> gameInstance
        }

    override val activeSpawnPoint: Pos
        get() = when (phase) {
            GamePhase.WAITING, GamePhase.STARTING -> lobbySpawnPoint
            GamePhase.PLAYING, GamePhase.ENDING -> spawnPoint
        }

    @Volatile private var _gameInstance: InstanceContainer? = null

    val gameInstance: InstanceContainer
        get() = _gameInstance ?: createGameInstance().also { _gameInstance = it }

    override val defaultInstance: InstanceContainer get() = gameInstance

    protected open fun createGameInstance(): InstanceContainer {
        val worldPath = resolveWorldPath(settings.worldPath)
        val centerX = spawnPoint.blockX() shr 4
        val centerZ = spawnPoint.blockZ() shr 4
        val (instance, future) = AnvilWorldLoader.loadAndPreload(
            "game-${this::class.simpleName?.lowercase()}", worldPath,
            centerX, centerZ, settings.preloadRadius,
        )
        future.join()
        return instance
    }

    private fun resolveWorldPath(configPath: String): java.nio.file.Path {
        val name = java.nio.file.Path.of(configPath).fileName.toString()
        return me.nebula.orbit.utils.maploader.MapLoader.resolve(name)
    }

    protected fun invalidateGameInstance() {
        val old = _gameInstance ?: return
        _gameInstance = null
        if (isDualInstance) {
            old.players.toList().forEach { it.setInstance(lobbyInstance, lobbySpawnPoint) }
        }
        MinecraftServer.getInstanceManager().unregisterInstance(old)
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

    @Volatile private var currentLobby: Lobby? = null
    @Volatile private var currentHotbar: Hotbar? = null
    @Volatile private var startingCountdown: Countdown? = null
    @Volatile private var gameTimer: MinigameTimer? = null
    @Volatile private var endingCountdown: Countdown? = null
    private lateinit var scoreboard: LiveScoreboard
    private lateinit var tabList: LiveTabList
    @Volatile private var lastEndResult: MatchResult? = null

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
        chatPipeline?.uninstall()
        spectatorToolkit?.uninstall()
        cleanupReconnectionState()
        cleanupRespawnTimers()
        cleanupLateJoinState()
        cleanupGameEvents()
        cleanupAfkCheck()
        cleanupOvertime()
        cleanupFreezeNode()
        cleanupGameMechanicsNode()
        cleanupVoidCheck()
        stateMachine.destroy()
    }

    fun eliminate(player: Player) {
        if (phase != GamePhase.PLAYING) return
        if (!tracker.isAlive(player.uuid) && !tracker.isRespawning(player.uuid)) return

        respawnTimers.remove(player.uuid)?.cancel()
        tracker.eliminate(player.uuid)
        player.gameMode = net.minestom.server.entity.GameMode.SPECTATOR
        player.teleport(spawnPoint)

        autoSpectateOnEliminate(player)
        spectatorToolkit?.apply(player)
        onPlayerEliminated(player)
        checkGameEnd()
    }

    fun revive(player: Player, position: Pos = spawnPoint) {
        if (phase != GamePhase.PLAYING) return
        if (!tracker.isSpectating(player.uuid)) return

        spectatorToolkit?.remove(player)
        spectatorTargets.remove(player.uuid)
        player.stopSpectating()
        tracker.revive(player.uuid)
        player.gameMode = net.minestom.server.entity.GameMode.SURVIVAL
        player.teleport(position)
    }

    fun handleDeath(player: Player, killer: Player? = null) {
        if (phase != GamePhase.PLAYING) return
        if (!tracker.isAlive(player.uuid)) return

        tracker.recordDeath(player.uuid)

        if (killer != null && killer.uuid != player.uuid) {
            tracker.recordKill(killer.uuid)
            val streak = tracker.streakOf(killer.uuid)
            if (streak > 1) onKillStreak(killer, streak)
        }

        creditAssists(player.uuid, killer?.uuid)

        killFeed?.reportKill(me.nebula.orbit.utils.killfeed.KillEvent(killer = killer, victim = player))
        deathRecapTracker?.sendRecap(player)

        onPlayerDeath(player, killer)

        if (_isSuddenDeath) {
            eliminate(player)
            return
        }

        val respawnConfig = settings.respawn
        if (respawnConfig != null) {
            val livesRemaining = if (respawnConfig.maxLives > 0) {
                tracker.decrementLives(player.uuid)
            } else {
                1
            }

            if (livesRemaining > 0) {
                scheduleRespawn(player, respawnConfig)
                return
            }
        }

        eliminate(player)
    }

    fun forceReconnect(player: Player) {
        if (phase != GamePhase.PLAYING) return
        if (!tracker.isDisconnected(player.uuid)) return

        disconnectTimers.remove(player.uuid)?.cancel()
        ReconnectionStore.delete(player.uuid)
        tracker.reconnect(player.uuid)
        onPlayerReconnected(player)
    }

    fun forceStart() {
        if (phase != GamePhase.WAITING && phase != GamePhase.STARTING) return

        if (phase == GamePhase.WAITING) {
            stateMachine.transition(GamePhase.STARTING)
        }
        startingCountdown?.stop()
        startingCountdown = null
        stateMachine.transition(GamePhase.PLAYING)
    }

    fun forceEnd(result: MatchResult) {
        if (phase == GamePhase.ENDING || phase == GamePhase.WAITING) return
        lastEndResult = result
        stateMachine.transition(GamePhase.ENDING)
    }

    fun nextSpectatorTarget(player: Player): Player? {
        if (!tracker.isSpectating(player.uuid)) return null
        val targets = resolveSpectatorTargets(player)
        if (targets.isEmpty()) return null
        val currentTargetUuid = spectatorTargets[player.uuid]
        val currentIndex = if (currentTargetUuid != null) targets.indexOfFirst { it.uuid == currentTargetUuid } else -1
        val next = targets[(currentIndex + 1) % targets.size]
        spectatorTargets[player.uuid] = next.uuid
        player.spectate(next)
        return next
    }

    fun previousSpectatorTarget(player: Player): Player? {
        if (!tracker.isSpectating(player.uuid)) return null
        val targets = resolveSpectatorTargets(player)
        if (targets.isEmpty()) return null
        val currentTargetUuid = spectatorTargets[player.uuid]
        val currentIndex = if (currentTargetUuid != null) targets.indexOfFirst { it.uuid == currentTargetUuid } else targets.size
        val prev = targets[(currentIndex - 1 + targets.size) % targets.size]
        spectatorTargets[player.uuid] = prev.uuid
        player.spectate(prev)
        return prev
    }

    fun scheduleGameEvent(name: String, delayTicks: Int, action: () -> Unit): Task {
        gameEvents.remove(name)?.cancel()
        val task = delay(delayTicks) {
            gameEvents.remove(name)
            if (phase == GamePhase.PLAYING) action()
        }
        gameEvents[name] = task
        return task
    }

    fun cancelGameEvent(name: String) {
        gameEvents.remove(name)?.cancel()
    }

    fun broadcastAlive(action: (Player) -> Unit) {
        for (uuid in tracker.alive) {
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.let(action)
        }
    }

    fun broadcastSpectators(action: (Player) -> Unit) {
        for (uuid in tracker.spectating) {
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.let(action)
        }
    }

    fun broadcastTeam(team: String, action: (Player) -> Unit) {
        for (uuid in tracker.teamMembers(team)) {
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.let(action)
        }
    }

    fun broadcastAll(action: (Player) -> Unit) {
        activeInstance.players.forEach(action)
    }

    protected fun lastTeamStandingName(): String? {
        val alive = tracker.aliveTeams()
        return if (alive.size == 1) alive.first() else null
    }

    protected fun lastPlayerStandingUuid(): UUID? =
        if (tracker.aliveCount <= 1) tracker.alive.firstOrNull() else null

    private fun autoSpectateOnEliminate(eliminated: Player) {
        val lastAttackerUuid = tracker.recentDamagersOf(eliminated.uuid, ASSIST_WINDOW_MILLIS).firstOrNull()
        val target = if (lastAttackerUuid != null && tracker.isAlive(lastAttackerUuid)) {
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(lastAttackerUuid)
        } else {
            resolveSpectatorTargets(eliminated).firstOrNull()
        }
        if (target != null) {
            spectatorTargets[eliminated.uuid] = target.uuid
            eliminated.spectate(target)
        }
    }

    private fun resolveSpectatorTargets(spectator: Player): List<Player> {
        val team = tracker.teamOf(spectator.uuid)
        val candidateUuids = if (team != null && isTeamMode) {
            val teammates = tracker.aliveInTeam(team)
            if (teammates.isNotEmpty()) teammates else tracker.alive
        } else {
            tracker.alive
        }
        return candidateUuids.mapNotNull { MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it) }
    }

    private fun creditAssists(victimUuid: UUID, killerUuid: UUID?) {
        val damagers = tracker.recentDamagersOf(victimUuid, ASSIST_WINDOW_MILLIS)
        for (damager in damagers) {
            if (damager != killerUuid && damager != victimUuid) {
                tracker.recordAssist(damager)
            }
        }
    }

    private fun handlePlayerJoin(player: Player) {
        when (phase) {
            GamePhase.WAITING -> {
                if (tracker.size >= settings.timing.maxPlayers) {
                    player.gameMode = net.minestom.server.entity.GameMode.SPECTATOR
                    return
                }
                tracker.join(player.uuid)
                currentHotbar?.apply(player)
                onPlayerJoinWaiting(player)
                checkMinPlayersThreshold()
            }
            GamePhase.STARTING -> {
                if (tracker.size >= settings.timing.maxPlayers) {
                    player.gameMode = net.minestom.server.entity.GameMode.SPECTATOR
                    return
                }
                tracker.join(player.uuid)
                currentHotbar?.apply(player)
                onPlayerJoinWaiting(player)
            }
            GamePhase.PLAYING -> handleJoinDuringPlaying(player)
            GamePhase.ENDING -> {
                player.gameMode = net.minestom.server.entity.GameMode.SPECTATOR
            }
        }
    }

    private fun handleJoinDuringPlaying(player: Player) {
        if (settings.timing.allowReconnect && player.uuid in tracker && tracker.isDisconnected(player.uuid)) {
            val previousState = tracker.stateOf(player.uuid) as? PlayerState.Disconnected

            disconnectTimers.remove(player.uuid)?.cancel()
            ReconnectionStore.delete(player.uuid)
            tracker.reconnect(player.uuid)

            if (previousState?.wasRespawning == true) {
                val pos = buildRespawnPosition(player)
                player.teleport(pos)
                player.gameMode = net.minestom.server.entity.GameMode.SURVIVAL
                if (settings.respawn?.clearInventoryOnRespawn == true) player.inventory.clear()
                buildRespawnKit()?.apply(player)
                applyRespawnInvincibility(player)
            } else {
                player.gameMode = net.minestom.server.entity.GameMode.SURVIVAL
            }

            onPlayerReconnected(player)
            return
        }

        if (canLateJoin()) {
            lateJoinCount.incrementAndGet()
            tracker.join(player.uuid)

            if (isTeamMode) {
                val smallestTeam = tracker.allTeams()
                    .minByOrNull { tracker.activeInTeam(it).size }
                if (smallestTeam != null) {
                    tracker.assignTeam(player.uuid, smallestTeam)
                }
            }

            val respawnConfig = settings.respawn
            if (respawnConfig != null && respawnConfig.maxLives > 0) {
                tracker.setLives(player.uuid, respawnConfig.maxLives)
            }

            if (settings.lateJoin?.joinAsSpectator == true) {
                player.gameMode = net.minestom.server.entity.GameMode.SPECTATOR
            } else {
                player.gameMode = net.minestom.server.entity.GameMode.SURVIVAL
            }

            onLateJoin(player)
            return
        }

        player.gameMode = net.minestom.server.entity.GameMode.SPECTATOR
        spectatorToolkit?.apply(player)
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
            GamePhase.PLAYING -> handleDisconnectDuringPlaying(player)
            GamePhase.ENDING -> {
                tracker.remove(player.uuid)
            }
        }
    }

    private fun handleDisconnectDuringPlaying(player: Player) {
        if (!tracker.isAlive(player.uuid) && !tracker.isRespawning(player.uuid)) return

        respawnTimers.remove(player.uuid)?.cancel()
        spectatorTargets.remove(player.uuid)

        val combatLogSeconds = settings.timing.combatLogSeconds
        if (combatLogSeconds > 0 && tracker.isInCombat(player.uuid, combatLogSeconds * 1000L)) {
            tracker.eliminate(player.uuid)
            onCombatLog(player)
            onPlayerEliminated(player)
            checkGameEnd()
            return
        }

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

        checkGameEnd()
    }

    private fun autoEliminateDisconnected(uuid: UUID) {
        disconnectTimers.remove(uuid)
        if (!tracker.isDisconnected(uuid)) return
        tracker.eliminate(uuid)
        ReconnectionStore.delete(uuid)
        checkGameEnd()
    }

    private fun checkGameEnd() {
        if (phase != GamePhase.PLAYING) return

        val result = checkWinCondition()
        if (result != null) {
            forceEnd(result)
            return
        }

        val minViable = settings.timing.minViablePlayers
        if (minViable > 0 && tracker.effectiveAliveCount < minViable) {
            forceEnd(buildTimeExpiredResult())
            return
        }

        if (tracker.effectiveAliveCount == 0) {
            onAllPlayersEliminated()
        }
    }

    private fun scheduleRespawn(player: Player, config: RespawnConfig) {
        tracker.markRespawning(player.uuid)
        player.gameMode = net.minestom.server.entity.GameMode.SPECTATOR

        respawnTimers[player.uuid] = delay(config.respawnDelayTicks) {
            executeRespawn(player.uuid, config)
        }
    }

    private fun executeRespawn(uuid: UUID, config: RespawnConfig) {
        respawnTimers.remove(uuid)
        if (!tracker.isRespawning(uuid)) return
        if (phase != GamePhase.PLAYING) return

        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
        if (player == null) {
            tracker.disconnect(uuid)
            val canReconnect = settings.timing.allowReconnect && !reconnectWindowExpired
            if (!canReconnect) {
                tracker.eliminate(uuid)
                checkGameEnd()
            } else {
                ReconnectionStore.save(uuid, ReconnectionData(
                    serverName = Orbit.serverName,
                    gameMode = Orbit.gameMode ?: "",
                    disconnectedAt = System.currentTimeMillis(),
                ))
            }
            return
        }

        spectatorToolkit?.remove(player)
        spectatorTargets.remove(uuid)
        player.stopSpectating()
        tracker.revive(uuid)
        val pos = buildRespawnPosition(player)
        player.teleport(pos)
        player.gameMode = net.minestom.server.entity.GameMode.SURVIVAL
        if (config.clearInventoryOnRespawn) player.inventory.clear()
        buildRespawnKit()?.apply(player)
        applyRespawnInvincibility(player)
        onPlayerRespawn(player)
    }

    private fun applyRespawnInvincibility(player: Player) {
        val ticks = settings.respawn?.invincibilityTicks ?: return
        if (ticks <= 0) return
        GracePeriodManager.apply(player, RESPAWN_GRACE_NAME)
    }

    private fun canLateJoin(): Boolean {
        val config = settings.lateJoin ?: return false
        if (lateJoinWindowExpired) return false
        if (config.maxLateJoiners > 0 && lateJoinCount.get() >= config.maxLateJoiners) return false
        return true
    }

    private fun startAfkCheck() {
        val thresholdSeconds = settings.timing.afkEliminationSeconds
        if (thresholdSeconds <= 0) return
        val thresholdMillis = thresholdSeconds * 1000L
        afkCheckTask = repeat(100) {
            if (phase != GamePhase.PLAYING) return@repeat
            for (uuid in tracker.alive.toSet()) {
                if (tracker.isAfk(uuid, thresholdMillis)) {
                    val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: continue
                    onAfkEliminated(player)
                    eliminate(player)
                }
            }
        }
    }

    private fun startVoidCheck() {
        val voidY = settings.timing.voidDeathY
        if (voidY == Double.NEGATIVE_INFINITY) return
        voidCheckTask = repeat(10) {
            if (phase != GamePhase.PLAYING) return@repeat
            for (uuid in tracker.alive.toSet()) {
                if (tracker.isRespawning(uuid)) continue
                val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: continue
                if (player.position.y() < voidY) {
                    handleDeath(player)
                }
            }
        }
    }

    private fun startOvertime() {
        val config = settings.overtime ?: return
        _isOvertime = true
        if (config.suddenDeath) _isSuddenDeath = true
        onOvertimeStart()

        overtimeTask = delay(config.durationSeconds * 20) {
            _isOvertime = false
            _isSuddenDeath = false
            onOvertimeEnd()
            forceEnd(buildOvertimeResult())
        }
    }

    private fun installFreezeNode() {
        if (!settings.timing.freezeDuringCountdown) return
        val node = EventNode.all("gamemode-countdown-freeze")
        node.addListener(PlayerMoveEvent::class.java) { event ->
            if (phase != GamePhase.STARTING) return@addListener
            if (!tracker.isAlive(event.player.uuid)) return@addListener
            val old = event.player.position
            event.newPosition = Pos(old.x(), old.y(), old.z(), event.newPosition.yaw(), event.newPosition.pitch())
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        freezeEventNode = node
    }

    private fun installGameMechanicsNode() {
        val node = EventNode.all("gamemode-mechanics")
        var hasListeners = false

        val needsFriendlyFire = isTeamMode && !isFriendlyFireEnabled
        val needsDamageHook = true
        val needsDamageTracking = settings.timing.combatLogSeconds > 0
        val needsActivityTracking = settings.timing.afkEliminationSeconds > 0

        if (needsFriendlyFire || needsDamageHook || needsDamageTracking) {
            hasListeners = true
            node.addListener(EntityDamageEvent::class.java) { event ->
                val victim = event.entity as? Player ?: return@addListener
                if (phase != GamePhase.PLAYING) return@addListener
                if (!tracker.isAlive(victim.uuid)) return@addListener

                val damage = event.damage
                val attackerEntity = if (damage is EntityDamage) damage.source else null
                val attacker = attackerEntity as? Player

                if (needsFriendlyFire && attacker != null && areTeammates(attacker.uuid, victim.uuid)) {
                    event.isCancelled = true
                    return@addListener
                }

                if (!onPlayerDamaged(victim, attacker, damage.amount, event)) {
                    event.isCancelled = true
                    return@addListener
                }

                if (event.isCancelled) return@addListener

                if (attacker != null && attacker.uuid != victim.uuid) {
                    tracker.recordDamage(attacker.uuid, victim.uuid)
                }

                deathRecapTracker?.recordDamage(victim.uuid, me.nebula.orbit.utils.deathrecap.DamageEntry(
                    attackerUuid = attacker?.uuid,
                    attackerName = attacker?.username ?: damage.type.key().value(),
                    amount = damage.amount,
                    source = if (attacker != null) "PLAYER" else damage.type.key().value(),
                ))
            }
        }

        if (needsActivityTracking) {
            hasListeners = true
            node.addListener(PlayerMoveEvent::class.java) { event ->
                if (phase == GamePhase.PLAYING && tracker.isAlive(event.player.uuid)) {
                    tracker.markActivity(event.player.uuid)
                }
            }
        }

        if (settings.timing.isolateSpectatorChat && chatPipeline == null) {
            hasListeners = true
            node.addListener(PlayerChatEvent::class.java) { event ->
                if (phase != GamePhase.PLAYING) return@addListener
                val sender = event.player
                val senderAlive = tracker.isAlive(sender.uuid)

                event.recipients.removeIf { recipient ->
                    if (!tracker.contains(recipient.uuid)) return@removeIf false
                    val recipientAlive = tracker.isAlive(recipient.uuid)
                    if (!senderAlive && recipientAlive) return@removeIf true
                    if (senderAlive && !recipientAlive) return@removeIf true
                    false
                }
            }
        }

        if (hasListeners) {
            MinecraftServer.getGlobalEventHandler().addChild(node)
            gameMechanicsNode = node
        }
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

    private fun cleanupRespawnTimers() {
        respawnTimers.values.forEach { it.cancel() }
        respawnTimers.clear()
    }

    private fun cleanupLateJoinState() {
        lateJoinWindowTask?.cancel()
        lateJoinWindowTask = null
        lateJoinWindowExpired = false
        lateJoinCount.set(0)
    }

    private fun cleanupGameEvents() {
        gameEvents.values.forEach { it.cancel() }
        gameEvents.clear()
    }

    private fun cleanupAfkCheck() {
        afkCheckTask?.cancel()
        afkCheckTask = null
    }

    private fun cleanupOvertime() {
        overtimeTask?.cancel()
        overtimeTask = null
        _isOvertime = false
        _isSuddenDeath = false
    }

    private fun cleanupFreezeNode() {
        freezeEventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        freezeEventNode = null
    }

    private fun cleanupGameMechanicsNode() {
        gameMechanicsNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        gameMechanicsNode = null
    }

    private fun cleanupVoidCheck() {
        voidCheckTask?.cancel()
        voidCheckTask = null
    }

    private fun enterWaiting() {
        cleanupReconnectionState()
        cleanupRespawnTimers()
        cleanupLateJoinState()
        cleanupGameEvents()
        cleanupAfkCheck()
        cleanupOvertime()
        cleanupGameMechanicsNode()
        cleanupVoidCheck()
        chatPipeline?.uninstall()
        chatPipeline = null
        spectatorToolkit?.uninstall()
        spectatorToolkit = null
        killFeed?.clear()
        killFeed = null
        deathRecapTracker?.clear()
        deathRecapTracker = null
        rewardDistributor = null
        ceremony?.stop()
        ceremony = null
        spectatorTargets.clear()
        tracker.clear()
        lastEndResult = null
        gameStartTime = 0L
        initialPlayerCount = 0

        currentLobby = lobby {
            instance = lobbyInstance
            spawnPoint = lobbySpawnPoint
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

        if (isDualInstance) {
            val gameInst = _gameInstance
            if (gameInst != null) {
                val transfers = gameInst.players.toList().map { player ->
                    player.setInstance(lobbyInstance, lobbySpawnPoint)
                }
                CompletableFuture.allOf(*transfers.toTypedArray()).join()
            }
        }

        for (player in lobbyInstance.players) {
            tracker.join(player.uuid)
            player.gameMode = net.minestom.server.entity.GameMode.valueOf(settings.lobby.gameMode)
            if (!isDualInstance) player.teleport(lobbySpawnPoint)
            currentHotbar?.apply(player)
        }

        onWaitingStart()
        onGameReset()
        checkMinPlayersThreshold()
    }

    private fun enterStarting() {
        installFreezeNode()

        startingCountdown = countdown(settings.timing.countdownSeconds.seconds) {
            onTick { remaining ->
                onCountdownTick(remaining)
            }
            onComplete {
                startingCountdown = null
                cleanupFreezeNode()
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
        cleanupFreezeNode()

        gameStartTime = System.currentTimeMillis()
        initialPlayerCount = tracker.aliveCount

        Orbit.hostOwner?.let { owner ->
            if (!RankManager.hasPermission(owner, "*")) {
                val consumed = HostTicketStore.executeOnKey(owner, ConsumeTicketProcessor())
                if (!consumed) logger.warn { "Failed to consume host ticket for $owner (balance may be zero)" }
            }
        }

        val teamConfig = settings.teams
        if (teamConfig != null && teamConfig.teamCount > 0) {
            val assignments = assignTeams(tracker.alive.toList())
            for ((uuid, team) in assignments) {
                tracker.assignTeam(uuid, team)
            }
            val grouped = assignments.entries.groupBy({ it.value }, { it.key })
            onTeamsAssigned(grouped)
        }

        val respawnConfig = settings.respawn
        if (respawnConfig != null && respawnConfig.maxLives > 0) {
            for (uuid in tracker.alive) {
                tracker.setLives(uuid, respawnConfig.maxLives)
            }
        }

        if (respawnConfig != null && respawnConfig.invincibilityTicks > 0) {
            gracePeriod(RESPAWN_GRACE_NAME) {
                duration((respawnConfig.invincibilityTicks * 50L).milliseconds)
            }
        }

        val alivePlayers = tracker.alive.mapNotNull { uuid ->
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
        }

        if (isDualInstance) {
            val transfers = alivePlayers.map { player ->
                player.setInstance(gameInstance, spawnPoint)
            }
            CompletableFuture.allOf(*transfers.toTypedArray()).join()
        }

        for (player in alivePlayers) {
            tracker.markActivity(player.uuid)
        }

        onGameSetup(alivePlayers)

        if (settings.timing.gracePeriodSeconds > 0) {
            val config = gracePeriod("game-grace") {
                duration(settings.timing.gracePeriodSeconds.seconds)
            }
            alivePlayers.forEach { GracePeriodManager.apply(it, config.name) }
        }

        val resolvedDuration = resolveGameDuration()
        if (resolvedDuration > 0) {
            gameTimer = minigameTimer("game-timer") {
                duration(resolvedDuration.seconds)
                onEnd {
                    gameTimer = null
                    handleTimerExpired()
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

        lateJoinWindowExpired = false
        lateJoinCount.set(0)
        val lateJoinConfig = settings.lateJoin
        if (lateJoinConfig != null) {
            lateJoinWindowTask = delay(lateJoinConfig.windowSeconds * 20) {
                lateJoinWindowExpired = true
            }
        }

        chatPipeline = buildChatPipeline()
        chatPipeline?.install()

        installGameMechanicsNode()
        startAfkCheck()
        startVoidCheck()

        spectatorToolkit = buildSpectatorToolkit()
        spectatorToolkit?.install()

        killFeed = buildKillFeed()
        deathRecapTracker = buildDeathRecapTracker()
        rewardDistributor = buildRewardDistributor()

        replayRecorder.start()

        onPlayingStart()
    }

    private fun handleTimerExpired() {
        val result = checkWinCondition()
        if (result != null) {
            forceEnd(result)
            return
        }

        val overtimeConfig = settings.overtime
        if (overtimeConfig != null && tracker.effectiveAliveCount > 0) {
            startOvertime()
            return
        }

        forceEnd(buildTimeExpiredResult())
    }

    private fun enterEnding() {
        gameTimer?.stop()
        gameTimer = null
        GracePeriodManager.clearAll()
        cleanupReconnectionState()
        cleanupRespawnTimers()
        cleanupLateJoinState()
        cleanupGameEvents()
        cleanupAfkCheck()
        cleanupOvertime()
        cleanupGameMechanicsNode()
        cleanupVoidCheck()

        val result = lastEndResult ?: matchResult { draw() }

        MatchResultManager.store(result)
        persistGameStats(result)

        val replayData = replayRecorder.stop()
        if (replayData.frames.isNotEmpty() && ReplayStorage.isInitialized()) {
            Thread.startVirtualThread {
                runCatching {
                    val metadata = ReplayMetadata(
                        gameMode = Orbit.gameMode ?: "unknown",
                        mapName = Orbit.mapName ?: "",
                        recordedAt = System.currentTimeMillis(),
                        playerCount = initialPlayerCount,
                        durationTicks = replayData.durationTicks,
                    )
                    ReplayStorage.save("${Orbit.serverName}-${System.currentTimeMillis()}", replayData, metadata)
                }.onFailure { logger.warn { "Failed to save replay: ${it.message}" } }
            }
        }

        rewardDistributor?.distribute(result, tracker.all)

        val allPlayers = gameInstance.players.toList()
        MatchResultDisplay.broadcast(allPlayers, result)

        ceremony = buildCeremony(result)
        ceremony?.start(allPlayers)

        onEndingStart(result)

        endingCountdown = countdown(settings.timing.endingDurationSeconds.seconds) {
            onComplete {
                endingCountdown = null
                onEndingComplete()
                logger.info { "Game ended, terminating server..." }
                Thread.startVirtualThread {
                    Orbit.app.stop().join()
                    Runtime.getRuntime().halt(0)
                }
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
        title { player -> resolver.resolveTranslated(cfg.title, player) }
        refreshEvery(cfg.refreshSeconds.seconds)
        for (line in cfg.lines) {
            line { player -> resolver.resolveTranslated(line, player) }
        }
    }

    private fun buildLiveTabList(): LiveTabList = liveTabList {
        val cfg = settings.tabList
        refreshEvery(cfg.refreshSeconds.seconds)
        header { player -> resolver.resolveTranslated(cfg.header, player) }
        footer { player -> resolver.resolveTranslated(cfg.footer, player) }
    }

    private companion object {
        const val RESPAWN_GRACE_NAME = "respawn-invincibility"
        const val ASSIST_WINDOW_MILLIS = 10_000L
    }
}
