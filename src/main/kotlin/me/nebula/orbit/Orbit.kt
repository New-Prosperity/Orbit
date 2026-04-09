package me.nebula.orbit

import me.nebula.ether.utils.app.App
import me.nebula.ether.utils.app.appDelegate
import me.nebula.ether.utils.environment.environment
import me.nebula.ether.utils.hazelcast.Store
import me.nebula.ether.utils.hazelcast.hazelcastModule
import me.nebula.ether.utils.module.moduleRegistry
import java.util.concurrent.atomic.AtomicBoolean
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.storage.StorageClient
import me.nebula.ether.utils.storage.storageClient
import me.nebula.ether.utils.translation.TranslationRegistry
import me.nebula.ether.utils.translation.translationRegistry
import me.nebula.orbit.utils.anticheat.AntiCheat
import me.nebula.orbit.utils.botai.BotAI
import me.nebula.orbit.utils.drain.PlayerTransfer
import me.nebula.orbit.utils.drain.ServerDrainManager
import me.nebula.orbit.utils.maploader.MapLoader
import me.nebula.orbit.utils.metrics.MetricsPublisher
import me.nebula.orbit.utils.replay.PendingReplayFlushes
import me.nebula.orbit.utils.replay.ReplayStorage
import me.nebula.gravity.achievement.AchievementStore
import me.nebula.gravity.audit.AuditStore
import me.nebula.gravity.battlepass.BattlePassStore
import me.nebula.gravity.battleroyale.BattleRoyaleKitStore
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.anticheat.FlaggedPlayerStore
import me.nebula.gravity.marketplace.MarketplaceListingStore
import me.nebula.gravity.economy.EconomyStore
import me.nebula.gravity.economy.EconomyTransactionStore
import me.nebula.gravity.host.HostRequestLookupStore
import me.nebula.gravity.host.HostRequestStore
import me.nebula.gravity.host.HostTicketStore
import me.nebula.gravity.map.MapStore
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.PropertyUpdateMessage
import me.nebula.gravity.messaging.ServerDeregistrationMessage
import me.nebula.gravity.messaging.ServerRegistrationMessage
import me.nebula.gravity.server.ServerStore
import me.nebula.gravity.mission.ActiveMission
import me.nebula.gravity.mission.MissionData
import me.nebula.gravity.mission.MissionStore
import me.nebula.gravity.party.PartyLookupStore
import me.nebula.gravity.party.PartyStore
import me.nebula.gravity.player.PlayerStore
import me.nebula.gravity.player.PreferenceStore
import me.nebula.gravity.property.PropertyStore
import me.nebula.gravity.queue.PoolConfigStore
import me.nebula.gravity.queue.QueueStore
import me.nebula.gravity.rank.PlayerRankStore
import me.nebula.gravity.rank.RankStore
import me.nebula.gravity.ranking.RankingReportStore
import me.nebula.gravity.ranking.RankingStore
import me.nebula.gravity.reconnection.ReconnectionStore
import me.nebula.gravity.sanction.SanctionStore
import me.nebula.gravity.server.ConnectPlayerProcessor
import me.nebula.gravity.server.DisconnectPlayerProcessor
import me.nebula.gravity.server.ProvisionStore
import me.nebula.gravity.server.SyncConnectedPlayersProcessor
import me.nebula.gravity.session.SessionStore
import me.nebula.gravity.stats.StatsStore
import me.nebula.orbit.commands.installBasicCommands
import me.nebula.orbit.commands.installGameCommands
import me.nebula.orbit.commands.orbitCommand
import me.nebula.orbit.commands.cleanupReplayViewer
import me.nebula.orbit.commands.replayCommand
import me.nebula.orbit.commands.settingsCommand
import me.nebula.orbit.commands.vanishCommand
import me.nebula.orbit.cosmetic.AuraManager
import me.nebula.orbit.cosmetic.CompanionManager
import me.nebula.orbit.cosmetic.CosmeticListener
import me.nebula.orbit.cosmetic.CosmeticMountManager
import me.nebula.orbit.cosmetic.CosmeticRegistry
import me.nebula.orbit.cosmetic.GadgetManager
import me.nebula.orbit.cosmetic.GravestoneManager
import me.nebula.orbit.cosmetic.PetManager
import me.nebula.orbit.cosmetic.cosmeticsCommand
import me.nebula.orbit.mutator.MutatorRegistry
import me.nebula.orbit.guild.guildCommand
import me.nebula.orbit.marketplace.MarketplaceExpiry
import me.nebula.orbit.marketplace.TradeManager
import me.nebula.orbit.marketplace.marketplaceCommand
import me.nebula.orbit.marketplace.sellCommand
import me.nebula.orbit.marketplace.tradeCommand
import me.nebula.orbit.cosmetic.installCosmeticInteraction
import me.nebula.orbit.cosmetic.previewCommand
import me.nebula.orbit.mode.ServerMode
import me.nebula.orbit.mode.game.battleroyale.BattleRoyaleMode
import me.nebula.orbit.mode.build.BuildMode
import me.nebula.orbit.utils.vanilla.VanillaModules
import me.nebula.orbit.mode.hub.HubMode
import me.nebula.orbit.progression.BattlePassMenu
import me.nebula.orbit.progression.ProgressionSubscribers
import me.nebula.orbit.progression.achievement.AchievementMenu
import me.nebula.orbit.progression.achievement.registerAchievementContent
import me.nebula.orbit.progression.mission.MissionMenu
import me.nebula.orbit.progression.mission.MissionRegistry
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.orbit.utils.cinematic.cinematicTestCommand
import me.nebula.orbit.utils.commandbuilder.OnlinePlayerCache
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.customcontent.CustomContentRegistry
import me.nebula.gravity.cache.CacheSlots
import me.nebula.gravity.cache.PlayerCache
import me.nebula.gravity.guild.GuildInviteStore
import me.nebula.gravity.guild.GuildLookupStore
import me.nebula.gravity.guild.GuildStore
import me.nebula.gravity.player.PreferenceData
import me.nebula.gravity.leveling.LevelStore
import me.nebula.gravity.nick.NickData
import me.nebula.gravity.nick.NickPoolManager
import me.nebula.gravity.nick.NickPoolStore
import me.nebula.gravity.nick.NickStore
import me.nebula.gravity.rating.RatingStore
import me.nebula.gravity.rank.RankManager
import me.nebula.orbit.nick.NickManager
import me.nebula.orbit.nick.nickCommands
import me.nebula.orbit.staff.StaffSpectateManager
import me.nebula.orbit.staff.inspectCommand
import me.nebula.orbit.staff.punishCommand
import me.nebula.orbit.staff.spectateCommand
import me.nebula.orbit.staff.unflagCommand
import me.nebula.orbit.utils.statue.statueCommand
import me.nebula.orbit.utils.vanish.VanishManager
import me.nebula.orbit.utils.actionbar.ActionBarManager
import me.nebula.orbit.utils.bossbar.AnimatedBossBarManager
import me.nebula.orbit.utils.counter.AnimatedCounterManager
import me.nebula.orbit.utils.tablist.TabListManager
import me.nebula.orbit.utils.customcontent.armor.armorTestCommand
import me.nebula.orbit.utils.tooltip.TooltipStyleRegistry
import me.nebula.orbit.utils.tooltip.tooltipCommand
import me.nebula.orbit.utils.customcontent.customContentCommand
import me.nebula.orbit.utils.hud.HudAnchor
import me.nebula.orbit.utils.hud.HudManager
import me.nebula.orbit.utils.hud.hudLayout
import me.nebula.orbit.utils.hud.hideHud
import me.nebula.orbit.utils.hud.isHudShowing
import me.nebula.orbit.utils.hud.showHud
import me.nebula.orbit.utils.hud.updateHud
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.generator.ModelGenerator
import me.nebula.orbit.utils.modelengine.modelEngineCommand
import me.nebula.orbit.utils.scheduler.repeat
import me.nebula.orbit.utils.screen.screenTestCommand
import net.kyori.adventure.text.Component
import net.minestom.server.timer.Task
import me.nebula.orbit.utils.chat.miniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import net.minestom.server.tag.Tag
import java.util.UUID

