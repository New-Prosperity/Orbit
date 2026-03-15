package me.nebula.orbit.mode.game.battleroyale

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.ranking.AggregateOperation
import me.nebula.gravity.ranking.RankingReportStore
import me.nebula.gravity.session.SessionStore
import me.nebula.gravity.stats.IncrementStatsProcessor
import me.nebula.gravity.stats.StatsStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.config.PlaceholderResolver
import me.nebula.orbit.mode.config.placeholderResolver
import me.nebula.orbit.progression.BattlePassManager
import me.nebula.orbit.progression.mission.MissionTracker
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.orbit.utils.achievement.AchievementTriggerManager
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.mode.game.GameSettings
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.chestloot.ChestLootManager
import me.nebula.orbit.utils.ceremony.Ceremony
import me.nebula.orbit.utils.ceremony.ceremony
import me.nebula.orbit.utils.deathrecap.DeathRecapTracker
import me.nebula.orbit.utils.gamechat.DeadPlayerDimProcessor
import me.nebula.orbit.utils.gamechat.GameChatPipeline
import me.nebula.orbit.utils.gamechat.RankPrefixProcessor
import me.nebula.orbit.utils.gamechat.SpectatorIsolationProcessor
import me.nebula.orbit.utils.gamechat.gameChatPipeline
import me.nebula.orbit.utils.killfeed.KillFeed
import me.nebula.orbit.utils.killfeed.killFeed
import me.nebula.orbit.utils.rewards.RewardDistributor
import me.nebula.orbit.utils.rewards.rewardDistributor
import me.nebula.orbit.utils.hotbar.Hotbar
import me.nebula.orbit.utils.hotbar.hotbar
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.mapgen.BattleRoyaleMapGenerator
import me.nebula.orbit.utils.mapgen.GeneratedMap
import me.nebula.orbit.utils.mapgen.MapPresets
import me.nebula.orbit.utils.matchresult.MatchResult
import me.nebula.orbit.utils.matchresult.matchResult
import me.nebula.orbit.utils.deathrecap.DamageEntry
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.scheduler.repeat
import me.nebula.orbit.utils.spectatortoolkit.SpectatorToolkit
import me.nebula.orbit.utils.spectatortoolkit.spectatorToolkit
import me.nebula.orbit.utils.stattracker.StatTracker
import me.nebula.orbit.utils.worldborder.ManagedWorldBorder
import me.nebula.orbit.utils.worldborder.managedWorldBorder
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task

class BattleRoyaleMode(worldPathOverride: String? = null) : GameMode() {

    private val logger = logger("BattleRoyaleMode")
    private val season = SeasonConfig.current

    override val settings: GameSettings = GameSettings(
        worldPath = worldPathOverride ?: season.worldPath,
        preloadRadius = season.preloadRadius,
        spawn = season.spawn,
        scoreboard = season.scoreboard,
        tabList = season.tabList,
        lobby = season.lobby,
        hotbar = season.hotbar,
        timing = season.timing,
        cosmetics = season.cosmetics,
        lobbyWorld = season.lobbyWorld,
    )

    private var _generatedMap: GeneratedMap? = null
    private val generatedMap: GeneratedMap?
        get() {
            if (_generatedMap == null) {
                val mapConfig = season.mapPreset?.let { MapPresets[it] } ?: return null
                _generatedMap = BattleRoyaleMapGenerator.generate(mapConfig)
            }
            return _generatedMap
        }

    override fun createGameInstance(): InstanceContainer =
        generatedMap?.instance ?: super.createGameInstance()

    override val spawnPoint: Pos
        get() = generatedMap?.center ?: super.spawnPoint

    private val brDeathRecapTracker = DeathRecapTracker()
    private val lastAttackerTag = Tag.UUID("br_last_attacker")
    private val lastAttackerTimeTag = Tag.Long("br_last_attacker_time")
    private var worldBorder: ManagedWorldBorder? = null
    private var borderDamageTask: Task? = null
    private val borderPhasesTasks = mutableListOf<Task>()
    private var currentBorderDamage = season.borderDamagePerSecond
    @Volatile private var deathmatchActive = false
    @Volatile private var spawnBlocking = false
    private var spawnModeResult: SpawnModeResult? = null

