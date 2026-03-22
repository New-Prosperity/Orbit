package me.nebula.orbit.mode.hub

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.messaging.GameEndMessage
import me.nebula.gravity.messaging.HostProvisionStatusMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.PartyQueueNotificationMessage
import me.nebula.gravity.messaging.QueueAssignmentMessage
import me.nebula.gravity.messaging.QueuePositionMessage
import me.nebula.gravity.messaging.QueueProvisioningMessage
import me.nebula.gravity.messaging.QueueRemovedMessage
import me.nebula.gravity.property.NetworkProperties
import me.nebula.gravity.property.PropertyStore
import me.nebula.gravity.rank.RankManager
import me.nebula.orbit.rankName
import me.nebula.orbit.rankColor
import me.nebula.orbit.rankPrefix
import me.nebula.orbit.rankWeight
import me.nebula.gravity.session.SessionStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.ServerMode
import me.nebula.orbit.translation.resolveTranslated
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.cosmetic.CosmeticMenu
import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.mode.config.placeholderResolver
import me.nebula.orbit.progression.BattlePassMenu
import me.nebula.orbit.progression.achievement.AchievementMenu
import me.nebula.orbit.progression.mission.MissionMenu
import me.nebula.orbit.utils.anvilloader.AnvilWorldLoader
import me.nebula.orbit.utils.maploader.MapLoader
import me.nebula.orbit.utils.hotbar.hotbar
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.lobby.Lobby
import me.nebula.orbit.utils.lobby.lobby
import me.nebula.orbit.utils.scoreboard.LiveScoreboard
import me.nebula.orbit.utils.scoreboard.liveScoreboard
import me.nebula.orbit.utils.tablist.LiveTabList
import me.nebula.orbit.utils.tablist.liveTabList
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.TaskSchedule
import me.nebula.gravity.cache.PlayerCache
import me.nebula.gravity.leveling.LevelFormula
import me.nebula.orbit.cached
import me.nebula.orbit.utils.counter.AnimatedCounterManager
import me.nebula.orbit.utils.counter.Easing
import me.nebula.orbit.utils.sound.playSound
import net.minestom.server.entity.Player
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class HubMode : ServerMode {

    private val logger = logger("HubMode")
    private val config = HubDefinitions.CONFIG

    private val resolver = placeholderResolver {
        global("online") { SessionStore.cachedSize.toString() }
        global("server") { Orbit.serverName }
        perPlayer("rank") { player -> player.rankName }
    }

    override val maxPlayers: Int get() = PropertyStore[NetworkProperties.HUB_SLOTS]

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
    private var queueProvisioningSubscription: UUID? = null
    private var partyQueueSubscription: UUID? = null
    private var gameEndSubscription: UUID? = null

    private fun scheduleSync(block: () -> Unit) {
        MinecraftServer.getSchedulerManager().buildTask(block).schedule()
    }

    private inline fun <reified T : me.nebula.gravity.messaging.NetworkMessage> subscribeSync(
        noinline handler: (T) -> Unit
    ): UUID = NetworkMessenger.subscribe<T> { msg -> scheduleSync { handler(msg) } }

    private inline fun <reified T : me.nebula.gravity.messaging.NetworkMessage> subscribeSyncForPlayer(
        crossinline extractId: (T) -> UUID,
        crossinline handler: (T, net.minestom.server.entity.Player) -> Unit
    ): UUID = NetworkMessenger.subscribe<T> { msg ->
        scheduleSync {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(extractId(msg)) ?: return@scheduleSync
            handler(msg, player)
        }
    }

    private inline fun <reified T : me.nebula.gravity.messaging.NetworkMessage> subscribeSyncForPlayers(
        crossinline extractIds: (T) -> List<UUID>,
        crossinline handler: (T, net.minestom.server.entity.Player) -> Unit
    ): UUID = NetworkMessenger.subscribe<T> { msg ->
        scheduleSync {
            for (playerId in extractIds(msg)) {
                val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId) ?: continue
                handler(msg, player)
            }
        }
    }

    private fun createInstance(): InstanceContainer {
        val worldPath = MapLoader.resolve("hub", "hub")
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

        setupHubTabEntries(handler)

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
            syncExperienceBar(event.player)
        }

        handler.addListener(PlayerDisconnectEvent::class.java) { event ->
            hotbar.remove(event.player)
            HostMenu.removePending(event.player.uuid)
            SelectorMenu.removeAllQueued(event.player.uuid)
        }

        hostStatusSubscription = subscribeSyncForPlayer<HostProvisionStatusMessage>({ it.hostOwner }) { msg, player ->
            when (msg.status) {
                "PROVISIONING" -> player.sendMessage(player.translate("orbit.host.status.provisioning"))
                "READY" -> {
                    HostMenu.removePending(msg.hostOwner)
                    player.sendMessage(player.translate("orbit.host.status.ready"))
                }
                "FAILED" -> {
                    HostMenu.removePending(msg.hostOwner)
                    player.sendMessage(player.translate("orbit.host.status.failed",
                        "reason" to (msg.failureReason ?: "unknown")))
                }
            }
        }

        queueRemovedSubscription = NetworkMessenger.subscribe<QueueRemovedMessage> { msg ->
            SelectorMenu.removeQueued(msg.playerId, msg.gameMode)
            scheduleSync {
                val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(msg.playerId) ?: return@scheduleSync
                player.sendMessage(player.translate("orbit.queue.removed", "gamemode" to msg.gameMode))
                player.playSound(SoundEvent.BLOCK_NOTE_BLOCK_BASS, 1f, 1f)
            }
        }

        queueAssignmentSubscription = NetworkMessenger.subscribe<QueueAssignmentMessage> { msg ->
            for (playerId in msg.playerIds) { SelectorMenu.removeAllQueued(playerId) }
            scheduleSync {
                for (playerId in msg.playerIds) {
                    val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId) ?: continue
                    player.playSound(SoundEvent.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                }
            }
        }

        queuePositionSubscription = subscribeSyncForPlayer<QueuePositionMessage>({ it.playerId }) { msg, player ->
            val estSeconds = if (msg.estimatedWaitMs > 0) (msg.estimatedWaitMs / 1000).toString() else "?"
            player.sendActionBar(player.translate("orbit.queue.position",
                "position" to msg.position.toString(),
                "total" to msg.totalInQueue.toString(),
                "estimate" to estSeconds))
            player.playSound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1f)
        }

        queueProvisioningSubscription = subscribeSyncForPlayers<QueueProvisioningMessage>({ it.playerIds }) { _, player ->
            player.sendActionBar(player.translate("orbit.queue.provisioning"))
        }

        partyQueueSubscription = subscribeSyncForPlayers<PartyQueueNotificationMessage>({ it.partyMemberIds }) { msg, player ->
            player.sendActionBar(player.translate("orbit.queue.party_queued",
                "leader" to msg.leaderName,
                "gamemode" to msg.gameMode))
            player.playSound(SoundEvent.UI_BUTTON_CLICK, 1f, 1f)
        }

        gameEndSubscription = subscribeSyncForPlayers<GameEndMessage>({ it.playerIds }) { msg, player ->
            player.sendMessage(player.translate("orbit.queue.requeue_prompt", "gamemode" to msg.gameMode))
        }

        logger.info { "Hub mode installed" }
    }

    private companion object {
        const val TAB_ROWS = 20
        const val TAB_TOTAL = 4 * TAB_ROWS
        const val COLUMN1 = TAB_ROWS
        const val HEADER_SLOT = COLUMN1
        const val PLAYER_START = HEADER_SLOT + 1
        const val PLAYER_SLOTS = TAB_TOTAL - PLAYER_START
        const val MAX_VISIBLE = PLAYER_SLOTS - 1
    }

    private val tabFakeUuids = Array(TAB_TOTAL) { UUID.nameUUIDFromBytes("hubtab:$it".toByteArray()) }
    private val tabMm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
    private val tabSentTo = java.util.concurrent.ConcurrentHashMap.newKeySet<UUID>()

    private fun setupHubTabEntries(handler: GlobalEventHandler) {
        handler.addListener(PlayerSpawnEvent::class.java) { event ->
            if (!event.isFirstSpawn) return@addListener
            sendFullTabList(event.player)
            MinecraftServer.getSchedulerManager().buildTask {
                broadcastTabUpdate()
            }.delay(TaskSchedule.tick(2)).schedule()
        }

        handler.addListener(PlayerDisconnectEvent::class.java) { event ->
            tabSentTo.remove(event.player.uuid)
            MinecraftServer.getSchedulerManager().buildTask {
                broadcastTabUpdate()
            }.delay(TaskSchedule.tick(2)).schedule()
        }

        MinecraftServer.getSchedulerManager()
            .buildTask { broadcastTabUpdate() }
            .repeat(TaskSchedule.seconds(5))
            .schedule()
    }

    private fun sendFullTabList(viewer: net.minestom.server.entity.Player) {
        val entries = buildTabEntries(viewer)
        if (tabSentTo.add(viewer.uuid)) {
            viewer.sendPacket(PlayerInfoUpdatePacket(
                java.util.EnumSet.of(
                    PlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    PlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                    PlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                    PlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER,
                ),
                entries,
            ))
        } else {
            viewer.sendPacket(PlayerInfoUpdatePacket(
                java.util.EnumSet.of(
                    PlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                    PlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER,
                ),
                entries,
            ))
        }
    }

    private fun broadcastTabUpdate() {
        for (viewer in MinecraftServer.getConnectionManager().onlinePlayers) {
            if (tabSentTo.contains(viewer.uuid)) sendFullTabList(viewer)
        }
    }

    private fun buildTabEntries(viewer: net.minestom.server.entity.Player): List<PlayerInfoUpdatePacket.Entry> {
        val sorted = MinecraftServer.getConnectionManager().onlinePlayers
            .sortedWith(
                compareBy<net.minestom.server.entity.Player> { it.rankWeight }
                    .thenBy { it.username.lowercase() }
            )

        val hasOverflow = sorted.size > MAX_VISIBLE
        val displayCount = if (hasOverflow) MAX_VISIBLE else sorted.size
        val overflowCount = if (hasOverflow) sorted.size - MAX_VISIBLE else 0

        val entries = mutableListOf<PlayerInfoUpdatePacket.Entry>()

        for (i in 0 until COLUMN1) {
            entries += fakeEntry(tabFakeUuids[i], i, net.kyori.adventure.text.Component.text(" "))
        }

        val headerText = viewer.translateRaw("orbit.tab.players_header", "count" to sorted.size.toString())
        entries += fakeEntry(tabFakeUuids[HEADER_SLOT], HEADER_SLOT, tabMm.deserialize(headerText))

        for (i in 0 until displayCount) {
            val player = sorted[i]
            val name = tabMm.deserialize("${player.rankPrefix}<${player.rankColor}>${player.username}")
            entries += fakeEntry(tabFakeUuids[PLAYER_START + i], PLAYER_START + i, name)
        }

        var nextSlot = PLAYER_START + displayCount

        if (hasOverflow) {
            val text = viewer.translateRaw("orbit.tab.overflow", "count" to overflowCount.toString())
            entries += fakeEntry(tabFakeUuids[nextSlot], nextSlot, tabMm.deserialize(text))
            nextSlot++
        }

        while (nextSlot < TAB_TOTAL) {
            entries += fakeEntry(tabFakeUuids[nextSlot], nextSlot, net.kyori.adventure.text.Component.text(" "))
            nextSlot++
        }

        return entries
    }

    private fun fakeEntry(uuid: UUID, slot: Int, displayName: net.kyori.adventure.text.Component): PlayerInfoUpdatePacket.Entry =
        PlayerInfoUpdatePacket.Entry(
            uuid, "!tab_%03d".format(slot), emptyList(),
            true, -1, GameMode.SURVIVAL,
            displayName, null, TAB_TOTAL - 1 - slot, false,
        )

    private fun syncExperienceBar(player: Player) {
        val data = player.cached.level
        player.level = data.level
        player.exp = LevelFormula.progressPercent(data.xp).toFloat()
    }

    fun animateXpGain(player: Player, oldXp: Long, newXp: Long) {
        val oldLevel = LevelFormula.levelFromXp(oldXp)
        val newLevel = LevelFormula.levelFromXp(newXp)

        AnimatedCounterManager.start(
            player = player,
            id = "hub_xp_bar",
            from = oldXp,
            to = newXp,
            durationTicks = 50,
            easing = Easing.EASE_OUT_CUBIC,
            onTick = { currentXp ->
                val currentLevel = LevelFormula.levelFromXp(currentXp)
                player.level = currentLevel
                player.exp = LevelFormula.progressPercent(currentXp).toFloat()
            },
            onComplete = {
                if (newLevel > oldLevel) {
                    player.playSound(SoundEvent.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                }
            },
        )

        val ticksBetweenSounds = 6
        val totalSoundTicks = 40
        var soundTick = 0
        MinecraftServer.getSchedulerManager().buildTask {
            if (soundTick >= totalSoundTicks) return@buildTask
            soundTick += ticksBetweenSounds
            player.playSound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 0.8f + (soundTick.toFloat() / totalSoundTicks) * 0.8f)
        }.repeat(TaskSchedule.tick(ticksBetweenSounds)).schedule()
    }

    override fun shutdown() {
        hostStatusSubscription?.let { NetworkMessenger.unsubscribe<HostProvisionStatusMessage>(it) }
        queueRemovedSubscription?.let { NetworkMessenger.unsubscribe<QueueRemovedMessage>(it) }
        queueAssignmentSubscription?.let { NetworkMessenger.unsubscribe<QueueAssignmentMessage>(it) }
        queuePositionSubscription?.let { NetworkMessenger.unsubscribe<QueuePositionMessage>(it) }
        queueProvisioningSubscription?.let { NetworkMessenger.unsubscribe<QueueProvisioningMessage>(it) }
        partyQueueSubscription?.let { NetworkMessenger.unsubscribe<PartyQueueNotificationMessage>(it) }
        gameEndSubscription?.let { NetworkMessenger.unsubscribe<GameEndMessage>(it) }
        scoreboard.uninstall()
        tabList.uninstall()
        lobby.uninstall()
        hotbar.uninstall()
    }
}