object Orbit {

    val shuttingDown = AtomicBoolean(false)

    lateinit var app: App
    lateinit var translations: TranslationRegistry
    lateinit var serverName: String
        private set
    lateinit var mode: ServerMode
        private set
    internal val isModeInitialized: Boolean get() = ::mode.isInitialized
    var provisionUuid: String? = null
        private set
    var gameMode: String? = null
        private set
    var hostOwner: UUID? = null
        private set
    var mapName: String? = null
        private set
    var activeMutatorIds: List<String> = emptyList()
        private set
    var randomMutatorCount: Int = 0
    var storage: StorageClient? = null
        private set

    @Volatile var instance: OrbitInstance? = null
        private set

    private val moduleRegistry = moduleRegistry {
        register(ModelEngineModule)
        register(BotAIModule)
        register(HudSystemModule)
        register(CosmeticSystemModule)
        register(StaffSystemModule)
        register(AntiCheatModule)
        register(ObservabilityModule)
        register(ProgressionModule)
        register(MarketplaceModule).enableIf { gameMode == null }
    }

    private fun rebuildInstance() {
        if (!::serverName.isInitialized || !::mode.isInitialized) return
        instance = OrbitInstance(
            serverName = serverName,
            mode = mode,
            provisionUuid = provisionUuid,
            gameMode = gameMode,
            hostOwner = hostOwner,
            mapName = mapName,
            activeMutatorIds = activeMutatorIds,
            randomMutatorCount = randomMutatorCount,
            storage = storage,
        )
    }

