package me.nebula.orbit

import me.nebula.ether.utils.app.App
import me.nebula.ether.utils.app.appDelegate
import me.nebula.ether.utils.environment.environment
import me.nebula.ether.utils.hazelcast.Store
import me.nebula.ether.utils.hazelcast.hazelcastModule
import java.util.concurrent.atomic.AtomicBoolean
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.storage.StorageClient
import me.nebula.ether.utils.storage.storageClient
import me.nebula.ether.utils.translation.TranslationRegistry
import me.nebula.ether.utils.translation.translationRegistry
import me.nebula.orbit.utils.maploader.MapLoader
import me.nebula.orbit.utils.replay.ReplayStorage
import me.nebula.gravity.achievement.AchievementStore
import me.nebula.gravity.battlepass.BattlePassStore
import me.nebula.gravity.battleroyale.BattleRoyaleKitStore
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.economy.EconomyStore
import me.nebula.gravity.economy.EconomyTransactionStore
import me.nebula.gravity.host.HostRequestLookupStore
import me.nebula.gravity.host.HostRequestStore
import me.nebula.gravity.host.HostTicketStore
import me.nebula.gravity.map.MapStore
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.ServerDeregistrationMessage
import me.nebula.gravity.messaging.ServerRegistrationMessage
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
import me.nebula.gravity.server.*
import me.nebula.gravity.session.SessionStore
import me.nebula.gravity.stats.StatsStore
import me.nebula.orbit.commands.installBasicCommands
import me.nebula.orbit.commands.installGameCommands
import me.nebula.orbit.cosmetic.*
import me.nebula.orbit.mode.ServerMode
import me.nebula.orbit.mode.game.battleroyale.BattleRoyaleMode
import me.nebula.orbit.mode.hub.HubMode
import me.nebula.orbit.progression.BattlePassMenu
import me.nebula.orbit.progression.achievement.AchievementMenu
import me.nebula.orbit.progression.achievement.registerAchievementContent
import me.nebula.orbit.progression.mission.MissionMenu
import me.nebula.orbit.progression.mission.MissionRegistry
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.orbit.utils.cinematic.cinematicTestCommand
import me.nebula.orbit.utils.commandbuilder.OnlinePlayerCache
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.customcontent.CustomContentRegistry
import me.nebula.orbit.utils.customcontent.armor.armorTestCommand
import me.nebula.orbit.utils.customcontent.customContentCommand
import me.nebula.orbit.utils.hud.*
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.generator.ModelGenerator
import me.nebula.orbit.utils.modelengine.modelEngineCommand
import me.nebula.orbit.utils.screen.screenTestCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.timer.TaskSchedule
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object Orbit {

    val shuttingDown = AtomicBoolean(false)

    lateinit var app: App
    lateinit var translations: TranslationRegistry
    lateinit var serverName: String
        private set
    lateinit var mode: ServerMode
        private set
    var provisionUuid: String? = null
        private set
    var gameMode: String? = null
        private set
    var hostOwner: UUID? = null
        private set
    var mapName: String? = null
        private set
    var storage: StorageClient? = null
        private set

    private val logger = logger("Orbit")
    val miniMessage = MiniMessage.miniMessage()
    private val localeCache = ConcurrentHashMap<UUID, String>()

    fun localeOf(playerId: UUID): String =
        localeCache[playerId] ?: translations.defaultLocale

    fun cacheLocale(playerId: UUID, locale: String) {
        localeCache[playerId] = locale
    }

    fun evictLocale(playerId: UUID) {
        localeCache.remove(playerId)
    }

    fun syncConnectedPlayers() {
        val pUuid = provisionUuid ?: return
        val playerIds = MinecraftServer.getConnectionManager().onlinePlayers.mapTo(mutableSetOf()) { it.uuid }
        ServerStore.executeOnKey(pUuid, SyncConnectedPlayersProcessor(playerIds))
    }

    fun deserialize(key: String, locale: String, vararg resolvers: TagResolver): Component =
        miniMessage.deserialize(translations.require(key, locale), *resolvers)

    private fun detectContainerAddress(): String? =
        NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress

    @JvmStatic
    fun main(args: Array<String>) {
        Thread.currentThread().contextClassLoader = Orbit::class.java.classLoader

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
                serverName = server?.name ?: "orbit-local"
                logger.info { "Provision discovered: name=$serverName, gameMode=$gameMode, hostOwner=$hostOwner, map=$mapName, provisionUuid=${provision.uuid}" }
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
            storage = storageClient {
                url = storageUrl
                token = storageToken
            }
            ReplayStorage.initialize(storage!!.scope("replays"))
        }

        var resolvedWorldPath: String? = null
        if (gameMode != null) {
            val targetMap = mapName
                ?: PoolConfigStore.load(gameMode!!)?.maps?.randomOrNull()
            if (targetMap != null) {
                resolvedWorldPath = runCatching {
                    MapLoader.resolve(targetMap).toString()
                }.onFailure { e ->
                    logger.error(e) { "Failed to resolve map '$targetMap', falling back to default world" }
                }.getOrNull()
            }
        }

        val server = MinecraftServer.init(Auth.Velocity(env.all["VELOCITY_SECRET"]!!))

        mode = resolveMode(resolvedWorldPath)
        logger.info { "Server mode: ${mode::class.simpleName}" }

        val handler = MinecraftServer.getGlobalEventHandler()

        app.resources.ensureDirectory("models")
        ModelEngine.install()

        CustomContentRegistry.init(app.resources, handler)

        app.resources.list("models", "bbmodel", recursive = true).forEach { path ->
            val relativePath = path.removePrefix("models/")
            logger.info { "Loading model: $relativePath" }
            val raw = ModelGenerator.generateRaw(app.resources, relativePath)
            ModelEngine.registerRaw(raw.blueprint.name, raw)
        }

        CustomContentRegistry.mergePack()

        HudManager.register(hudLayout("test-hud") {
            bar("health") {
                anchor(HudAnchor.BOTTOM_LEFT)
                offset(0.02f, 0.88f)
                sprites(bg = "bar_bg", fill = "bar_fill_red", empty = "bar_empty")
                segments(10)
            }
            bar("mana") {
                anchor(HudAnchor.BOTTOM_LEFT)
                offset(0.02f, 0.84f)
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

        HudManager.install(handler)

        val commandManager = MinecraftServer.getCommandManager()
        installBasicCommands(commandManager)
        installGameCommands(commandManager)
        commandManager.register(modelEngineCommand(app.resources))
        commandManager.register(customContentCommand(app.resources))
        commandManager.register(cinematicTestCommand())
        commandManager.register(screenTestCommand())
        commandManager.register(armorTestCommand())
        commandManager.register(command("cosmetics") {
            onPlayerExecute { CosmeticMenu.openCategoryMenu(player) }
        })
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
        registerAchievementContent()
        CosmeticListener.activeConfig = mode.cosmeticConfig
        CosmeticListener.install(handler)
        AuraManager.install()
        CompanionManager.install()
        PetManager.install()
        GravestoneManager.install()
        CosmeticMountManager.install()

        mode.install(handler)

        handler.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            val player = event.player
            val playerData = PlayerStore.load(player.uuid)
            val locale = playerData?.language ?: translations.defaultLocale
            cacheLocale(player.uuid, locale)
            event.spawningInstance = mode.activeInstance
            player.respawnPoint = mode.activeSpawnPoint
            AchievementRegistry.loadPlayer(player.uuid)
            if (MissionStore.load(player.uuid) == null) {
                val daily = MissionRegistry.randomDaily(3).map { t ->
                    me.nebula.gravity.mission.ActiveMission(
                        t.id,
                        t.type,
                        t.target,
                        xpReward = t.xpReward,
                        coinReward = t.coinReward
                    )
                }
                val weekly = MissionRegistry.randomWeekly(3).map { t ->
                    me.nebula.gravity.mission.ActiveMission(
                        t.id,
                        t.type,
                        t.target,
                        xpReward = t.xpReward,
                        coinReward = t.coinReward
                    )
                }
                val now = System.currentTimeMillis()
                MissionStore.save(
                    player.uuid, me.nebula.gravity.mission.MissionData(
                        dailyMissions = daily,
                        weeklyMissions = weekly,
                        dailyResetAt = nextDailyReset(now),
                        weeklyResetAt = nextWeeklyReset(now),
                    )
                )
            }
        }

        handler.addListener(PlayerSpawnEvent::class.java) { event ->
            val pUuid = provisionUuid
            if (pUuid != null) {
                ServerStore.executeOnKey(pUuid, ConnectPlayerProcessor(event.player.uuid))
            }
        }

        handler.addListener(PlayerDisconnectEvent::class.java) { event ->
            AchievementRegistry.unloadPlayer(event.player.uuid)
            evictLocale(event.player.uuid)
            val pUuid = provisionUuid
            if (pUuid != null) {
                ServerStore.executeOnKey(pUuid, DisconnectPlayerProcessor(event.player.uuid))
            }
        }

        MinecraftServer.getSchedulerManager()
            .buildTask { OnlinePlayerCache.refresh() }
            .repeat(TaskSchedule.seconds(5))
            .schedule()

        MinecraftServer.getSchedulerManager()
            .buildTask { HudManager.tick() }
            .repeat(TaskSchedule.tick(2))
            .schedule()

        server.start("0.0.0.0", port)

        if (serverUuid.isNotEmpty()) {
            logger.info { "Publishing ServerRegistrationMessage(serverUuid=$serverUuid, address=$serverHost, maxPlayers=${mode.maxPlayers}, gameMode=$gameMode)" }
            NetworkMessenger.publish(ServerRegistrationMessage(serverUuid, serverHost, mode.maxPlayers, gameMode))
            logger.info { "ServerRegistrationMessage published" }
        } else {
            logger.warn { "P_SERVER_UUID is empty, skipping server registration" }
        }

        val pUuid = provisionUuid
        if (pUuid != null) {
            MinecraftServer.getSchedulerManager()
                .buildTask { syncConnectedPlayers() }
                .repeat(TaskSchedule.seconds(5))
                .schedule()
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            if (!shuttingDown.compareAndSet(false, true)) return@Thread
            for (player in MinecraftServer.getConnectionManager().onlinePlayers.toList()) {
                runCatching { player.kick(deserialize("orbit.server_shutdown", localeOf(player.uuid))) }
            }
            if (serverUuid.isNotEmpty()) {
                logger.info { "Publishing ServerDeregistrationMessage(serverUuid=$serverUuid)" }
                runCatching { NetworkMessenger.publish(ServerDeregistrationMessage(serverUuid)) }
            }
            CosmeticListener.uninstall()
            AuraManager.uninstall()
            CompanionManager.uninstall()
            PetManager.uninstall()
            GravestoneManager.uninstall()
            CosmeticMountManager.uninstall()
            ModelEngine.uninstall()
            mode.shutdown()
            runCatching { Store.flushAll() }
            app.stop().join()
        })
    }

    private fun nextDailyReset(now: Long): Long {
        val tomorrow = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC)
            .toLocalDate().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC)
        return tomorrow.toInstant().toEpochMilli()
    }

    private fun nextWeeklyReset(now: Long): Long {
        val date = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val nextMonday = date.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
        return nextMonday.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    private fun resolveMode(worldPath: String? = null): ServerMode =
        when (gameMode) {
            null -> HubMode()
            "battleroyale" -> BattleRoyaleMode(worldPath)
            else -> error("Unknown game mode: $gameMode")
        }
}
