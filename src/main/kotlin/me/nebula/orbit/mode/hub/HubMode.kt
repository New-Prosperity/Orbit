package me.nebula.orbit.mode.hub

import me.nebula.ether.utils.environment.EnvironmentVariableBuilder
import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.rank.PlayerRankStore
import me.nebula.gravity.rank.RankStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.ServerMode
import me.nebula.orbit.utils.anvilloader.AnvilWorldLoader
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.hotbar.hotbar
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.lobby.Lobby
import me.nebula.orbit.utils.lobby.lobby
import me.nebula.orbit.utils.scoreboard.PerPlayerScoreboard
import me.nebula.orbit.utils.scoreboard.perPlayerScoreboard
import me.nebula.orbit.utils.tablist.tabList
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.nio.file.Files
import java.nio.file.Path

class HubMode(env: EnvironmentVariableBuilder) : ServerMode {

    private val logger = logger("HubMode")
    private val translations = Orbit.translations

    override val spawnPoint: Pos = Pos(
        env.optional("HUB_SPAWN_X", 0.5) { it.toDouble() },
        env.optional("HUB_SPAWN_Y", 65.0) { it.toDouble() },
        env.optional("HUB_SPAWN_Z", 0.5) { it.toDouble() },
        env.optional("HUB_SPAWN_YAW", 0.0f) { it.toFloat() },
        env.optional("HUB_SPAWN_PITCH", 0.0f) { it.toFloat() },
    )

    override val defaultInstance: InstanceContainer = createInstance()

    private lateinit var lobby: Lobby
    private lateinit var scoreboard: PerPlayerScoreboard
    private lateinit var hotbar: me.nebula.orbit.utils.hotbar.Hotbar
    private var updateTask: Task? = null

    private fun createInstance(): InstanceContainer {
        val worldPath = Path.of("worlds/hub")
        if (Files.isDirectory(worldPath)) {
            logger.info { "Loading hub world from ${worldPath.toAbsolutePath()}" }
            val centerChunkX = spawnPoint.blockX() shr 4
            val centerChunkZ = spawnPoint.blockZ() shr 4
            val (instance, future) = AnvilWorldLoader.loadAndPreload(
                "hub", worldPath, centerChunkX, centerChunkZ, 6
            )
            future.join()
            if (!AnvilWorldLoader.verifyLoaded(instance, spawnPoint)) {
                logger.warn { "Hub world loaded but no blocks found at spawn $spawnPoint — world may be empty or built at different coordinates" }
            }
            return instance
        }
        logger.warn { "No hub world at ${worldPath.toAbsolutePath()}, using flat generator" }
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator { unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK) }
        return instance
    }

    override fun install(handler: GlobalEventHandler) {
        logger.info { "Installing hub mode (spawn=$spawnPoint)" }

        scoreboard = perPlayerScoreboard(
            translations.require("orbit.hub.scoreboard.title", "en"),
        ) {
            line("")
            line(translations.require("orbit.hub.scoreboard.online", "en"))
            line(translations.require("orbit.hub.scoreboard.rank", "en"))
            line("")
            line(translations.require("orbit.hub.scoreboard.server", "en"))
            line("")
            line(translations.require("orbit.hub.scoreboard.website", "en"))
        }

        val selectorGui = gui(
            translations.require("orbit.hub.selector.title", "en"),
            3,
        ) {
            border(Material.GRAY_STAINED_GLASS_PANE)
        }

        hotbar = hotbar("hub") {
            slot(4, itemStack(Material.COMPASS) {
                name(translations.require("orbit.hub.selector.item", "en"))
                glowing()
            }) { player ->
                selectorGui.open(player)
            }
        }
        hotbar.install()

        lobby = lobby {
            instance = defaultInstance
            this.spawnPoint = this@HubMode.spawnPoint
            gameMode = GameMode.ADVENTURE
            protectBlocks = true
            disableDamage = true
            disableHunger = true
            lockInventory = true
            voidTeleportY = -64.0
        }
        lobby.install()

        handler.addListener(PlayerSpawnEvent::class.java) { event ->
            if (!event.isFirstSpawn) return@addListener
            val player = event.player
            val onlineCount = MinecraftServer.getConnectionManager().onlinePlayers.size.toString()
            val rankData = PlayerRankStore.load(player.uuid)
            val rankName = rankData?.let { RankStore.load(it.rank)?.name } ?: "Member"

            hotbar.apply(player)

            player.tabList {
                header(translations.require("orbit.hub.tab.header", "en"))
                footer(
                    translations.require("orbit.hub.tab.footer", "en")
                        .replace("<online>", onlineCount)
                        .replace("<server>", Orbit.serverName)
                )
            }

            scoreboard.show(player, mapOf(
                "online" to onlineCount,
                "rank" to rankName,
                "server" to Orbit.serverName,
            ))
        }

        handler.addListener(PlayerDisconnectEvent::class.java) { event ->
            scoreboard.hide(event.player)
            hotbar.remove(event.player)
        }

        updateTask = MinecraftServer.getSchedulerManager().buildTask {
            val onlineCount = MinecraftServer.getConnectionManager().onlinePlayers.size.toString()
            MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
                val rankData = PlayerRankStore.load(player.uuid)
                val rankName = rankData?.let { RankStore.load(it.rank)?.name } ?: "Member"
                scoreboard.update(player, mapOf(
                    "online" to onlineCount,
                    "rank" to rankName,
                    "server" to Orbit.serverName,
                ))
                player.tabList {
                    header(translations.require("orbit.hub.tab.header", "en"))
                    footer(
                        translations.require("orbit.hub.tab.footer", "en")
                            .replace("<online>", onlineCount)
                            .replace("<server>", Orbit.serverName)
                    )
                }
            }
        }.repeat(TaskSchedule.seconds(5)).schedule()

        logger.info { "Hub mode installed" }
    }

    override fun shutdown() {
        updateTask?.cancel()
        lobby.uninstall()
        hotbar.uninstall()
    }
}
