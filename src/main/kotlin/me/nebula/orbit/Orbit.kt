package me.nebula.orbit

import me.nebula.ether.utils.app.App
import me.nebula.ether.utils.app.appDelegate
import me.nebula.ether.utils.environment.environment
import me.nebula.ether.utils.hazelcast.hazelcastModule
import me.nebula.ether.utils.translation.TranslationRegistry
import me.nebula.ether.utils.translation.translationRegistry
import me.nebula.gravity.economy.EconomyStore
import me.nebula.gravity.economy.EconomyTransactionStore
import me.nebula.gravity.player.PlayerStore
import me.nebula.gravity.player.PreferenceStore
import me.nebula.gravity.property.PropertyStore
import me.nebula.gravity.queue.PoolConfigStore
import me.nebula.gravity.queue.QueueStore
import me.nebula.gravity.rank.PlayerRankStore
import me.nebula.gravity.rank.RankStore
import me.nebula.gravity.ranking.RankingReportStore
import me.nebula.gravity.ranking.RankingStore
import me.nebula.gravity.sanction.SanctionStore
import me.nebula.gravity.session.ServerOccupancyStore
import me.nebula.gravity.session.SessionStore
import me.nebula.gravity.stats.StatsStore
import me.nebula.orbit.command.OnlinePlayerCache
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object Orbit {

    lateinit var app: App
    lateinit var translations: TranslationRegistry
    lateinit var serverName: String
        private set
    var gameMode: String? = null
        private set

    private val miniMessage = MiniMessage.miniMessage()
    private val localeCache = ConcurrentHashMap<UUID, String>()

    val isGameServer: Boolean get() = gameMode != null

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

    @JvmStatic
    fun main(args: Array<String>) {
        Thread.currentThread().contextClassLoader = Orbit::class.java.classLoader

        val env = environment {
            serverName = required("SERVER_NAME")
            gameMode = optional("GAME_MODE", "").ifEmpty { null }
            required("HAZELCAST_LICENSE")
        }

        val port = env.optional("SERVER_PORT", 25565) { it.toInt() }

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
                    }
                }
            }
            onEnable {
                translations = translationRegistry {
                    prefix("orbit")
                    defaultLocale("en")
                    fallback(true)
                }
            }
        }

        app.start().join()

        val server = MinecraftServer.init()
        val instanceManager = MinecraftServer.getInstanceManager()
        val defaultInstance = instanceManager.createInstanceContainer()
        defaultInstance.setGenerator { unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK) }

        val handler = MinecraftServer.getGlobalEventHandler()

        handler.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            val player = event.player
            val playerData = PlayerStore.load(player.uuid)
            val locale = playerData?.language ?: translations.defaultLocale
            cacheLocale(player.uuid, locale)
            event.spawningInstance = defaultInstance
            player.respawnPoint = Pos(0.0, 41.0, 0.0)
        }

        handler.addListener(PlayerDisconnectEvent::class.java) { event ->
            evictLocale(event.player.uuid)
        }

        MinecraftServer.getSchedulerManager()
            .buildTask { OnlinePlayerCache.refresh() }
            .repeat(TaskSchedule.seconds(5))
            .schedule()

        server.start("0.0.0.0", port)

        Runtime.getRuntime().addShutdownHook(Thread {
            app.modules.disableAll()
            app.stop().join()
        })
    }
}
