package me.nebula.orbit.mode.game

import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.logging.withTrace
import me.nebula.orbit.traceId
import me.nebula.ether.utils.hazelcast.Store
import net.minestom.server.entity.GameMode as MinestomGameMode
import me.nebula.orbit.progression.PartyBonusCalculator
import me.nebula.orbit.progression.ProgressionEvent
import me.nebula.orbit.progression.ProgressionEventBus
import me.nebula.gravity.battlepass.BattlePassDefinition
import me.nebula.gravity.host.ConsumeTicketProcessor
import me.nebula.gravity.host.HostTicketStore
import me.nebula.gravity.messaging.GameEndMessage
import me.nebula.gravity.rating.ApplyRatingChangeProcessor
import me.nebula.gravity.rating.EloCalculator
import me.nebula.gravity.rating.GameModeRating
import me.nebula.gravity.rating.RatingStore
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.rank.RankManager
import me.nebula.gravity.reconnection.ReconnectionData
import me.nebula.gravity.reconnection.ReconnectionStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.audit.OrbitAudit
import me.nebula.orbit.displayUsername
import me.nebula.orbit.utils.botai.BotLobbyFiller
import me.nebula.orbit.mode.ServerMode
import me.nebula.orbit.mutator.MutatorEngine
import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.mode.config.PlaceholderResolver
import me.nebula.orbit.utils.anvilloader.AnvilWorldLoader
import me.nebula.orbit.utils.maploader.MapLoader
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.orbit.utils.achievement.AchievementTriggerManager
import me.nebula.orbit.utils.ceremony.Ceremony
import me.nebula.orbit.utils.combo.ComboConfig
import me.nebula.orbit.utils.combo.install
import me.nebula.orbit.utils.combo.uninstall
import me.nebula.orbit.utils.deathrecap.DamageEntry
import me.nebula.orbit.utils.deathrecap.DeathRecapTracker
import me.nebula.orbit.utils.gamechat.GameChatPipeline
import me.nebula.gravity.messaging.ReplayHighlightMessage
import me.nebula.orbit.utils.replay.HighlightType
import me.nebula.orbit.utils.replay.PendingReplayFlushes
import me.nebula.orbit.utils.replay.PacketReplayRecorder
import me.nebula.orbit.utils.replay.ReplayHighlights
import me.nebula.orbit.utils.replay.ReplayMetadata
import me.nebula.orbit.utils.replay.ReplayRecorder
import me.nebula.orbit.utils.replay.ReplayStorage
import me.nebula.orbit.utils.killfeed.KillEvent
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
import me.nebula.orbit.utils.teambalance.TeamBalance
import me.nebula.orbit.translation.resolveTranslated
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.progression.BattlePassRegistry
import me.nebula.orbit.progression.mission.MissionTracker
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.EventNode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
    open fun buildDeathRecapTracker(): DeathRecapTracker? = DeathRecapTracker()
    open fun buildRewardDistributor(): RewardDistributor? = null
    open fun buildCeremony(result: MatchResult): Ceremony? = null
    protected open fun buildComboCounter(): ComboConfig? = null

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

    private fun applyRatingChanges() {
        val gameModeName = Orbit.gameMode ?: return
        val gameDurationTicks = ((System.currentTimeMillis() - gameStartTime) / 50).coerceAtLeast(1)

        data class PlayerRatingInput(
            val uuid: UUID,
            val rating: Int,
            val placement: Int,
            val gamesPlayed: Int,
            val consecutiveGains: Int,
            val survivalTicks: Long,
        )

        val playerData = mutableListOf<PlayerRatingInput>()
        for (uuid in tracker.all) {
            val placement = placements[uuid] ?: initialPlayerCount
            val ratingData = RatingStore.load(uuid)
            val gmRating = ratingData?.ratings?.get(gameModeName) ?: GameModeRating()
            val survivalTicks = gameDurationTicks * placement / initialPlayerCount.coerceAtLeast(1)
            playerData.add(PlayerRatingInput(uuid, gmRating.rating, placement, gmRating.gamesPlayed, gmRating.consecutiveGains, survivalTicks))
        }

        if (playerData.size < 2) return

        val lobbyAvg = EloCalculator.lobbyAverage(playerData.map { it.rating })

        for (pd in playerData) {
            val opponents = playerData.filter { it.uuid != pd.uuid }.map { it.rating to it.placement }
            val delta = EloCalculator.calculateDelta(
                playerRating = pd.rating,
                playerPlacement = pd.placement,
                opponents = opponents,
                gamesPlayed = pd.gamesPlayed,
                consecutiveGains = pd.consecutiveGains,
                survivalTicks = pd.survivalTicks,
                totalGameTicks = gameDurationTicks,
            )

            val updated = RatingStore.executeOnKey(pd.uuid, ApplyRatingChangeProcessor(
                gameMode = gameModeName,
                change = delta,
                placement = pd.placement,
                lobbySize = playerData.size,
                lobbyAvgRating = lobbyAvg,
            ))

            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(pd.uuid) ?: continue
            val oldRating = pd.rating
            val newRating = updated.rating
            val changeStr = if (delta >= 0) "<green>+$delta" else "<red>$delta"
            val tier = EloCalculator.tierOf(newRating)
            val oldTier = EloCalculator.tierOf(oldRating)

            player.sendMessage(player.translate(
                "orbit.rating.change",
                "old" to oldRating.toString(),
                "new" to newRating.toString(),
                "change" to changeStr,
                "tier" to "${tier.color}${tier.displayName}",
                "placement" to pd.placement.toString(),
                "lobby" to playerData.size.toString(),
            ))

            if (tier != oldTier && newRating > oldRating) {
                player.sendMessage(player.translate(
                    "orbit.rating.tier_up",
                    "tier" to "${tier.color}${tier.displayName}",
                ))
            } else if (tier != oldTier && newRating < oldRating) {
                player.sendMessage(player.translate(
                    "orbit.rating.tier_down",
                    "tier" to "${tier.color}${tier.displayName}",
                ))
            }
        }
    }

    val phase: GamePhase get() = stateMachine.current

    fun onPhaseChange(callback: (from: GamePhase, to: GamePhase) -> Unit) {
        stateMachine.onTransition(callback)
    }

    val tracker = PlayerTracker()
    protected var gameStartTime: Long = 0L
        private set
    var initialPlayerCount: Int = 0
        private set

    @Volatile protected var gamePartySnapshot: Map<UUID, Set<UUID>> = emptyMap()
        private set

    @Volatile protected var partyBonuses: Map<UUID, PartyBonusCalculator.PartyBonus> = emptyMap()
        private set

    protected fun applyPartyBonus(uuid: UUID, baseAmount: Long): Long {
        val bonus = partyBonuses[uuid] ?: return baseAmount
        return (baseAmount * bonus.multiplier).toLong()
    }

    protected fun applyPartyBonus(uuid: UUID, baseAmount: Double): Double {
        val bonus = partyBonuses[uuid] ?: return baseAmount
        return baseAmount * bonus.multiplier
    }

    val isTeamMode: Boolean get() = settings.teams?.let { it.teamCount > 0 } ?: false
    val isFriendlyFireEnabled: Boolean get() = settings.teams?.friendlyFire ?: true
    val isOvertime: Boolean get() = overtimeController.isOvertime
    val isSuddenDeath: Boolean get() = overtimeController.isSuddenDeath

    fun areTeammates(a: UUID, b: UUID): Boolean = tracker.areTeammates(a, b)

    fun placementOf(uuid: UUID): Int? = placements[uuid]

    private val spectatorTargetTag = Tag.UUID("game:spectator_target")
    internal val spectatorTargetTagInternal: Tag<UUID> get() = spectatorTargetTag
    private val reconnectionManager = ReconnectionManager(this)
    private val respawnManager = RespawnManager(this)
    private val gameEvents = ConcurrentHashMap<String, Task>()
    private val lateJoinManager = LateJoinManager()
    private val activityWatchdog = ActivityWatchdog(this)
    private val overtimeController = OvertimeController()
    private val lobbyLifecycle = LobbyLifecycleManager(this)
    internal fun gameInstanceOrNull(): InstanceContainer? = _gameInstance
    internal fun buildLobbyHotbarInternal(): Hotbar? = buildLobbyHotbar()
    private val damageTakenCounters = ConcurrentHashMap<UUID, AtomicLong>()
    internal val damageTakenCountersInternal: ConcurrentHashMap<UUID, AtomicLong>
        get() = damageTakenCounters
    private val damageRouter = DamageRouter(this)
    private val placements = ConcurrentHashMap<UUID, Int>()
    private var eliminationOrder = 0
    private var totalKillCount = 0
    @Volatile private var coreNode: EventNode<*>? = null
    @Volatile private var freezeEventNode: EventNode<*>? = null
    @Volatile private var gameMechanicsNode: EventNode<*>? = null
    internal val reconnectWindowExpiredInternal: Boolean get() = reconnectionManager.isWindowExpired
    @Volatile private var chatPipeline: GameChatPipeline? = null
    @Volatile var spectatorToolkit: SpectatorToolkit? = null
        private set
    @Volatile private var killFeed: KillFeed? = null
    @Volatile var deathRecapTracker: DeathRecapTracker? = null
        private set
    @Volatile private var rewardDistributor: RewardDistributor? = null
    @Volatile private var ceremony: Ceremony? = null
    @Volatile protected var comboCounter: ComboConfig? = null
    private val replayRecorder = PacketReplayRecorder()
    private val semanticRecorder = ReplayRecorder()
    @Volatile var activeSeasonPasses: List<BattlePassDefinition> = emptyList()
        private set

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
        return MapLoader.resolve(name)
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

        val node = EventNode.all("gamemode-core-${this::class.simpleName?.lowercase()}")

        node.addListener(PlayerSpawnEvent::class.java) { event ->
            if (!event.isFirstSpawn) return@addListener
            handlePlayerJoin(event.player)
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            handlePlayerDisconnect(event.player)
        }

        node.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.player.heldSlot.toInt() != 8) return@addListener
            if (Orbit.hostOwner != event.player.uuid) return@addListener
            if (phase != GamePhase.WAITING && phase != GamePhase.STARTING) return@addListener
            if (tracker.aliveCount < 2) return@addListener
            forceStart()
        }

        handler.addChild(node)
        coreNode = node

        stateMachine.forceTransition(GamePhase.WAITING)
        logger.info { "Game mode installed: ${this::class.simpleName}" }
    }

    override fun shutdown() {
        startingCountdown?.stop()
        gameTimer?.stop()
        endingCountdown?.stop()
        lobbyLifecycle.tearDownLobby()
        scoreboard.uninstall()
        tabList.uninstall()
        GracePeriodManager.uninstall()
        chatPipeline?.uninstall()
        spectatorToolkit?.uninstall()
        comboCounter?.uninstall()
        comboCounter = null
        cleanupReconnectionState()
        cleanupRespawnTimers()
        cleanupLateJoinState()
        cleanupGameEvents()
        cleanupAfkCheck()
        cleanupOvertime()
        cleanupFreezeNode()
        MutatorEngine.cleanup(activeInstance)
        coreNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        coreNode = null
        cleanupGameMechanicsNode()
        cleanupVoidCheck()
        lobbyLifecycle.cancelWaitingActionBarLoop()
        activityWatchdog.cleanupGameplayLoops()
        damageTakenCounters.clear()
        stateMachine.destroy()
    }

    fun eliminate(player: Player) {
        if (phase != GamePhase.PLAYING) return
        if (!tracker.isAlive(player.uuid) && !tracker.isRespawning(player.uuid)) return

        respawnManager.cancelFor(player.uuid)
        tracker.eliminate(player.uuid)
        player.gameMode = MinestomGameMode.SPECTATOR
        player.teleport(spawnPoint)

        eliminationOrder++
        val placement = initialPlayerCount - eliminationOrder + 1
        placements[player.uuid] = placement
        if (placement in 1..3) ProgressionEventBus.publish(ProgressionEvent.TopPlacement(player))
        player.sendMessage(player.translate("orbit.game.placement",
            "place" to placement.toString(),
            "total" to initialPlayerCount.toString()))

        autoSpectateOnEliminate(player)
        spectatorToolkit?.apply(player)
        onPlayerEliminated(player)
        checkGameEnd()
    }

    fun revive(player: Player, position: Pos = spawnPoint) {
        if (phase != GamePhase.PLAYING) return
        if (!tracker.isSpectating(player.uuid)) return

        spectatorToolkit?.remove(player)
        player.removeTag(spectatorTargetTag)
        player.stopSpectating()
        tracker.revive(player.uuid)
        player.gameMode = MinestomGameMode.SURVIVAL
        player.teleport(position)
    }

    fun handleDeath(player: Player, killer: Player? = null) {
        if (phase != GamePhase.PLAYING) return
        if (!tracker.isAlive(player.uuid)) return

        tracker.recordDeath(player.uuid)
        semanticRecorder.recordDeath(player, killer)
        runCatching { AchievementRegistry.progress(player, "deaths", 1) }

        if (killer != null && killer.uuid != player.uuid) {
            tracker.recordKill(killer.uuid)
            runCatching { AchievementRegistry.progress(killer, "kills", 1) }
            totalKillCount++
            if (totalKillCount == 1) {
                broadcastAll { p ->
                    p.sendMessage(p.translate("orbit.game.first_blood", "player" to killer.displayUsername))
                }
            }
            val streak = tracker.streakOf(killer.uuid)
            if (streak > 1) onKillStreak(killer, streak)
            if (streak >= 3) ProgressionEventBus.publish(ProgressionEvent.KillStreak(killer))
        }

        creditAssists(player.uuid, killer?.uuid)

        killFeed?.reportKill(KillEvent(killer = killer, victim = player))
        deathRecapTracker?.sendRecap(player)

        onPlayerDeath(player, killer)

        if (overtimeController.isSuddenDeath) {
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

        reconnectionManager.cancelEliminate(player.uuid)
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
        val currentTargetUuid = player.getTag(spectatorTargetTag)
        val currentIndex = if (currentTargetUuid != null) targets.indexOfFirst { it.uuid == currentTargetUuid } else -1
        val next = targets[(currentIndex + 1) % targets.size]
        player.setTag(spectatorTargetTag, next.uuid)
        player.spectate(next)
        return next
    }

    fun previousSpectatorTarget(player: Player): Player? {
        if (!tracker.isSpectating(player.uuid)) return null
        val targets = resolveSpectatorTargets(player)
        if (targets.isEmpty()) return null
        val currentTargetUuid = player.getTag(spectatorTargetTag)
        val currentIndex = if (currentTargetUuid != null) targets.indexOfFirst { it.uuid == currentTargetUuid } else targets.size
        val prev = targets[(currentIndex - 1 + targets.size) % targets.size]
        player.setTag(spectatorTargetTag, prev.uuid)
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
        tracker.forEachAlive { uuid ->
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
            eliminated.setTag(spectatorTargetTag, target.uuid)
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
                val assister = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(damager)
                if (assister != null) ProgressionEventBus.publish(ProgressionEvent.Assist(assister))
            }
        }
    }

    private fun handlePlayerJoin(player: Player) = withTrace(player.traceId) {
        when (JoinPolicy.decide(player, this)) {
            JoinDecision.Spectator -> applySpectatorJoin(player)
            JoinDecision.Participant -> applyParticipantJoin(player)
            JoinDecision.Reconnect -> applyReconnectJoin(player)
            JoinDecision.LateJoin -> applyLateJoin(player)
        }
    }

    private fun applySpectatorJoin(player: Player) {
        player.gameMode = MinestomGameMode.SPECTATOR
        if (phase == GamePhase.PLAYING) spectatorToolkit?.apply(player)
    }

    private fun applyParticipantJoin(player: Player) {
        tracker.join(player.uuid)
        lobbyLifecycle.applyHotbarTo(player)
        onPlayerJoinWaiting(player)
        if (phase == GamePhase.WAITING) {
            if (Orbit.hostOwner == player.uuid && tracker.aliveCount < settings.timing.minPlayers) {
                player.inventory.setItemStack(8, itemStack(Material.EMERALD) {
                    name(player.translate("orbit.game.force_start"))
                })
            }
            checkMinPlayersThreshold()
        }
    }

    private fun applyReconnectJoin(player: Player) {
        val previousState = tracker.stateOf(player.uuid) as? PlayerState.Disconnected

        reconnectionManager.cancelEliminate(player.uuid)
        ReconnectionStore.delete(player.uuid)
        tracker.reconnect(player.uuid)

        if (previousState?.wasRespawning == true) {
            val pos = buildRespawnPosition(player)
            player.teleport(pos)
            player.gameMode = MinestomGameMode.SURVIVAL
            if (settings.respawn?.clearInventoryOnRespawn == true) player.inventory.clear()
            buildRespawnKit()?.apply(player)
            applyRespawnInvincibility(player)
        } else {
            player.gameMode = MinestomGameMode.SURVIVAL
        }

        onPlayerReconnected(player)
    }

    private fun applyLateJoin(player: Player) {
        tracker.join(player.uuid)

        if (isTeamMode) {
            val smallestTeam = tracker.allTeams().minByOrNull { tracker.activeInTeam(it).size }
            if (smallestTeam != null) tracker.assignTeam(player.uuid, smallestTeam)
        }

        val respawnConfig = settings.respawn
        if (respawnConfig != null && respawnConfig.maxLives > 0) {
            tracker.setLives(player.uuid, respawnConfig.maxLives)
        }

        if (settings.lateJoin?.joinAsSpectator == true) {
            player.gameMode = MinestomGameMode.SPECTATOR
        } else {
            player.gameMode = MinestomGameMode.SURVIVAL
        }

        onLateJoin(player)
    }

    private fun handlePlayerDisconnect(player: Player) = withTrace(player.traceId) {
        killFeed?.removePlayer(player.uuid)
        when (val action = DisconnectPolicy.decide(player, this)) {
            DisconnectAction.CleanRemove -> applyCleanRemove(player)
            DisconnectAction.SimpleRemove -> tracker.remove(player.uuid)
            DisconnectAction.CombatLog -> applyCombatLogDisconnect(player)
            is DisconnectAction.ScheduleEliminate -> applyScheduledDisconnect(player, action.delayTicks)
            DisconnectAction.EliminateNow -> applyImmediateDisconnect(player)
        }
    }

    private fun applyCleanRemove(player: Player) {
        tracker.remove(player.uuid)
        lobbyLifecycle.removeHotbarFrom(player)
        onPlayerLeaveWaiting(player)
        if (phase == GamePhase.STARTING && tracker.aliveCount < settings.timing.minPlayers) {
            startingCountdown?.stop()
            startingCountdown = null
            stateMachine.transition(GamePhase.WAITING)
        }
    }

    private fun applyCombatLogDisconnect(player: Player) {
        respawnManager.cancelFor(player.uuid)
        player.removeTag(spectatorTargetTag)
        tracker.eliminate(player.uuid)
        onCombatLog(player)
        onPlayerEliminated(player)
        checkGameEnd()
    }

    private fun applyScheduledDisconnect(player: Player, delayTicks: Int) {
        respawnManager.cancelFor(player.uuid)
        player.removeTag(spectatorTargetTag)
        tracker.disconnect(player.uuid)
        ReconnectionStore.save(
            player.uuid,
            ReconnectionData(
                serverName = Orbit.serverName,
                gameMode = Orbit.gameMode ?: "",
                disconnectedAt = System.currentTimeMillis(),
            ),
        )
        if (delayTicks > 0) reconnectionManager.scheduleEliminate(player.uuid, delayTicks)
        onPlayerDisconnected(player)
        checkGameEnd()
    }

    private fun applyImmediateDisconnect(player: Player) {
        respawnManager.cancelFor(player.uuid)
        player.removeTag(spectatorTargetTag)
        tracker.eliminate(player.uuid)
        onPlayerEliminated(player)
        checkGameEnd()
    }

    internal fun checkGameEndInternal() = checkGameEnd()

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
        respawnManager.schedule(player, config)
    }

    private fun applyRespawnInvincibility(player: Player) {
        respawnManager.applyInvincibility(player)
    }

    private fun tryClaimLateJoinSlot(): Boolean {
        val config = settings.lateJoin ?: return false
        return lateJoinManager.tryClaimSlot(config.maxLateJoiners)
    }

    internal fun tryClaimLateJoinSlotInternal(): Boolean = tryClaimLateJoinSlot()

    private fun startAfkCheck() {
        activityWatchdog.startAfkCheck()
    }

    private fun startVoidCheck() {
        activityWatchdog.startVoidCheck()
    }

    private fun startOvertime() {
        val config = settings.overtime ?: return
        onOvertimeStart()
        overtimeController.start(config.durationSeconds, config.suddenDeath) {
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
            damageRouter.install(node)
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

        hasListeners = true
        node.addListener(PlayerChatEvent::class.java) { event ->
            if (phase == GamePhase.PLAYING) {
                semanticRecorder.recordChat(event.player, event.rawMessage)
            }
        }

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (phase == GamePhase.PLAYING && tracker.isAlive(event.player.uuid)) {
                ProgressionEventBus.publish(ProgressionEvent.BlockMined(event.player))
            }
        }

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (phase == GamePhase.PLAYING && tracker.isAlive(event.player.uuid)) {
                ProgressionEventBus.publish(ProgressionEvent.BlockPlaced(event.player))
            }
        }

        if (hasListeners) {
            MinecraftServer.getGlobalEventHandler().addChild(node)
            gameMechanicsNode = node
        }
    }

    private fun cleanupReconnectionState() {
        reconnectionManager.cleanup()
        for (uuid in tracker.disconnected) {
            ReconnectionStore.delete(uuid)
        }
    }

    private fun cleanupRespawnTimers() {
        respawnManager.cleanup()
    }

    private fun cleanupLateJoinState() {
        lateJoinManager.cleanup()
    }

    private fun cleanupGameEvents() {
        gameEvents.values.forEach { it.cancel() }
        gameEvents.clear()
    }

    private fun cleanupAfkCheck() {
        activityWatchdog.cleanupAfkCheck()
    }

    private fun cleanupOvertime() {
        overtimeController.cleanup()
    }

    private fun cleanupFreezeNode() {
        freezeEventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        freezeEventNode = null
    }

    private fun cleanupGameMechanicsNode() {
        gameMechanicsNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        gameMechanicsNode = null
    }

    private fun stopGameplayTimersAndCleanup() {
        BotLobbyFiller.stopFilling(Orbit.serverName)
        gameTimer?.stop()
        gameTimer = null
        activityWatchdog.cleanupGameplayLoops()
        GracePeriodManager.clearAll()
        cleanupReconnectionState()
        cleanupRespawnTimers()
        cleanupLateJoinState()
        cleanupGameEvents()
        cleanupAfkCheck()
        cleanupOvertime()
        cleanupGameMechanicsNode()
        cleanupVoidCheck()
    }

    private fun cleanupVoidCheck() {
        activityWatchdog.cleanupVoidCheck()
    }

    private fun enterWaiting() {
        resetSubsystemsForNewRound()
        lobbyLifecycle.installLobbyAndHotbar()
        lobbyLifecycle.teleportPlayersToLobby()

        onWaitingStart()
        onGameReset()
        checkMinPlayersThreshold()
        lobbyLifecycle.startWaitingActionBarLoop()

        assertInvariants(GamePhase.WAITING)
    }

    private fun resetSubsystemsForNewRound() {
        startingCountdown?.stop()
        startingCountdown = null
        cleanupFreezeNode()
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
        comboCounter?.uninstall()
        comboCounter = null
        lobbyInstance.players.forEach { it.removeTag(spectatorTargetTag) }
        _gameInstance?.players?.forEach { it.removeTag(spectatorTargetTag) }
        activityWatchdog.cleanupGameplayLoops()
        tracker.clear()
        lastEndResult = null
        gameStartTime = 0L
        initialPlayerCount = 0
        gamePartySnapshot = emptyMap()
        partyBonuses = emptyMap()
        placements.clear()
        eliminationOrder = 0
        totalKillCount = 0
    }

    private fun assertInvariants(expected: GamePhase) {
        if (!ASSERTIONS_ENABLED) return
        when (expected) {
            GamePhase.WAITING -> {
                check(lobbyLifecycle.hasLobby()) { "WAITING: lobby must be installed" }
                check(freezeEventNode == null) { "WAITING: freezeEventNode must be cleared" }
                check(gameMechanicsNode == null) { "WAITING: gameMechanicsNode must be cleared" }
                check(gameStartTime == 0L) { "WAITING: gameStartTime must be reset" }
            }
            GamePhase.STARTING -> {
                check(startingCountdown != null) { "STARTING: startingCountdown must be running" }
            }
            GamePhase.PLAYING -> {
                check(gameStartTime > 0L) { "PLAYING: gameStartTime must be set" }
                check(initialPlayerCount > 0) { "PLAYING: initialPlayerCount must be > 0" }
                check(startingCountdown == null) { "PLAYING: startingCountdown must be cleared" }
            }
            GamePhase.ENDING -> {
                check(lastEndResult != null) { "ENDING: lastEndResult must be stored" }
                check(gameMechanicsNode == null) { "ENDING: gameMechanicsNode must be cleared" }
            }
        }
    }

    private fun enterStarting() {
        lobbyLifecycle.cancelWaitingActionBarLoop()

        installFreezeNode()

        startingCountdown = countdown(settings.timing.countdownSeconds.seconds) {
            onTick { remaining ->
                onCountdownTick(remaining)
                val seconds = remaining.inWholeSeconds.toInt()
                if (seconds in 1..3) {
                    lobbyInstance.players.forEach { p ->
                        p.playSound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, 1f, if (seconds == 1) 2f else 1f)
                    }
                } else if (seconds == 0) {
                    lobbyInstance.players.forEach { p ->
                        p.playSound(SoundEvent.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                    }
                }
            }
            onComplete {
                startingCountdown = null
                cleanupFreezeNode()
                stateMachine.transition(GamePhase.PLAYING)
            }
        }.also { it.start() }
        assertInvariants(GamePhase.STARTING)
    }

    private fun enterPlaying() {
        tearDownLobbyAndCountdown()
        initializeGameSession()
        consumeHostTicketIfNeeded()
        applyTeamAssignments()
        respawnManager.applyInitialPlayingState()

        val alivePlayers = collectAlivePlayers()
        transferAlivePlayersToGameInstance(alivePlayers)
        alivePlayers.forEach { tracker.markActivity(it.uuid) }
        onGameSetup(alivePlayers)
        for (player in alivePlayers) {
            runCatching { AchievementRegistry.progress(player, "games_played", 1) }
        }

        installGameSubsystems()
        applyGracePeriodIfConfigured(alivePlayers)
        startGameTimerIfConfigured(alivePlayers)

        reconnectionManager.startWindow(settings.timing.reconnectWindowSeconds)
        lateJoinManager.startWindow(settings.lateJoin?.windowSeconds ?: 0)

        startReplayAndProgressionTasks(alivePlayers)

        onPlayingStart()
        assertInvariants(GamePhase.PLAYING)
    }

    private fun tearDownLobbyAndCountdown() {
        lobbyLifecycle.tearDownLobby()
        startingCountdown?.stop()
        startingCountdown = null
        cleanupFreezeNode()
    }

    private fun initializeGameSession() {
        gameStartTime = System.currentTimeMillis()
        initialPlayerCount = tracker.aliveCount
        activeSeasonPasses = BattlePassRegistry.activePasses()
        gamePartySnapshot = PartyBonusCalculator.buildPartySnapshot(tracker.all)
        partyBonuses = tracker.all.associateWith {
            PartyBonusCalculator.calculateBonus(it, gamePartySnapshot)
        }
    }

    private fun consumeHostTicketIfNeeded() {
        val owner = Orbit.hostOwner ?: return
        if (RankManager.hasPermission(owner, "*")) return
        val consumed = HostTicketStore.executeOnKey(owner, ConsumeTicketProcessor())
        if (!consumed) logger.warn { "Failed to consume host ticket for $owner (balance may be zero)" }
    }

    private fun applyTeamAssignments() {
        val teamConfig = settings.teams ?: return
        if (teamConfig.teamCount <= 0) return
        val assignments = assignTeams(tracker.alive.toList())
        for ((uuid, team) in assignments) tracker.assignTeam(uuid, team)
        val grouped = assignments.entries.groupBy({ it.value }, { it.key })
        onTeamsAssigned(grouped)
    }

    private fun collectAlivePlayers(): List<Player> {
        val alivePlayers = mutableListOf<Player>()
        tracker.forEachAlive { uuid ->
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.let(alivePlayers::add)
        }
        return alivePlayers
    }

    private fun transferAlivePlayersToGameInstance(alivePlayers: List<Player>) {
        if (!isDualInstance) return
        val transfers = alivePlayers.map { it.setInstance(gameInstance, spawnPoint) }
        CompletableFuture.allOf(*transfers.toTypedArray()).join()
    }

    private fun installGameSubsystems() {
        comboCounter = buildComboCounter()
        comboCounter?.install()

        chatPipeline = buildChatPipeline()
        chatPipeline?.install()

        installGameMechanicsNode()
        startAfkCheck()
        startVoidCheck()

        spectatorToolkit = buildSpectatorToolkit()
        spectatorToolkit?.install()

        killFeed = buildKillFeed()
        deathRecapTracker = buildDeathRecapTracker()?.also { it.gameStartTime = gameStartTime }
        rewardDistributor = buildRewardDistributor()
    }

    private fun applyGracePeriodIfConfigured(alivePlayers: List<Player>) {
        if (settings.timing.gracePeriodSeconds <= 0) return
        val config = gracePeriod("game-grace") {
            duration(settings.timing.gracePeriodSeconds.seconds)
        }
        alivePlayers.forEach { GracePeriodManager.apply(it, config.name) }
    }

    private fun startGameTimerIfConfigured(alivePlayers: List<Player>) {
        val resolvedDuration = resolveGameDuration()
        if (resolvedDuration <= 0) return
        gameTimer = minigameTimer("game-timer") {
            duration(resolvedDuration.seconds)
            onEnd {
                gameTimer = null
                handleTimerExpired()
            }
        }.also {
            it.addAllViewers(alivePlayers)
            it.start()
        }
    }

    private fun startReplayAndProgressionTasks(alivePlayers: List<Player>) {
        replayRecorder.start(gameInstance)
        semanticRecorder.start(gameInstance)
        for (player in alivePlayers) {
            semanticRecorder.recordPlayerJoin(player)
        }
        activityWatchdog.startGameplayLoops(semanticRecorder::recordPosition)
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
        stopGameplayTimersAndCleanup()

        tracker.forEachAlive { uuid ->
            placements[uuid] = 1
            val winner = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            if (winner != null) ProgressionEventBus.publish(ProgressionEvent.TopPlacement(winner))
        }

        val result = lastEndResult ?: matchResult { draw() }

        MatchResultManager.store(result)
        OrbitAudit.gameEnd(result, Orbit.gameMode, System.currentTimeMillis() - gameStartTime, initialPlayerCount)

        result.winner?.first?.let { winnerUuid ->
            val winnerPlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(winnerUuid)
            if (winnerPlayer != null) {
                runCatching {
                    AchievementRegistry.progress(winnerPlayer, "wins", 1)
                    OrbitAudit.achievementGrant(winnerPlayer, "wins", 1)
                }
            }
        }

        persistGameStats(result)
        if (Orbit.hostOwner == null) {
            runCatching { applyRatingChanges() }
                .onFailure { logger.warn { "Failed to apply rating changes: ${it.message}" } }
        } else {
            logger.info { "Skipping rating changes for hosted game (host=${Orbit.hostOwner})" }
        }

        activityWatchdog.cleanupPositionRecording()
        val semanticData = semanticRecorder.stop()

        if (replayRecorder.isRecording && ReplayStorage.isInitialized()) {
            val replayName = "${Orbit.serverName}-${System.currentTimeMillis()}"
            val gm = Orbit.gameMode ?: "unknown"
            val map = Orbit.mapName ?: ""
            PendingReplayFlushes.mark(replayName, replayName)
            Thread.startVirtualThread {
                runCatching {
                    val replayFile = replayRecorder.buildReplayFile(
                        gameInstance,
                        replayName,
                        gm,
                        map,
                    )
                    ReplayStorage.saveBinary(replayName, replayFile)
                    PendingReplayFlushes.complete(replayName)
                    logger.info { "Replay saved: $replayName (${replayFile.rawPackets.size} packets)" }

                    val semanticFrames = semanticData.frames.map { it.tickOffset to it }
                    val metadata = ReplayMetadata(
                        gameMode = gm,
                        mapName = map,
                        recordedAt = System.currentTimeMillis(),
                        playerCount = initialPlayerCount,
                        durationTicks = semanticData.durationTicks,
                    )
                    ReplayStorage.save("$replayName-semantic", semanticData, metadata)

                    val highlights = ReplayHighlights.detect(semanticFrames, metadata)
                    for (highlight in highlights) {
                        if (highlight.type == HighlightType.MULTI_KILL ||
                            highlight.type == HighlightType.CLUTCH) {
                            runCatching {
                                NetworkMessenger.publish(ReplayHighlightMessage(
                                    replayName = replayName,
                                    gameMode = gm,
                                    mapName = map,
                                    playerName = semanticData.playerNames[highlight.playerUuid] ?: highlight.playerUuid.toString(),
                                    highlightType = highlight.type.name,
                                    description = highlight.description,
                                ))
                            }
                        }
                    }
                }.onFailure { logger.warn { "Failed to save replay: ${it.message}" } }
            }
        } else if (replayRecorder.isRecording) {
            replayRecorder.stop(gameInstance)
        }

        runCatching { rewardDistributor?.distribute(result, tracker.all, partyBonuses) }
            .onFailure { logger.error(it) { "Reward distribution failed" } }

        for (uuid in tracker.all) {
            val bonus = partyBonuses[uuid] ?: continue
            if (bonus.bonusPercent <= 0) continue
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: continue
            player.sendMessage(player.translate("orbit.party.bonus_applied",
                "percent" to bonus.bonusPercent.toString(),
                "members" to bonus.partyMembersInGame.toString(),
            ))
        }

        val allPlayers = gameInstance.players.toList()
        MatchResultDisplay.broadcast(allPlayers, result)

        ceremony = buildCeremony(result)
        ceremony?.start(allPlayers)

        onEndingStart(result)
        assertInvariants(GamePhase.ENDING)

        endingCountdown = countdown(settings.timing.endingDurationSeconds.seconds) {
            onComplete {
                endingCountdown = null
                onEndingComplete()
                if (!Orbit.shuttingDown.compareAndSet(false, true)) return@onComplete
                logger.info { "Game ended, terminating server..." }
                val gameMode = Orbit.gameMode
                val playerIds = gameInstance.players.map { it.uuid }
                if (gameMode != null && playerIds.isNotEmpty()) {
                    runCatching { NetworkMessenger.publish(GameEndMessage(playerIds, gameMode)) }
                }
                Thread.startVirtualThread {
                    runCatching { Store.flushAll() }
                    Orbit.app.stop().join()
                }
            }
        }.also { it.start() }
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

    protected fun autoBalanceTeams(teamNames: List<String>) {
        val alivePlayers = mutableListOf<Player>()
        tracker.forEachAlive { uuid ->
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.let(alivePlayers::add)
        }
        val balanced = TeamBalance.balance(alivePlayers, teamNames.size)
        for ((teamIndex, players) in balanced) {
            val teamName = teamNames[teamIndex]
            for (player in players) {
                tracker.assignTeam(player.uuid, teamName)
            }
        }
    }

    internal companion object {
        const val RESPAWN_GRACE_NAME = "respawn-invincibility"
        private val ASSERTIONS_ENABLED: Boolean =
            System.getProperty("orbit.gamemode.assertions")?.toBooleanStrictOrNull() ?: false
        const val ASSIST_WINDOW_MILLIS = 10_000L
    }
}