    init {
        if (season.goldenHead.enabled) GoldenHeadManager.configure(season.goldenHead)
        SeasonConfig.current.lootTables.forEach { ChestLootManager.register(it) }
    }

    override fun buildPlaceholderResolver(): PlaceholderResolver = placeholderResolver {
        global("online") { SessionStore.cachedSize.toString() }
        global("server") { Orbit.serverName }
        global("alive") { tracker.aliveCount.toString() }
        global("phase") { phase.name }
        global("deathmatch") { if (deathmatchActive) "ACTIVE" else "" }
        perPlayer("kills") { player -> StatTracker.get(player, "kills").toString() }
    }

    override fun buildLobbyHotbar(): Hotbar = hotbar("br-lobby") {
        slot(3, itemStack(Material.BOOK) {
            name("<green><bold>Kit Selector")
        }) { player ->
            BattleRoyaleKitManager.openKitMenu(player)
        }
        slot(5, itemStack(Material.PAPER) {
            name("<yellow>Vote on Settings <gray>(Right Click)")
        }) { player ->
            BattleRoyaleVoteManager.openCategoryMenu(player)
        }
    }

    private val votedValues = mutableMapOf<String, Int>()

    override fun onGameSetup(players: List<Player>) {
        for (cat in SeasonConfig.current.voteCategories) {
            votedValues[cat.id] = BattleRoyaleVoteManager.resolveValue(cat.id)
        }

        broadcastAll { p ->
            for (cat in SeasonConfig.current.voteCategories) {
                p.sendMessage(p.translate("orbit.game.br.vote.result",
                    "category" to p.translateRaw(cat.nameKey),
                    "option" to BattleRoyaleVoteManager.resolveOptionName(p, cat.id),
                ))
            }
        }
        BattleRoyaleVoteManager.clear()

        StatTracker.clear()

        if (season.goldenHead.enabled) GoldenHeadManager.install()
        LegendaryListener.install()

        worldBorder = gameInstance.managedWorldBorder {
            diameter(season.border.initialDiameter)
            center(season.border.centerX, season.border.centerZ)
        }

        val mapRadius = generatedMap?.mapRadius ?: (season.border.initialDiameter / 2).toInt()
        val result = SpawnModeExecutor.execute(
            config = season.spawnMode,
            players = players,
            instance = gameInstance,
            center = spawnPoint,
            mapRadius = mapRadius,
            onPlayerReady = { player, pos ->
                player.teleport(pos)
                player.gameMode = net.minestom.server.entity.GameMode.SURVIVAL
                applyVotedHealth(player)
                BattleRoyaleKitManager.resolveKit(player).apply(player)
            },
            onBusComplete = { spawnBlocking = false },
        )

        spawnModeResult = result
        spawnBlocking = result.pvpBlocked
    }

    override fun onPlayingStart() {
        if (season.borderPhases.isNotEmpty()) {
            scheduleBorderPhases(borderSpeedMultiplier())
        } else {
            scheduleLegacyBorder()
        }

        borderDamageTask = repeat(20) {
            if (phase != GamePhase.PLAYING) return@repeat
            val border = worldBorder ?: return@repeat
            for (uuid in tracker.alive.toSet()) {
                val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: continue
                if (border.isOutside(player.position)) {
                    player.damage(DamageType.OUT_OF_WORLD, currentBorderDamage)
                }
            }
        }

        if (season.deathmatch.enabled) {
            scheduleDeathmatchCheck()
        }
    }

    override fun resolveGameDuration(): Int {
        val voted = votedValues["duration"] ?: 0
        return if (voted > 0) voted else settings.timing.gameDurationSeconds
    }

    override fun checkWinCondition(): MatchResult? {
        if (tracker.aliveCount > 1) return null

        val winnerUuid = tracker.alive.firstOrNull()
        val winner = winnerUuid?.let { MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it) }

        if (winner != null) {
            BattleRoyaleKitManager.awardXp(winner, "win")
        }

