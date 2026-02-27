package me.nebula.orbit

import me.nebula.ether.utils.app.App
import me.nebula.ether.utils.app.appDelegate
import me.nebula.ether.utils.environment.environment
import me.nebula.ether.utils.hazelcast.hazelcastModule
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.translation.TranslationRegistry
import me.nebula.ether.utils.translation.translationRegistry
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.economy.EconomyStore
import me.nebula.gravity.economy.EconomyTransactionStore
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.ServerDeregistrationMessage
import me.nebula.gravity.messaging.ServerRegistrationMessage
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
import me.nebula.gravity.server.ProvisionStore
import me.nebula.gravity.server.ServerStore
import me.nebula.gravity.session.ServerOccupancyStore
import me.nebula.gravity.session.SessionStore
import me.nebula.gravity.stats.StatsStore
import me.nebula.orbit.utils.commandbuilder.OnlinePlayerCache
import me.nebula.orbit.mode.ServerMode
import me.nebula.orbit.mode.game.hoplite.HopliteMode
import me.nebula.orbit.mode.hub.HubMode
import me.nebula.orbit.translation.OrbitTranslations
import me.nebula.orbit.utils.customcontent.CustomContentRegistry
import me.nebula.orbit.utils.customcontent.customContentCommand
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.generator.ModelGenerator
import me.nebula.orbit.utils.cinematic.cinematicTestCommand
import me.nebula.orbit.utils.modelengine.modelEngineCommand
import me.nebula.orbit.cosmetic.CosmeticListener
import me.nebula.orbit.cosmetic.CosmeticMenu
import me.nebula.orbit.cosmetic.CosmeticRegistry
import me.nebula.orbit.commands.installBasicCommands
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.customcontent.armor.armorTestCommand
import me.nebula.orbit.utils.screen.screenTestCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.timer.TaskSchedule
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object Orbit {

    lateinit var app: App
    lateinit var translations: TranslationRegistry
    lateinit var serverName: String
        private set
    var gameMode: String? = null
        private set

    private val logger = logger("Orbit")
    private val miniMessage = MiniMessage.miniMessage()
    private val localeCache = ConcurrentHashMap<UUID, String>()

    fun localeOf(playerId: UUID): String =
        localeCache[playerId] ?: translations.defaultLocale

    fun cacheLocale(playerId: UUID, locale: String) {
        localeCache[playerId] = locale
    }

    fun evictLocale(playerId: UUID) {
        localeCache.remove(playerId)
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
            required("HAZELCAST_LICENSE")
            required("VELOCITY_SECRET")
        }

        val port = env.optional("SERVER_PORT", 25565) { it.toInt() }
        val serverUuid = env.optional("P_SERVER_UUID", "")
        val serverHost = env.optional("SERVER_HOST", "").ifEmpty { null } ?: detectContainerAddress()

        app = appDelegate("Orbit") {
            configureResources {
                dataDirectory = Path.of("data")
            }
            configureModules {
                +hazelcastModule {
                    member {
                        isLiteMember = true
                        licenseKey = env.all["HAZELCAST_LICENSE"]
                    }
                    stores {
                        +PlayerStore
                        +SanctionStore
                        +SessionStore
                        +ServerOccupancyStore
                        +EconomyStore
                        +EconomyTransactionStore
                        +PropertyStore
                        +RankStore
                        +PlayerRankStore
                        +PreferenceStore
                        +QueueStore
                        +PoolConfigStore
                        +RankingStore
                        +StatsStore
                        +RankingReportStore
                        +ServerStore
                        +ProvisionStore
                        +ReconnectionStore
                        +CosmeticStore
                    }
                }
            }
            onEnable {
                translations = translationRegistry {
                    prefix("orbit")
                    defaultLocale("en")
                    fallback(true)
                }
                OrbitTranslations.register(translations)
            }
        }

        app.start().join()

        if (serverUuid.isNotEmpty()) {
            val cached = ProvisionStore.load(serverUuid)
            if (cached != null) {
                val (provision, server) = cached
                gameMode = provision.metadata?.get("game_mode")
                serverName = server?.name ?: "orbit-local"
                logger.info { "Provision discovered: name=$serverName, gameMode=$gameMode, provisionUuid=${provision.uuid}" }
            } else {
                serverName = "orbit-local"
                logger.warn { "No provision found in store for P_SERVER_UUID=$serverUuid, using fallback serverName=$serverName" }
            }
        } else {
            serverName = "orbit-local"
        }

        val server = MinecraftServer.init(Auth.Velocity(env.all["VELOCITY_SECRET"]!!))

        val mode: ServerMode = resolveMode()
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

        val commandManager = MinecraftServer.getCommandManager()
        installBasicCommands(commandManager)
        commandManager.register(modelEngineCommand(app.resources))
        commandManager.register(customContentCommand(app.resources))
        commandManager.register(cinematicTestCommand())
        commandManager.register(screenTestCommand())
        commandManager.register(armorTestCommand())
        commandManager.register(command("cosmetics") {
            onPlayerExecute { CosmeticMenu.openCategoryMenu(player) }
        })

        CosmeticRegistry.loadFromResources(app.resources)
        CosmeticListener.activeConfig = mode.cosmeticConfig
        CosmeticListener.install(handler)

        mode.install(handler)

        handler.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            val player = event.player
            val playerData = PlayerStore.load(player.uuid)
            val locale = playerData?.language ?: translations.defaultLocale
            cacheLocale(player.uuid, locale)
            event.spawningInstance = mode.defaultInstance
            player.respawnPoint = mode.spawnPoint
        }

        handler.addListener(PlayerDisconnectEvent::class.java) { event ->
            evictLocale(event.player.uuid)
        }

        MinecraftServer.getSchedulerManager()
            .buildTask { OnlinePlayerCache.refresh() }
            .repeat(TaskSchedule.seconds(5))
            .schedule()

        server.start("0.0.0.0", port)

        if (serverUuid.isNotEmpty()) {
            logger.info { "Publishing ServerRegistrationMessage(serverUuid=$serverUuid, address=$serverHost)" }
            NetworkMessenger.publish(ServerRegistrationMessage(serverUuid, serverHost))
            logger.info { "ServerRegistrationMessage published" }
        } else {
            logger.warn { "P_SERVER_UUID is empty, skipping server registration" }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            if (serverUuid.isNotEmpty()) {
                logger.info { "Publishing ServerDeregistrationMessage(serverUuid=$serverUuid)" }
                NetworkMessenger.publish(ServerDeregistrationMessage(serverUuid))
            }
            ModelEngine.uninstall()
            mode.shutdown()
            app.stop().join()
        })
    }

    private fun resolveMode(): ServerMode =
        when (gameMode) {
            null -> HubMode(app.resources)
            "hoplite" -> HopliteMode(app.resources)
            else -> error("Unknown game mode: $gameMode")
        }
}