    private val logger = logger("Orbit")
    val LOCALE_TAG: Tag<String> = Tag.String("nebula:locale")
    val TRACE_TAG: Tag<String> = Tag.String("nebula:traceId")
    private val globalTasks = mutableListOf<Task>()

    fun localeOf(playerId: UUID): String {
        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId)
        return player?.getTag(LOCALE_TAG) ?: translations.defaultLocale
    }

    fun cacheLocale(player: Player, locale: String) {
        player.setTag(LOCALE_TAG, locale)
    }

    fun syncConnectedPlayers() {
        val pUuid = provisionUuid ?: return
        val playerIds = MinecraftServer.getConnectionManager().onlinePlayers.mapTo(mutableSetOf()) { it.uuid }
        ServerStore.executeOnKey(pUuid, SyncConnectedPlayersProcessor(playerIds))
    }

    fun deserialize(key: String, locale: String, vararg resolvers: TagResolver): Component =
        miniMessage.deserialize(translations.require(key, locale), *resolvers)

    private inline fun <T> bootStep(name: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            val ms = (System.nanoTime() - start) / 1_000_000
            logger.info { "[boot] step=$name duration=${ms}ms" }
        }
    }

    private fun detectContainerAddress(): String? =
        NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress

    @JvmStatic
    fun main(args: Array<String>) {
        Thread.currentThread().contextClassLoader = Orbit::class.java.classLoader

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logger.error(throwable) { "Uncaught exception in thread '${thread.name}'" }
        }

        val env = environment {
            required("VELOCITY_SECRET")
            optional("P_SERVER_NAME")
            optional("HAZELCAST_ADDRESSES")
            optional("STORAGE_URL")
            optional("STORAGE_TOKEN")
        }

        val port = env.optional("SERVER_PORT", 25565) { it.toInt() }
        val serverUuid = env.optional("P_SERVER_UUID", "")
        val serverHost = env.optional("SERVER_HOST", "").ifEmpty { null } ?: detectContainerAddress()
        val hazelcastAddresses = env.all["HAZELCAST_ADDRESSES"]?.ifEmpty { null }

        app = appDelegate("Orbit") {
            configureResources {
                dataDirectory = Path.of("data")
            }
            configureModules {
                +hazelcastModule {
                    client {
                        instanceName = env.all["P_SERVER_NAME"]?.ifEmpty { null } ?: "orbit-local"
                        if (hazelcastAddresses != null) {
                            networkConfig.addAddress(*hazelcastAddresses.split(",").toTypedArray())
                        }
                    }
                    stores {
                        +PlayerStore
                        +SanctionStore
                        +SessionStore
                        +EconomyStore
                        +EconomyTransactionStore
                        +RankStore
                        +PlayerRankStore
                        +PreferenceStore
                        +PartyStore
                        +PartyLookupStore
                        +QueueStore
                        +PoolConfigStore
                        +RankingStore
                        +StatsStore
                        +RankingReportStore
                        +ServerStore
                        +ProvisionStore
                        +ReconnectionStore
                        +CosmeticStore
                        +HostTicketStore
                        +HostRequestStore
                        +HostRequestLookupStore
                        +BattleRoyaleKitStore
                        +MapStore
                        +AchievementStore
                        +BattlePassStore
                        +MissionStore
                        +LevelStore
                        +NickStore
                        +NickPoolStore
                        +RatingStore
                        +AuditStore
                        +MarketplaceListingStore
                        +FlaggedPlayerStore
                        +GuildStore
                        +GuildLookupStore
                        +GuildInviteStore
                    }
                }
            }
            onEnable {
                PropertyStore.initialize()
                translations = translationRegistry {
                    prefix("orbit")
                    defaultLocale("en")
                    fallback(true)
                }
            }
        }

        app.start().join()

        if (serverUuid.isNotEmpty()) {
            val cached = ProvisionStore.load(serverUuid)
            if (cached != null) {
                val (provision, server) = cached
                provisionUuid = provision.uuid
                gameMode = provision.metadata?.get("game_mode")
                hostOwner =
                    provision.metadata?.get("host_owner")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                mapName = provision.metadata?.get("map")
                activeMutatorIds = provision.metadata?.get("mutators")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                serverName = server?.name ?: "orbit-local"
                logger.info { "Provision discovered: name=$serverName, gameMode=$gameMode, hostOwner=$hostOwner, map=$mapName, mutators=$activeMutatorIds, provisionUuid=${provision.uuid}" }
            } else {
                serverName = "orbit-local"
                logger.warn { "No provision found in store for P_SERVER_UUID=$serverUuid, using fallback serverName=$serverName" }
            }
        } else {
            serverName = "orbit-local"
        }

        val storageUrl = env.all["STORAGE_URL"]?.ifEmpty { null }
        val storageToken = env.all["STORAGE_TOKEN"]?.ifEmpty { null }
        if (storageUrl != null && storageToken != null) {
            val client = storageClient {
                url = storageUrl
                token = storageToken
            }
            storage = client
            ReplayStorage.initialize(client.scope("replays"))
        }

        var resolvedWorldPath: String? = null
        val activeGameMode = gameMode
        if (activeGameMode != null) {
            val targetMap = mapName
                ?: PoolConfigStore.load(activeGameMode)?.maps?.randomOrNull()
            if (targetMap != null) {
                resolvedWorldPath = runCatching {
                    MapLoader.resolve(targetMap).toString()
                }.onFailure { e ->
                    logger.error(e) { "Failed to resolve map '$targetMap', falling back to default world" }
                }.getOrNull()
            }
        }

        val velocitySecret = env.all.getValue("VELOCITY_SECRET")
        val server = MinecraftServer.init(Auth.Velocity(velocitySecret))

        MinecraftServer.getExceptionManager().setExceptionHandler { throwable ->
            val msg = throwable.message ?: ""
            if (msg.contains("packet") && (msg.contains("invalid") || msg.contains("Unknown"))) {
                logger.debug { "Suppressed packet-state error: $msg" }
                return@setExceptionHandler
            }
            logger.error(throwable) { "Unhandled exception" }
        }

        registerVanillaModules()
        mode = resolveMode(resolvedWorldPath)
        logger.info { "Server mode: ${mode::class.simpleName}" }

        val handler = MinecraftServer.getGlobalEventHandler()

        app.resources.ensureDirectory("models")

        bootStep("TooltipStyleRegistry") { TooltipStyleRegistry.registerDefaults() }
        bootStep("CustomContentRegistry.init") { CustomContentRegistry.init(app.resources, handler) }

        bootStep("ModelEngine.registerRaw") {
            app.resources.list("models", "bbmodel", recursive = true).forEach { path ->
                val relativePath = path.removePrefix("models/")
                logger.info { "Loading model: $relativePath" }
                val raw = ModelGenerator.generateRaw(app.resources, relativePath)
                ModelEngine.registerRaw(raw.blueprint.name, raw)
            }
        }

        CustomContentRegistry.mergePack()

        HudManager.register(hudLayout("test-hud") {
            bar("health") {
                anchor(HudAnchor.BOTTOM_LEFT)
                offset(0.02f, -0.12f)
                sprites(bg = "bar_bg", fill = "bar_fill_red", empty = "bar_empty")
                segments(10)
            }
            bar("mana") {
                anchor(HudAnchor.BOTTOM_LEFT)
                offset(0.02f, -0.16f)
                sprites(bg = "bar_bg", fill = "bar_fill_blue", empty = "bar_empty")
                segments(10)
            }
            sprite("compass") {
                anchor(HudAnchor.TOP_CENTER)
                offset(0f, 0.02f)
                sprite("icon_health")
            }
            text("score") {
                anchor(HudAnchor.TOP_RIGHT)
                offset(-0.05f, 0.02f)
            }
            text("timer") {
                anchor(HudAnchor.TOP_CENTER)
                offset(0f, 0.06f)
            }
        })

        val commandManager = MinecraftServer.getCommandManager()
        installBasicCommands(commandManager)
        installGameCommands(commandManager)
        commandManager.register(orbitCommand())
        commandManager.register(modelEngineCommand(app.resources))
        commandManager.register(customContentCommand(app.resources))
        commandManager.register(cinematicTestCommand())
        commandManager.register(screenTestCommand())
        commandManager.register(armorTestCommand())
        commandManager.register(tooltipCommand())
        nickCommands().forEach(commandManager::register)
        commandManager.register(vanishCommand())
        commandManager.register(settingsCommand())
        commandManager.register(spectateCommand())
        commandManager.register(inspectCommand())
        commandManager.register(punishCommand())
        commandManager.register(unflagCommand())
        commandManager.register(replayCommand())
        commandManager.register(cosmeticsCommand())
        commandManager.register(previewCommand())
        commandManager.register(marketplaceCommand())
        commandManager.register(sellCommand())
        commandManager.register(tradeCommand())
        commandManager.register(guildCommand())
        commandManager.register(statueCommand())
        commandManager.register(command("battlepass") {
            onPlayerExecute { BattlePassMenu.open(player) }
        })
        commandManager.register(command("missions") {
            onPlayerExecute { MissionMenu.open(player) }
        })
        commandManager.register(command("achievements") {
            onPlayerExecute { AchievementMenu.open(player) }
        })
        commandManager.register(command("hud") {
            onPlayerExecute {
                if (player.isHudShowing("test-hud")) {
                    player.hideHud("test-hud")
                } else {
                    player.showHud("test-hud")
                    player.updateHud("health", 7)
                    player.updateHud("mana", 4)
                    player.updateHud("score", "42")
                    player.updateHud("timer", "3:45")
                }
            }
        })

        CosmeticRegistry.loadFromDefinitions()
        MutatorRegistry.loadDefinitions()
        registerAchievementContent()

        mode.install(handler)
        rebuildInstance()

        PlayerCache.install(*CacheSlots.ORBIT)
        PlayerCache.onStoreUpdate {
            LevelStore.listen { onUpdated { uuid, _ -> PlayerCache.refresh(uuid, CacheSlots.LEVEL) } }
            EconomyStore.listen { onUpdated { uuid, _ -> PlayerCache.refresh(uuid, CacheSlots.ECONOMY) } }
            PreferenceStore.listen { onUpdated { uuid, _ -> PlayerCache.refresh(uuid, CacheSlots.PREFERENCES) } }
            StatsStore.listen { onUpdated { uuid, _ -> PlayerCache.refresh(uuid, CacheSlots.STATS) } }
            CosmeticStore.listen { onUpdated { uuid, _ -> PlayerCache.refresh(uuid, CacheSlots.COSMETICS) } }
            PlayerRankStore.listen { onUpdated { uuid, _ -> PlayerCache.refresh(uuid, CacheSlots.RANK, CacheSlots.PERMISSIONS) } }
            PlayerStore.listen { onUpdated { uuid, _ -> PlayerCache.refresh(uuid, CacheSlots.PLAYER) } }
            NickStore.listen {
                onUpdated { uuid, _ -> PlayerCache.refresh(uuid, CacheSlots.NICK) }
                onRemoved { uuid -> PlayerCache.get(uuid)?.set(CacheSlots.NICK, null) }
            }
            GuildLookupStore.listen {
                onUpdated { uuid, _ -> PlayerCache.refresh(uuid, CacheSlots.GUILD) }
                onRemoved { uuid -> PlayerCache.get(uuid)?.set(CacheSlots.GUILD, null) }
            }
        }
        PlayerCache.installListeners()

        moduleRegistry.enableAll()
        runCatching { PendingReplayFlushes.sweepStale() }

        handler.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance = mode.activeInstance
            event.player.respawnPoint = mode.activeSpawnPoint
            if (event.isFirstConfig) {
                PlayerCache.preload(event.player.uuid)
            } else {
                event.setSendRegistryData(false)
                event.setClearChat(false)
            }
            ServerDrainManager.handleIncomingPlayer(event.player.uuid)
        }

        handler.addListener(PlayerSpawnEvent::class.java) { event ->
            val player = event.player
            val cached = PlayerCache.get(player.uuid) ?: PlayerCache.getOrLoad(player.uuid)
            val playerData = cached[CacheSlots.PLAYER]
            val locale = playerData?.language ?: translations.defaultLocale
            cacheLocale(player, locale)

            val session = SessionStore.load(player.uuid)
            val traceId = session?.traceId?.ifEmpty { null } ?: player.uuid.toString().take(8)
            player.setTag(TRACE_TAG, traceId)

            AchievementRegistry.loadPlayer(player.uuid)

            val nickData = cached[CacheSlots.NICK]
            val preferences = cached[CacheSlots.PREFERENCES] ?: PreferenceData()
            if (nickData != null) {
                NickManager.applyNick(player, nickData)
            } else if (preferences.streamerMode && RankManager.hasPermission(player.uuid, "nebula.streamer") && !NickManager.isNicked(player)) {
                val entry = NickPoolManager.claimRandom()
                if (entry != null) {
                    val autoNick = NickData(entry.name, entry.skinTextures, entry.skinSignature, entry.identity)
                    NickStore.save(player.uuid, autoNick)
                    NickManager.applyNick(player, autoNick)
                }
            }

            if (preferences.staffAutoVanish && RankManager.hasPermission(player.uuid, "staff.vanish") && !VanishManager.isVanished(player)) {
                VanishManager.vanish(player)
            }

            for (other in event.spawnInstance.players) {
                if (other === player) continue
                val otherNick = PlayerCache.get(other.uuid)?.get(CacheSlots.NICK) ?: continue
                NickManager.sendNickedInfoTo(viewer = player, target = other, otherNick)
            }

            Thread.startVirtualThread {
                if (MissionStore.load(player.uuid) == null) {
                    val daily = MissionRegistry.randomDaily(3).map { t ->
                        ActiveMission(
                            t.id,
                            t.type,
                            t.target,
                            xpReward = t.xpReward,
                            coinReward = t.coinReward
                        )
                    }
                    val weekly = MissionRegistry.randomWeekly(3).map { t ->
                        ActiveMission(
                            t.id,
                            t.type,
                            t.target,
                            xpReward = t.xpReward,
                            coinReward = t.coinReward
                        )
                    }
                    val now = System.currentTimeMillis()
                    MissionStore.save(
                        player.uuid, MissionData(
                            dailyMissions = daily,
                            weeklyMissions = weekly,
                            dailyResetAt = nextDailyReset(now),
                            weeklyResetAt = nextWeeklyReset(now),
                        )
                    )
                }
            }

            val pUuid = provisionUuid
            if (pUuid != null) {
                ServerStore.executeOnKey(pUuid, ConnectPlayerProcessor(player.uuid))
            }
        }

        handler.addListener(PlayerDisconnectEvent::class.java) { event ->
            TradeManager.onDisconnect(event.player)
            AchievementRegistry.unloadPlayer(event.player.uuid)
            PlayerCache.evict(event.player.uuid)
            cleanupReplayViewer(event.player.uuid)
            val pUuid = provisionUuid
            if (pUuid != null) {
                ServerStore.executeOnKey(pUuid, DisconnectPlayerProcessor(event.player.uuid))
            }
        }

        globalTasks += repeat(Duration.ofSeconds(5)) { OnlinePlayerCache.refresh() }
        globalTasks += repeat(2) { HudManager.tick() }
        globalTasks += repeat(2) { ActionBarManager.tick() }
        globalTasks += repeat(20) { TabListManager.tick() }

        server.start("0.0.0.0", port)

        if (serverUuid.isNotEmpty()) {
            logger.info { "Publishing ServerRegistrationMessage(serverUuid=$serverUuid, address=$serverHost, maxPlayers=${mode.maxPlayers}, gameMode=$gameMode)" }
            NetworkMessenger.publish(ServerRegistrationMessage(serverUuid, serverHost, mode.maxPlayers, gameMode))
            logger.info { "ServerRegistrationMessage published" }
        } else {
            logger.warn { "P_SERVER_UUID is empty, skipping server registration" }
        }

        if (gameMode != null) {
            NetworkMessenger.subscribe<PropertyUpdateMessage> { msg ->
                if (msg.key.startsWith("MAINTENANCE_") && msg.value == "true") {
                    val disabledMode = msg.key.removePrefix("MAINTENANCE_").lowercase()
                    if (disabledMode == gameMode) {
                        for (p in MinecraftServer.getConnectionManager().onlinePlayers) {
                            p.sendMessage(deserialize("orbit.mode.disabled_notice", localeOf(p.uuid)))
                        }
                    }
                }
            }
        }

        val pUuid = provisionUuid
        if (pUuid != null) {
            globalTasks += repeat(Duration.ofSeconds(5)) { syncConnectedPlayers() }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            if (!shuttingDown.compareAndSet(false, true)) return@Thread

            if (serverUuid.isNotEmpty()) {
                logger.info { "Publishing ServerDeregistrationMessage(serverUuid=$serverUuid)" }
                runCatching { NetworkMessenger.publish(ServerDeregistrationMessage(serverUuid)) }
            }

            val players = MinecraftServer.getConnectionManager().onlinePlayers.toList()
            if (players.isNotEmpty()) {
                val transferred = PlayerTransfer.transferAllToHub()
                if (transferred > 0) Thread.sleep(2000)

                for (player in MinecraftServer.getConnectionManager().onlinePlayers.toList()) {
                    runCatching { player.kick(deserialize("orbit.server_shutdown", localeOf(player.uuid))) }
                }
            }
            globalTasks.forEach { it.cancel() }
            globalTasks.clear()
            moduleRegistry.disableAll()
            mode.shutdown()
            PlayerCache.clear()
            runCatching { Store.flushAll() }
            app.stop().join()
        })
    }

    private fun nextDailyReset(now: Long): Long {
        val tomorrow = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC)
            .toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC)
        return tomorrow.toInstant().toEpochMilli()
    }

    private fun nextWeeklyReset(now: Long): Long {
        val date = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
        val nextMonday = date.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        return nextMonday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    private fun resolveMode(worldPath: String? = null): ServerMode =
        when (gameMode) {
            null -> HubMode()
            "battleroyale" -> BattleRoyaleMode(worldPath)
            "build" -> BuildMode(worldPath)
            else -> error("Unknown game mode: $gameMode")
        }

    private fun registerVanillaModules() {
        VanillaModules.registerAll()
    }
}

val Player.traceId: String
    get() = getTag(Orbit.TRACE_TAG) ?: uuid.toString().take(8)