        return buildResult(winner)
    }

    override fun onPlayerEliminated(player: Player) {
        BattleRoyaleKitManager.awardXp(player, "survival")

        val killerUuid = resolveKiller(player)
        val killer = killerUuid?.let { MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it) }
        val killerName = killer?.username ?: "?"

        broadcastAll { p ->
            p.sendMessage(p.translate(
                "orbit.game.br.elimination",
                "victim" to player.username,
                "killer" to killerName,
            ))
        }

        if (season.deathmatch.enabled && !deathmatchActive && tracker.aliveCount <= season.deathmatch.triggerAtPlayers) {
            triggerDeathmatch()
        }
    }

    override fun onPlayerDamaged(victim: Player, attacker: Player?, amount: Float, event: EntityDamageEvent): Boolean {
        if (spawnBlocking && attacker != null) return false

        if (attacker != null && attacker.uuid != victim.uuid) {
            victim.setTag(lastAttackerTag, attacker.uuid)
            victim.setTag(lastAttackerTimeTag, System.currentTimeMillis())
        }

        brDeathRecapTracker.recordDamage(victim.uuid, DamageEntry(
            attackerUuid = attacker?.uuid,
            attackerName = attacker?.username ?: event.damage.type.key().value(),
            amount = amount,
            source = if (attacker != null) "PLAYER" else event.damage.type.key().value(),
        ))

        if (victim.health - amount <= 0) {
            event.isCancelled = true
            victim.health = victim.getAttributeValue(Attribute.MAX_HEALTH).toFloat()

            val killerUuid = resolveKiller(victim)
            val killer = killerUuid?.let { MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it) }

            if (killer != null && tracker.isAlive(killer.uuid)) {
                StatTracker.increment(killer, "kills")
                BattleRoyaleKitManager.awardXp(killer, "kill")
                LegendaryListener.notifyKill(killer, victim)
                MissionTracker.onKill(killer)
                BattlePassManager.addXpToAll(killer, 10)

                val killStreak = StatTracker.get(killer, "kills").toInt()
                if (killStreak == 2) AchievementRegistry.complete(killer, "double_trouble")
                if (killStreak == 5) AchievementRegistry.complete(killer, "unstoppable")
                if (killStreak == 10) AchievementRegistry.complete(killer, "rampage")

                if (season.goldenHead.enabled) {
                    val headName = { key: String -> killer.translateRaw(key) }
                    killer.inventory.addItemStack(GoldenHeadManager.createStack(headName))
                }
            }

            brDeathRecapTracker.sendRecap(victim)
            val recap = brDeathRecapTracker.buildRecap(victim)

            eliminate(victim)

            if (recap?.killerUuid != null) {
                val killerTarget = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(recap.killerUuid)
                if (killerTarget != null) {
                    victim.spectate(killerTarget)
                    delay(60) {
                        if (victim.gameMode == net.minestom.server.entity.GameMode.SPECTATOR) {
                            victim.stopSpectating()
                        }
                    }
                }
            }

            brDeathRecapTracker.clearPlayer(victim.uuid)
            victim.removeTag(lastAttackerTag)
            victim.removeTag(lastAttackerTimeTag)
            return false
        }

        return true
    }

    override fun onGameReset() {
        borderDamageTask?.cancel()
        borderDamageTask = null
        borderPhasesTasks.forEach { it.cancel() }
        borderPhasesTasks.clear()
        worldBorder?.setDiameter(season.border.initialDiameter)
        worldBorder = null
        StatTracker.clear()
        brDeathRecapTracker.clear()
        deathmatchActive = false
        spawnBlocking = false
        spawnModeResult?.let { SpawnModeExecutor.cleanup(it) }
        spawnModeResult = null
        currentBorderDamage = season.borderDamagePerSecond

        ChestLootManager.clear()
        LegendaryListener.uninstall()
        GoldenHeadManager.uninstall()
        LegendaryRegistry.clear()
        BattleRoyaleVoteManager.clear()
        votedValues.clear()

        if (isDualInstance && _generatedMap != null) {
            _generatedMap = null
            invalidateGameInstance()
        }
    }

    override fun buildTimeExpiredResult(): MatchResult {
        val topKiller = StatTracker.top("kills", 1).firstOrNull()
        val winner = topKiller?.let { (uuid, _) ->
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
        }
        if (winner != null) BattleRoyaleKitManager.awardXp(winner, "win")
        return buildResult(winner)
    }

    override fun persistGameStats(result: MatchResult) {
        val gameDuration = result.duration.toMillis()

        for (uuid in StatTracker.players()) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            val playerName = player?.username ?: uuid.toString().take(8)
            val kills = StatTracker.get(uuid, "kills").toInt()
            val isWinner = result.winner?.first == uuid

            StatsStore.submitToKey(uuid, IncrementStatsProcessor(
                game = "battleroyale",
                wins = if (isWinner) 1 else 0,
                losses = if (!isWinner && !result.isDraw) 1 else 0,
                kills = kills,
                deaths = tracker.deathsOf(uuid),
                gamesPlayed = 1,
                playtime = gameDuration,
            ))

            if (kills > 0) {
                RankingReportStore.record("br_kills", playerName, uuid, kills.toDouble(), AggregateOperation.SUM)
            }

            if (isWinner) {
                RankingReportStore.record("br_wins", playerName, uuid, 1.0, AggregateOperation.SUM)
            }

            if (player != null) {
                MissionTracker.onGamePlayed(player)
                BattlePassManager.addXpToAll(player, 50)

                if (isWinner) {
                    MissionTracker.onWin(player)
                    BattlePassManager.addXpToAll(player, 100)

                    if (tracker.deathsOf(uuid) == 0) {
                        AchievementRegistry.complete(player, "invincible")
                    }
                }

                val stats = StatsStore.load(uuid)
                if (stats != null) {
                    val brStats = stats.stats["battleroyale"]
                    if (brStats != null) {
                        AchievementTriggerManager.evaluate(player, "br_games_played", brStats.gamesPlayed.toLong())
                        AchievementTriggerManager.evaluate(player, "br_kills", brStats.kills.toLong())
                        AchievementTriggerManager.evaluate(player, "br_wins", brStats.wins.toLong())
                    }
                }
            }
        }
    }

    override fun buildChatPipeline(): GameChatPipeline = gameChatPipeline {
        processor("rank_prefix", RankPrefixProcessor())
        processor("dead_dim", DeadPlayerDimProcessor(tracker))
        processor("spectator_isolation", SpectatorIsolationProcessor(tracker))
    }

    override fun buildSpectatorToolkit(): SpectatorToolkit {
        val mode = this
        return spectatorToolkit {
            onNext { player -> mode.nextSpectatorTarget(player) }
            onPrevious { player -> mode.previousSpectatorTarget(player) }
            alivePlayers {
                mode.tracker.alive.mapNotNull {
                    MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it)
                }
            }
        }
    }

    override fun buildKillFeed(): KillFeed = killFeed {
        tracker(this@BattleRoyaleMode.tracker)
        firstBlood("orbit.killfeed.first_blood")
        multiKillWindow(5000L)
        multiKill(2, "orbit.killfeed.double_kill")
        multiKill(3, "orbit.killfeed.triple_kill")
        multiKill(4, "orbit.killfeed.quad_kill")
        multiKill(5, "orbit.killfeed.penta_kill")
        streak(3, "orbit.killfeed.streak_3")
        streak(5, "orbit.killfeed.streak_5")
        streak(10, "orbit.killfeed.streak_10")
        broadcastTo { gameInstance.players }
    }

    override fun buildDeathRecapTracker(): DeathRecapTracker = DeathRecapTracker()

    override fun buildRewardDistributor(): RewardDistributor = rewardDistributor {
        announcement("orbit.reward.earned")
        participation("coins", 10.0)
        perKill("coins", 5.0)
        winnerRule {
            reward("coins", 50.0)
        }
        topKillerRule {
            reward("coins", 25.0)
        }
    }

    override fun buildCeremony(result: MatchResult): Ceremony = ceremony(gameInstance, result) {
        podiumPosition(1, spawnPoint.add(0.0, 0.0, 0.0))
        podiumPosition(2, spawnPoint.add(3.0, 0.0, 0.0))
        podiumPosition(3, spawnPoint.add(-3.0, 0.0, 0.0))
        fireworks(interval = 15, max = 20)
        personalStats("kills", "deaths", "damage_dealt")
        spectateWinner()
    }

    private fun resolveKiller(target: Player): java.util.UUID? {
        val attackerUuid = target.getTag(lastAttackerTag) ?: return null
        val attackTime = target.getTag(lastAttackerTimeTag) ?: return null
        return if (System.currentTimeMillis() - attackTime < 10_000L) attackerUuid else null
    }

    private fun scheduleLegacyBorder() {
        if (season.border.shrinkStartSeconds > 0) {
            val task = delay(season.border.shrinkStartSeconds * 20) {
                broadcastAll { p ->
                    p.sendMessage(p.translate("orbit.game.br.border_shrinking"))
                }
                worldBorder?.shrinkTo(season.border.finalDiameter, season.border.shrinkDurationSeconds.toDouble())
            }
            borderPhasesTasks.add(task)
        } else {
            worldBorder?.shrinkTo(season.border.finalDiameter, season.border.shrinkDurationSeconds.toDouble())
        }
    }

    private fun scheduleBorderPhases(speedMultiplier: Double = 1.0) {
        for (phase in season.borderPhases) {
            val startDelay = (phase.startAfterSeconds * speedMultiplier).toInt()
            val shrinkDuration = (phase.shrinkDurationSeconds * speedMultiplier).toInt()
            val task = scheduleGameEvent("border_phase_${phase.startAfterSeconds}", startDelay * 20) {
                currentBorderDamage = phase.damagePerSecond
                broadcastAll { p ->
                    p.sendMessage(p.translate("orbit.game.br.border_shrinking"))
                }
                worldBorder?.shrinkTo(phase.targetDiameter, shrinkDuration.toDouble())
            }
            borderPhasesTasks.add(task)
        }
    }

    private fun scheduleDeathmatchCheck() {
        scheduleGameEvent("deathmatch_check", 600 * 20) {
            if (!deathmatchActive && tracker.aliveCount <= season.deathmatch.triggerAtPlayers) {
                triggerDeathmatch()
            }
        }
    }

    private fun triggerDeathmatch() {
        if (deathmatchActive) return
        deathmatchActive = true

        broadcastAll { p ->
            p.sendMessage(p.translate("orbit.game.br.deathmatch_start"))
        }

        if (season.deathmatch.teleportToCenter) {
            val center = generatedMap?.center ?: Pos(season.border.centerX, season.spawn.y, season.border.centerZ)
            broadcastAlive { player ->
                player.teleport(center)
            }
        }

        worldBorder?.shrinkTo(season.deathmatch.borderDiameter, season.deathmatch.borderShrinkSeconds.toDouble())
    }

    private fun applyVotedHealth(player: Player) {
        val health = votedValues["health"] ?: 0
        if (health > 20) {
            player.getAttribute(Attribute.MAX_HEALTH).baseValue = health.toDouble()
            player.health = health.toFloat()
        }
    }

    private fun borderSpeedMultiplier(): Double = when (votedValues["border"] ?: 1) {
        0 -> 0.6
        2 -> 1.5
        else -> 1.0
    }

    private fun buildResult(winner: Player?): MatchResult {
        val gameDuration = java.time.Duration.ofMillis(System.currentTimeMillis() - gameStartTime)

        return matchResult {
            if (winner != null) {
                winner(winner)
            } else {
                draw()
            }
            duration(gameDuration)
            metadata("mode", "battleroyale")

            stat("Kills") {
                StatTracker.players().forEach { uuid ->
                    val kills = StatTracker.get(uuid, "kills")
                    if (kills > 0) {
                        val name = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.username
                            ?: uuid.toString().take(8)
                        player(uuid, name, kills.toDouble())
                    }
                }
            }
        }
    }
}
