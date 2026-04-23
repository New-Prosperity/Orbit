package me.nebula.orbit.mode.game.battleroyale

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.ranking.AggregateOperation
import me.nebula.gravity.ranking.RankingReportStore
import me.nebula.gravity.session.SessionStore
import me.nebula.gravity.stats.IncrementStatsProcessor
import me.nebula.gravity.stats.StatsStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.displayUsername
import me.nebula.orbit.mode.config.PlaceholderResolver
import me.nebula.orbit.mode.config.placeholderResolver
import me.nebula.orbit.progression.BattlePassManager
import me.nebula.orbit.progression.mission.MissionTracker
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.orbit.utils.achievement.AchievementTriggerManager
import me.nebula.orbit.utils.challenge.ChallengeRegistry
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.player.PreferenceStore
import me.nebula.gravity.player.TogglePreferenceProcessor
import me.nebula.gravity.party.PartyLookupStore
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.mode.game.GameSettings
import me.nebula.orbit.mode.game.battleroyale.script.BorderController
import me.nebula.orbit.mode.game.battleroyale.script.DeathmatchController
import me.nebula.orbit.mode.game.battleroyale.script.ShrinkBorder
import me.nebula.orbit.mode.game.battleroyale.script.buildBorderSteps
import me.nebula.orbit.mode.game.battleroyale.spawn.SpawnContext
import me.nebula.orbit.mode.game.battleroyale.spawn.SpawnImmunityController
import me.nebula.orbit.mode.game.battleroyale.spawn.SpawnModeRegistry
import me.nebula.orbit.event.GameEvent
import me.nebula.orbit.loadout.LoadoutDeliveryRuntime
import me.nebula.orbit.loadout.LoadoutMenu
import me.nebula.orbit.mode.game.battleroyale.downed.DownedPlayerController
import me.nebula.orbit.mode.game.battleroyale.downed.DownedPlayerRuntime
import me.nebula.orbit.mode.game.battleroyale.downed.DownedReviveListener
import me.nebula.orbit.mode.game.battleroyale.downed.FinalReason
import me.nebula.orbit.mode.game.battleroyale.team.RespawnBeacon
import me.nebula.orbit.mode.game.battleroyale.team.TeamDamageMarker
import me.nebula.orbit.mode.game.battleroyale.team.TeamHudController
import me.nebula.orbit.utils.chestloot.LegendaryChestRenderer
import me.nebula.orbit.utils.supplydrop.SupplyDropScheduler
import me.nebula.orbit.mode.game.battleroyale.zone.ManagedZoneBorderDriver
import me.nebula.orbit.mode.game.battleroyale.zone.ZoneController
import me.nebula.orbit.mode.game.battleroyale.zone.ZoneHudOverlay
import me.nebula.orbit.mode.game.battleroyale.zone.ZoneShrinkController
import me.nebula.orbit.rules.Rules
import me.nebula.orbit.variant.GameComponent
import me.nebula.orbit.variant.GameVariantPool
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
import me.nebula.orbit.utils.scheduler.repeat
import me.nebula.orbit.utils.spectatortoolkit.SpectatorTargetStats
import me.nebula.orbit.utils.spectatortoolkit.SpectatorToolkit
import me.nebula.orbit.utils.spectatortoolkit.spectatorToolkit
import me.nebula.orbit.utils.stattracker.StatTracker
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.minestom.server.timer.Task
import me.nebula.gravity.translation.Keys

