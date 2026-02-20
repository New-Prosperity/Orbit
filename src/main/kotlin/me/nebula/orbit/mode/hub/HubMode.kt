package me.nebula.orbit.mode.hub

import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.gravity.queue.QueueStore
import me.nebula.gravity.rank.PlayerRankStore
import me.nebula.gravity.rank.RankStore
import me.nebula.gravity.session.SessionStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.ServerMode
import me.nebula.orbit.mode.config.placeholderResolver
import me.nebula.orbit.utils.anvilloader.AnvilWorldLoader
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.hotbar.hotbar
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.lobby.Lobby
import me.nebula.orbit.utils.lobby.lobby
import me.nebula.orbit.utils.scoreboard.LiveScoreboard
import me.nebula.orbit.utils.scoreboard.liveScoreboard
import me.nebula.orbit.utils.tablist.LiveTabList
import me.nebula.orbit.utils.tablist.liveTabList
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class HubMode(resources: ResourceManager) : ServerMode {

    private val logger = logger("HubMode")
    private val config = resources.loadOrCopyDefault<HubModeConfig>("hub.json")

    private val resolver = placeholderResolver {
        global("online") { SessionStore.cachedSize.toString() }
        global("server") { Orbit.serverName }
        perPlayer("rank") { player ->
            val rankData = PlayerRankStore.load(player.uuid)
            rankData?.let { RankStore.load(it.rank)?.name } ?: "Member"
        }
    }

    override val spawnPoint: Pos = config.spawn.toPos()

    override val defaultInstance: InstanceContainer = createInstance()

    private lateinit var lobby: Lobby
    private lateinit var scoreboard: LiveScoreboard
    private lateinit var tabList: LiveTabList
    private lateinit var hotbar: me.nebula.orbit.utils.hotbar.Hotbar

    private fun createInstance(): InstanceContainer {
        val worldPath = Path.of(config.worldPath)
        if (Files.isDirectory(worldPath)) {
            logger.info { "Loading hub world from ${worldPath.toAbsolutePath()}" }
            val centerChunkX = spawnPoint.blockX() shr 4
            val centerChunkZ = spawnPoint.blockZ() shr 4
            val (instance, future) = AnvilWorldLoader.loadAndPreload(
                "hub", worldPath, centerChunkX, centerChunkZ, config.preloadRadius
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

        val selectorGui = gui(config.selector.title, config.selector.rows) {
            border(Material.fromKey(config.selector.border) ?: Material.GRAY_STAINED_GLASS_PANE)
        }

        val actions = mapOf<String, (net.minestom.server.entity.Player) -> Unit>(
            "open_selector" to { player -> selectorGui.open(player) }
        )

        scoreboard = liveScoreboard {
            if (resolver.hasPlaceholders(config.scoreboard.title)) {
                title { player -> resolver.resolve(config.scoreboard.title, player) }
            } else {
                title(config.scoreboard.title)
            }
            refreshEvery(config.scoreboard.refreshSeconds.seconds)
            for (line in config.scoreboard.lines) {
                if (resolver.hasPlaceholders(line)) {
                    line { player -> resolver.resolve(line, player) }
                } else {
                    line(line)
                }
            }
        }

        tabList = liveTabList {
            refreshEvery(config.tabList.refreshSeconds.seconds)
            if (resolver.hasPlaceholders(config.tabList.header)) {
                header { player -> resolver.resolve(config.tabList.header, player) }
            } else {
                header(config.tabList.header)
            }
            if (resolver.hasPlaceholders(config.tabList.footer)) {
                footer { player -> resolver.resolve(config.tabList.footer, player) }
            } else {
                footer(config.tabList.footer)
            }
        }

        hotbar = hotbar("hub") {
            for (item in config.hotbar) {
                val material = Material.fromKey(item.material)
                if (material == null) {
                    logger.warn { "Unknown material: ${item.material}" }
                    continue
                }
                slot(item.slot, itemStack(material) {
                    name(item.name)
                    if (item.glowing) glowing()
                }) { player ->
                    actions[item.action]?.invoke(player)
                        ?: logger.warn { "Unknown hotbar action: ${item.action}" }
                }
            }
        }
        hotbar.install()

        lobby = lobby {
            instance = defaultInstance
            this.spawnPoint = this@HubMode.spawnPoint
            gameMode = GameMode.valueOf(config.lobby.gameMode)
            protectBlocks = config.lobby.protectBlocks
            disableDamage = config.lobby.disableDamage
            disableHunger = config.lobby.disableHunger
            lockInventory = config.lobby.lockInventory
            voidTeleportY = config.lobby.voidTeleportY
        }
        lobby.install()

        handler.addListener(PlayerSpawnEvent::class.java) { event ->
            if (!event.isFirstSpawn) return@addListener
            hotbar.apply(event.player)
        }

        handler.addListener(PlayerDisconnectEvent::class.java) { event ->
            hotbar.remove(event.player)
        }

        logger.info { "Hub mode installed" }
    }

    override fun shutdown() {
        scoreboard.uninstall()
        tabList.uninstall()
        lobby.uninstall()
        hotbar.uninstall()
    }
}
