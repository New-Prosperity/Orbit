package me.nebula.orbit.mode.hub

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.messaging.HostProvisionStatusMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.QueueAssignmentMessage
import me.nebula.gravity.messaging.QueuePositionMessage
import me.nebula.gravity.messaging.QueueRemovedMessage
import me.nebula.gravity.rank.PlayerRankStore
import me.nebula.gravity.rank.RankStore
import me.nebula.gravity.session.SessionStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.ServerMode
import me.nebula.orbit.translation.resolveTranslated
import me.nebula.orbit.translation.translate
import me.nebula.orbit.cosmetic.CosmeticMenu
import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.mode.config.placeholderResolver
import me.nebula.orbit.progression.BattlePassMenu
import me.nebula.orbit.progression.achievement.AchievementMenu
import me.nebula.orbit.progression.mission.MissionMenu
import me.nebula.orbit.utils.anvilloader.AnvilWorldLoader
import me.nebula.orbit.utils.hotbar.hotbar
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.lobby.Lobby
import me.nebula.orbit.utils.lobby.lobby
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.generator.ModelGenerator
import me.nebula.orbit.utils.modelengine.model.standAloneModel
import me.nebula.orbit.utils.modelengine.modeledEntity
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
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class HubMode : ServerMode {

    private val logger = logger("HubMode")
    private val config = HubDefinitions.CONFIG

    private val resolver = placeholderResolver {
        global("online") { SessionStore.cachedSize.toString() }
        global("server") { Orbit.serverName }
        perPlayer("rank") { player ->
            val rankData = PlayerRankStore.load(player.uuid)
            rankData?.let { RankStore.load(it.rank)?.name } ?: "Member"
        }
    }

    override val cosmeticConfig: CosmeticConfig = config.cosmetics ?: CosmeticConfig()

    override val spawnPoint: Pos = config.spawn.toPos()

    override val defaultInstance: InstanceContainer = createInstance()

    private lateinit var lobby: Lobby
    private lateinit var scoreboard: LiveScoreboard
    private lateinit var tabList: LiveTabList
    private lateinit var hotbar: me.nebula.orbit.utils.hotbar.Hotbar
    private var hostStatusSubscription: UUID? = null
    private var queueRemovedSubscription: UUID? = null
    private var queueAssignmentSubscription: UUID? = null
    private var queuePositionSubscription: UUID? = null

    private fun createInstance(): InstanceContainer {
        val worldPath = me.nebula.orbit.utils.maploader.MapLoader.resolve("hub")
        logger.info { "Loading hub world from $worldPath" }
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

    override fun install(handler: GlobalEventHandler) {
        logger.info { "Installing hub mode (spawn=$spawnPoint)" }

        val actions = mapOf<String, (net.minestom.server.entity.Player) -> Unit>(
            "open_selector" to { player -> SelectorMenu.open(player) },
            "open_host" to { player -> HostMenu.openGameModeMenu(player) },
            "open_cosmetics" to { player -> CosmeticMenu.openCategoryMenu(player) },
            "open_battlepass" to { player -> BattlePassMenu.open(player) },
            "open_missions" to { player -> MissionMenu.open(player) },
            "open_achievements" to { player -> AchievementMenu.open(player) },
        )

        scoreboard = liveScoreboard {
            title { player -> resolver.resolveTranslated(config.scoreboard.title, player) }
            refreshEvery(config.scoreboard.refreshSeconds.seconds)
            for (line in config.scoreboard.lines) {
                line { player -> resolver.resolveTranslated(line, player) }
            }
        }

        tabList = liveTabList {
            refreshEvery(config.tabList.refreshSeconds.seconds)
            header { player -> resolver.resolveTranslated(config.tabList.header, player) }
            footer { player -> resolver.resolveTranslated(config.tabList.footer, player) }
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
            HostMenu.removePending(event.player.uuid)
            SelectorMenu.removeQueued(event.player.uuid)
        }

        hostStatusSubscription = NetworkMessenger.subscribe<HostProvisionStatusMessage> { msg ->
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(msg.hostOwner)
            when (msg.status) {
                "PROVISIONING" -> player?.let { it.sendMessage(it.translate("orbit.host.status.provisioning")) }
                "READY" -> {
                    HostMenu.removePending(msg.hostOwner)
                    player?.let { it.sendMessage(it.translate("orbit.host.status.ready")) }
                }
                "FAILED" -> {
                    HostMenu.removePending(msg.hostOwner)
                    player?.let { it.sendMessage(it.translate("orbit.host.status.failed",
                        "reason" to (msg.failureReason ?: "unknown"))) }
                }
            }
        }

        queueRemovedSubscription = NetworkMessenger.subscribe<QueueRemovedMessage> { msg ->
            SelectorMenu.removeQueued(msg.playerId)
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(msg.playerId)
            player?.sendMessage(player.translate("orbit.queue.removed"))
        }

        queueAssignmentSubscription = NetworkMessenger.subscribe<QueueAssignmentMessage> { msg ->
            for (playerId in msg.playerIds) {
                SelectorMenu.removeQueued(playerId)
            }
        }

        queuePositionSubscription = NetworkMessenger.subscribe<QueuePositionMessage> { msg ->
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(msg.playerId)
            player?.sendActionBar(player.translate("orbit.queue.position",
                "position" to msg.position.toString(),
                "total" to msg.totalInQueue.toString()))
        }

        logger.info { "Hub mode installed" }
    }

    override fun shutdown() {
        hostStatusSubscription?.let { NetworkMessenger.unsubscribe<HostProvisionStatusMessage>(it) }
        queueRemovedSubscription?.let { NetworkMessenger.unsubscribe<QueueRemovedMessage>(it) }
        queueAssignmentSubscription?.let { NetworkMessenger.unsubscribe<QueueAssignmentMessage>(it) }
        queuePositionSubscription?.let { NetworkMessenger.unsubscribe<QueuePositionMessage>(it) }
        scoreboard.uninstall()
        tabList.uninstall()
        lobby.uninstall()
        hotbar.uninstall()
    }
}