class BattleRoyaleMode(worldPathOverride: String? = null) : GameMode(), BorderController, DeathmatchController, ZoneShrinkController {

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
    private val killPipeline = BattleRoyaleKillPipeline(this, brDeathRecapTracker, lastAttackerTag, lastAttackerTimeTag)
    private val zoneController = ZoneController(
        eventBus = events,
        zoneShrinkingEnabled = { rules[Rules.ZONE_SHRINKING] },
    )
    private val zoneHudOverlay = ZoneHudOverlay(this) { zoneController.state }
    private val loadoutDelivery = LoadoutDeliveryRuntime(
        modeId = "battleroyale",
        events = events,
    )
    private var borderDamageTask: Task? = null
    private var downedTask: Task? = null
    @Volatile private var deathmatchActive = false
    private val spawnImmunity = SpawnImmunityController()
    private var spawnModeResult: SpawnModeResult? = null
    private val playerMaps = ConcurrentHashMap<UUID, MutableSet<String>>()
    private var supplyDropScheduler: SupplyDropScheduler? = null
    private var legendaryChestRenderer: LegendaryChestRenderer? = null
    private val killstreakDropsFired = ConcurrentHashMap<UUID, MutableSet<Int>>()
    private val downedRuntime = DownedPlayerRuntime()
    private val downedController = DownedPlayerController(
        config = season.team,
        onFinalEliminate = ::onDownedFinalEliminate,
        onRevived = ::onDownedRevived,
    )
    private val downedReviveListener = DownedReviveListener(
        controller = downedController,
        tracker = tracker,
        config = season.team,
    )
    private val teamHud = TeamHudController(
        tracker = tracker,
        downed = downedController,
        config = season.team,
    )
    private val teamDamageMarker = TeamDamageMarker(tracker = tracker, config = season.team)
    private val respawnBeacon = RespawnBeacon(
        tracker = tracker,
        config = season.team,
        resolveRespawnPos = { reviver -> reviver.position },
        respawnCallback = { revived, reviver -> onBeaconRespawn(revived, reviver) },
        broadcast = { componentFactory -> broadcastAll { p -> p.sendMessage(componentFactory(p)) } },
    )

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
            name("<green><bold>Loadout")
            clean()
        }) { player ->
            LoadoutMenu.open(player, "battleroyale")
        }
        slot(5, itemStack(Material.PAPER) {
            name("<yellow>Vote on Settings <gray>(Right Click)")
            clean()
        }) { player ->
            BattleRoyaleVoteManager.openCategoryMenu(player)
        }
        configuredSlot(7, autofillItem(enabled = true)) {
            id("br_autofill_toggle")
            dynamicItem { player -> autofillItem(enabled = autofillOn(player.uuid)) }
            sound(SoundEvent.UI_BUTTON_CLICK)
            onClick { player -> toggleAutofill(player) }
        }
    }

    private fun autofillOn(uuid: UUID): Boolean =
        PreferenceStore.load(uuid)?.autofillEnabled ?: true

    private fun autofillItem(enabled: Boolean): ItemStack = itemStack(
        if (enabled) Material.LIME_CONCRETE else Material.RED_CONCRETE
    ) {
        name(if (enabled) "<green><bold>Autofill: ON" else "<red><bold>Autofill: OFF")
        lore(if (enabled) "<gray>You'll be matched with others to complete a team." else "<gray>You'll queue alone.")
        lore("<dark_gray>Click to toggle")
        clean()
    }

    private fun toggleAutofill(player: Player) {
        val newValue = PreferenceStore.executeOnKey(
            player.uuid, TogglePreferenceProcessor("autofillEnabled"),
        )
        lobbyLifecycleInternal.currentHotbar?.refreshSlot(player, 7)
        player.sendMessage(player.translate(
            if (newValue) Keys.Orbit.Game.Br.Autofill.EnabledChat
            else Keys.Orbit.Game.Br.Autofill.DisabledChat,
        ))
    }

    private val votedValues = ConcurrentHashMap<String, Int>()

    override fun onGameSetup(players: List<Player>) {
        for (cat in SeasonConfig.current.voteCategories) {
            val winningIndex = BattleRoyaleVoteManager.resolveAndRecord(cat.id)
            votedValues[cat.id] = cat.options.getOrNull(winningIndex)?.value ?: 0
        }

        broadcastAll { p ->
            for (cat in SeasonConfig.current.voteCategories) {
                p.sendMessage(p.translate(Keys.Orbit.Game.Br.Vote.Result,
                    "category" to p.translateRaw(cat.nameKey),
                    "option" to BattleRoyaleVoteManager.resolveOptionName(p, cat.id),
                ))
            }
        }
        BattleRoyaleVoteManager.clear()

        StatTracker.clear()

        if (season.goldenHead.enabled) GoldenHeadManager.install()
        LegendaryListener.install()
        if (season.team.enabled && season.team.reviveEnabled) downedReviveListener.install()
        if (season.team.enabled && season.team.respawnBeaconEnabled) {
            respawnBeacon.install()
            ChestLootManager.configureStackDecorator { stack -> respawnBeacon.decorate(stack) }
        }

        zoneController.attach(
            driver = ManagedZoneBorderDriver(gameInstance),
            initialDiameter = season.border.initialDiameter,
            centerX = season.border.centerX,
            centerZ = season.border.centerZ,
            initialDamagePerSecond = season.borderDamagePerSecond,
        )
        zoneHudOverlay.install()

        val mapRadius = generatedMap?.mapRadius ?: (season.border.initialDiameter / 2).toInt()
        val spawnComponent = activeVariant?.find<GameComponent.Spawn>()
        val providerId = spawnComponent?.spawnModeId ?: "battle_royale_bus"
        val provider = SpawnModeRegistry.resolve(providerId)
            ?: error("Unknown spawn mode id: $providerId")
        val result = provider.execute(SpawnContext(
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
            onComplete = null,
            random = kotlin.random.Random(gameStartTime.coerceAtLeast(1L)),
            teamOf = { uuid -> tracker.teamOf(uuid) },
            onImmunityGrant = { uuid, pos -> spawnImmunity.grant(uuid, pos) },
        ))

        spawnModeResult = result
        for (p in players) loadoutDelivery.scheduleForPlayer(p)

        val airdropTable = season.airdropTable?.let { ChestLootManager[it] }
        if (airdropTable != null) {
            supplyDropScheduler = SupplyDropScheduler(
                instance = gameInstance,
                events = events,
                defaultTable = airdropTable,
                config = season.supplyDropSchedule,
            )
        }
        legendaryChestRenderer = LegendaryChestRenderer(gameInstance).also { it.install() }
    }

    override fun variantPool(): GameVariantPool = BattleRoyaleVariants.POOL

    override fun onPlayingStart() {
        brDeathRecapTracker.gameStartTime = System.currentTimeMillis()
        loadoutDelivery.start()
        supplyDropScheduler?.install()
        if (!variantDeclaresBorderShrinks()) {
            variantController.runAuxiliarySteps(buildBorderSteps(season, borderSpeedMultiplier()))
        }

        downedTask = repeat(1) {
            if (phase != GamePhase.PLAYING) return@repeat
            downedReviveListener.tickRevives()
            downedController.tick()
        }

        borderDamageTask = repeat(20) {
            if (phase != GamePhase.PLAYING) return@repeat
            zoneController.tick(System.currentTimeMillis())
            updateDownedActionBars()
            teamHud.tick()
            val damage = zoneController.currentDamage
            if (damage <= 0f) return@repeat
            for (uuid in tracker.alive.toSet()) {
                val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: continue
                if (zoneController.isOutside(player.position)) {
                    player.damage(DamageType.OUT_OF_WORLD, damage)
                }
            }
            for (uuid in spawnImmunity.activeUuids()) {
                val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: continue
                spawnImmunity.checkMovement(uuid, player.position)
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
        val killerName = killer?.displayUsername ?: "?"

        broadcastAll { p ->
            p.sendMessage(p.translate(
                Keys.Orbit.Game.Br.Elimination,
                "victim" to player.displayUsername,
                "killer" to killerName,
            ))
        }

        maybeBroadcastTeamWipe(player, killerName)
        teamHud.onPlayerRemoved(player.uuid)

        if (season.deathmatch.enabled && !deathmatchActive && tracker.aliveCount <= season.deathmatch.triggerAtPlayers) {
            startDeathmatch()
        }
    }

    private fun maybeBroadcastTeamWipe(victim: Player, killerName: String) {
        val teamCfg = season.team
        if (!teamCfg.enabled || teamCfg.teamSize <= 1) return
        val team = tracker.teamOf(victim.uuid) ?: return
        if (!tracker.isTeamEliminated(team)) return
        broadcastAll { p ->
            p.sendMessage(p.translate(
                Keys.Orbit.Killfeed.TeamWipe,
                "team" to team,
                "killer" to killerName,
            ))
        }
    }

    override fun onKillStreak(player: Player, streak: Int) {
        val config = season.killstreakAirdrop
        if (!config.enabled) return
        val rarity = config.milestones[streak] ?: return
        val alreadyFired = killstreakDropsFired.computeIfAbsent(player.uuid) { ConcurrentHashMap.newKeySet() }
        if (!alreadyFired.add(streak)) return
        val scheduler = supplyDropScheduler ?: return
        val table = season.killstreakTable?.let { ChestLootManager[it] } ?: return
        scheduler.launchPersonalDrop(player.position, rarity, table)
        broadcastAll { p ->
            p.sendMessage(p.translate(
                Keys.Orbit.Game.Br.Killstreak.DropIncoming,
                "player" to player.displayUsername,
                "streak" to streak.toString(),
                "rarity" to rarity.displayLabel,
            ))
        }
    }

    override fun onPlayerDamaged(victim: Player, attacker: Player?, amount: Float, event: EntityDamageEvent): Boolean {
        if (!rules[Rules.DAMAGE_ENABLED]) return false
        if (attacker != null && spawnImmunity.isImmune(victim.uuid)) {
            spawnImmunity.onDamageTaken(victim.uuid)
            return false
        }
        if (attacker != null && !rules[Rules.PVP_ENABLED]) return false

        if (downedController.isDowned(victim.uuid)) {
            event.isCancelled = true
            downedController.absorbDamage(victim.uuid, amount.toInt().coerceAtLeast(1))
            return false
        }

        if (attacker != null && attacker.uuid != victim.uuid) {
            victim.setTag(lastAttackerTag, attacker.uuid)
            victim.setTag(lastAttackerTimeTag, System.currentTimeMillis())
        }

        brDeathRecapTracker.recordDamage(victim.uuid, DamageEntry(
            attackerUuid = attacker?.uuid,
            attackerName = attacker?.displayUsername ?: event.damage.type.key().value(),
            amount = amount,
            source = if (attacker != null) "PLAYER" else event.damage.type.key().value(),
            weapon = attacker?.itemInMainHand?.material(),
            distance = attacker?.let { it.position.distance(victim.position) },
        ))

        teamDamageMarker.flash(victim)

        if (victim.health - amount <= 0) {
            return killPipeline.handleLethal(victim, attacker, amount, event)
        }

        return true
    }

    internal fun tryKnock(victim: Player, attacker: Player?): Boolean {
        val teamCfg = season.team
        if (!teamCfg.enabled || !teamCfg.reviveEnabled) return false
        if (downedController.isDowned(victim.uuid)) return false
        val team = tracker.teamOf(victim.uuid) ?: return false
        val aliveMates = tracker.aliveInTeam(team).count { it != victim.uuid }
        if (aliveMates == 0) return false
        val knock = downedController.knock(victim.uuid, attacker?.uuid)
        if (knock.isFailure) return false
        downedRuntime.applyDownedState(victim)
        val attackerName = attacker?.displayUsername ?: "?"
        broadcastAll { p ->
            p.sendMessage(p.translate(
                Keys.Orbit.Game.Br.Downed.KnockedBroadcast,
                "victim" to victim.displayUsername,
                "attacker" to attackerName,
            ))
        }
        events.publish(GameEvent.PlayerKnocked(victim, attacker))
        return true
    }

    private fun onDownedFinalEliminate(uuid: UUID, reason: FinalReason) {
        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: return
        downedRuntime.clearDownedState(player, restoreHealth = false)
        if (reason == FinalReason.BLEEDOUT) {
            broadcastAll { p ->
                p.sendMessage(p.translate(
                    Keys.Orbit.Game.Br.Downed.BledOutBroadcast,
                    "victim" to player.displayUsername,
                ))
            }
            events.publish(GameEvent.PlayerBledOut(player))
        }
        eliminate(player)
    }

    private fun onBeaconRespawn(revived: Player, reviver: Player) {
        revive(revived, reviver.position)
        revived.health = revived.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
        events.publish(GameEvent.PlayerRespawned(revived))
    }

    private fun onDownedRevived(reviverUuid: UUID, revivedUuid: UUID) {
        val reviver = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(reviverUuid) ?: return
        val revived = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(revivedUuid) ?: return
        downedRuntime.clearDownedState(revived, restoreHealth = true)
        downedReviveListener.onRevived(reviverUuid)
        broadcastAll { p ->
            p.sendMessage(p.translate(
                Keys.Orbit.Game.Br.Downed.RevivedBroadcast,
                "revived" to revived.displayUsername,
                "reviver" to reviver.displayUsername,
            ))
        }
        events.publish(GameEvent.PlayerRevived(revived, reviver))
    }

    private fun updateDownedActionBars() {
        if (downedController.activeCount() == 0) return
        val now = System.currentTimeMillis()
        for (uuid in downedController.activeUuids()) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: continue
            val remaining = downedController.bleedoutRemainingSeconds(uuid, now)
            player.sendActionBar(player.translate(
                Keys.Orbit.Game.Br.Downed.SelfSubtitle,
                "seconds" to remaining.toString(),
            ))
        }
    }

    internal fun resolveKillerForVictim(victim: Player): UUID? = resolveKiller(victim)

    internal fun isKillerAlive(uuid: UUID): Boolean = tracker.isAlive(uuid)

    internal fun eliminatePlayer(victim: Player) = eliminate(victim)

    internal fun applyPartyBonusToKiller(uuid: UUID, base: Long): Long = applyPartyBonus(uuid, base)

    internal fun activeBattlePasses() = activeSeasonPasses

    internal fun goldenHeadEnabled(): Boolean = season.goldenHead.enabled

    override fun onGameReset() {
        borderDamageTask?.cancel()
        borderDamageTask = null
        downedTask?.cancel()
        downedTask = null
        downedReviveListener.uninstall()
        respawnBeacon.uninstall()
        ChestLootManager.configureStackDecorator(null)
        zoneController.end()
        zoneHudOverlay.uninstall()
        zoneController.reset()
        loadoutDelivery.shutdown()
        StatTracker.clear()
        brDeathRecapTracker.clear()
        deathmatchActive = false
        spawnImmunity.releaseAll()
        spawnModeResult?.let { SpawnModeExecutor.cleanup(it) }
        spawnModeResult = null
        supplyDropScheduler?.uninstall()
        supplyDropScheduler = null
        legendaryChestRenderer?.uninstall()
        legendaryChestRenderer = null
        killstreakDropsFired.clear()
        downedController.clearAll()
        downedRuntime.clearAll()
        teamHud.clear()
        teamDamageMarker.clear()
        ChestLootManager.resetChests()

        ChestLootManager.clear()
        LegendaryListener.uninstall()
        GoldenHeadManager.uninstall()
        LegendaryRegistry.clear()
        BattleRoyaleVoteManager.clear()
        votedValues.clear()
        playerMaps.clear()

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
            val playerName = player?.displayUsername ?: uuid.toString().take(8)
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
                MissionTracker.onPlayGamemode(player, "battleroyale")
                BattlePassManager.addXpToAll(player, applyPartyBonus(uuid, 50L), activeSeasonPasses)

                if (isWinner) {
                    MissionTracker.onWin(player)
                    MissionTracker.onWinGamemode(player, "battleroyale")
                    BattlePassManager.addXpToAll(player, applyPartyBonus(uuid, 100L), activeSeasonPasses)

                    if (tracker.deathsOf(uuid) == 0) {
                        AchievementRegistry.complete(player, "invincible")
                    }

                    if (kills == 0) {
                        AchievementRegistry.complete(player, "pacifist")
                    }

                    if (player.health < 4f) {
                        AchievementRegistry.complete(player, "close_call")
                    }

                    if (gameDuration < 300_000L) {
                        AchievementRegistry.complete(player, "speed_demon")
                    }
                }

                if (PartyLookupStore.exists(uuid)) {
                    AchievementRegistry.progress(player, "party_animal", 1)
                }

                if (Orbit.hostOwner != null && Orbit.hostOwner == uuid) {
                    AchievementRegistry.progress(player, "host_master", 1)
                }

                val mapName = Orbit.mapName
                if (mapName != null) {
                    AchievementTriggerManager.evaluate(player, "unique_maps_played", countUniqueMap(uuid, mapName))
                }

                if (kills >= 3) {
                    AchievementRegistry.progress(player, "berserker", 1)
                }

                val cosmeticData = CosmeticStore.load(uuid)
                if (cosmeticData != null) {
                    AchievementTriggerManager.evaluate(player, "cosmetics_owned", cosmeticData.owned.size.toLong())
                }

                val stats = StatsStore.load(uuid)
                if (stats != null) {
                    val brStats = stats.stats["battleroyale"]
                    if (brStats != null) {
                        AchievementTriggerManager.evaluate(player, "br_games_played", brStats.gamesPlayed.toLong())
                        AchievementTriggerManager.evaluate(player, "br_kills", brStats.kills.toLong())
                        AchievementTriggerManager.evaluate(player, "br_wins", brStats.wins.toLong())
                        ChallengeRegistry.onStatUpdate(player, "br_games_played", brStats.gamesPlayed.toLong())
                        ChallengeRegistry.onStatUpdate(player, "br_kills", brStats.kills.toLong())
                        ChallengeRegistry.onStatUpdate(player, "br_wins", brStats.wins.toLong())
                    }
                }

                val nonHiddenTotal = AchievementRegistry.totalNonHiddenCount()
                val nonHiddenCompleted = AchievementRegistry.completedNonHiddenCount(uuid)
                if (nonHiddenTotal > 0 && nonHiddenCompleted >= nonHiddenTotal) {
                    AchievementRegistry.complete(player, "completionist")
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
            speedSteps(1f, 2f, 4f)
            hud()
            freeCamera()
            hideOtherSpectators()
            aliveCount { mode.tracker.aliveCount }
            gameTimer { mode.formatRemainingTime() }
            targetStats { target ->
                SpectatorTargetStats(
                    kills = StatTracker.get(target, "kills").toInt(),
                    team = mode.tracker.teamOf(target.uuid),
                )
            }
        }
    }

    private fun formatRemainingTime(): String {
        val elapsedSec = ((System.currentTimeMillis() - gameStartTime) / 1000L).coerceAtLeast(0L)
        val remaining = (resolveGameDuration() - elapsedSec).coerceAtLeast(0L)
        val minutes = remaining / 60
        val seconds = remaining % 60
        return "%d:%02d".format(minutes, seconds)
    }

    override fun buildKillFeed(): KillFeed {
        val mode = this
        return killFeed {
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
            effect { event, _ ->
                mode.spectatorToolkit?.recordKill(event.killer?.username, event.victim.username)
            }
        }
    }

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

    private fun resolveKiller(target: Player): UUID? {
        val attackerUuid = target.getTag(lastAttackerTag) ?: return null
        val attackTime = target.getTag(lastAttackerTimeTag) ?: return null
        return if (System.currentTimeMillis() - attackTime < 10_000L) attackerUuid else null
    }

    override fun shrinkBorderTo(diameter: Double, durationSeconds: Double) {
        zoneController.shrinkBorderTo(diameter, durationSeconds)
    }

    override fun planZoneShrink(targetDiameter: Double, durationSeconds: Double, announceLeadSeconds: Double) {
        zoneController.planShrink(targetDiameter, durationSeconds, announceLeadSeconds)
    }

    override fun setBorderDamage(damagePerSecond: Double) {
        zoneController.setBorderDamage(damagePerSecond)
    }

    override fun startDeathmatch() {
        if (deathmatchActive) return
        deathmatchActive = true

        broadcastAll { p ->
            p.sendMessage(p.translate(Keys.Orbit.Game.Br.DeathmatchStart))
        }

        if (season.deathmatch.teleportToCenter) {
            val center = generatedMap?.center ?: Pos(season.border.centerX, season.spawn.y, season.border.centerZ)
            broadcastAlive { player ->
                player.teleport(center)
            }
        }

        zoneController.shrinkForDeathmatch(
            season.deathmatch.borderDiameter,
            season.deathmatch.borderShrinkSeconds.toDouble(),
        )
    }

    private fun scheduleDeathmatchCheck() {
        scheduleGameEvent("deathmatch_check", season.deathmatch.checkDelaySeconds * 20) {
            if (!deathmatchActive && tracker.aliveCount <= season.deathmatch.triggerAtPlayers) {
                startDeathmatch()
            }
        }
    }

    private fun applyVotedHealth(player: Player) {
        val health = votedValues["health"] ?: 0
        if (health > 20) {
            player.getAttribute(Attribute.MAX_HEALTH).baseValue = health.toDouble()
            player.health = health.toFloat()
        }
    }

    private fun variantDeclaresBorderShrinks(): Boolean {
        val script = activeVariant?.find<GameComponent.Script>() ?: return false
        return script.steps.any { step -> step.actions.any { it is ShrinkBorder } }
    }

    private fun borderSpeedMultiplier(): Double = when (votedValues["border"] ?: 1) {
        0 -> 0.6
        2 -> 1.5
        else -> 1.0
    }

    private fun countUniqueMap(uuid: UUID, mapName: String): Long {
        val maps = playerMaps.computeIfAbsent(uuid) { ConcurrentHashMap.newKeySet() }
        maps.add(mapName)
        return maps.size.toLong()
    }

    private fun buildResult(winner: Player?): MatchResult {
        val gameDuration = Duration.ofMillis(System.currentTimeMillis() - gameStartTime)

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
                        val name = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.displayUsername
                            ?: uuid.toString().take(8)
                        player(uuid, name, kills.toDouble())
                    }
                }
            }
        }
    }
}
