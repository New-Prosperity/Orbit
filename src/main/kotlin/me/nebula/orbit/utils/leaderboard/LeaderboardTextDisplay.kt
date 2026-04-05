package me.nebula.orbit.utils.leaderboard

import me.nebula.gravity.ranking.Periodicity
import me.nebula.gravity.ranking.RankedPlayer
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.scheduler.repeat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.other.InteractionMeta
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val periodicities = Periodicity.entries.toTypedArray()
private val nextEntityId = AtomicInteger(-2_000_000)

private const val META_NO_GRAVITY = 5
private const val META_SCALE = 12
private const val META_BILLBOARD = 15
private const val META_TEXT = 23
private const val META_BACKGROUND_COLOR = 25
private const val META_TEXT_OPACITY = 26
private const val META_TEXT_FLAGS = 27

class LeaderboardTextDisplay @PublishedApi internal constructor(
    private val instance: Instance,
    private val display: LeaderboardDisplay,
    private val position: Pos,
    private val statKey: String,
    private val titleText: String,
    private val entriesShown: Int,
    private val refreshTicks: Int,
    private val scale: Float,
    private val backgroundColor: Int,
) {

    private val textEntityId = nextEntityId.getAndDecrement()
    private val textEntityUuid = UUID.randomUUID()

    private var interactionEntity: Entity? = null
    private val viewers = ConcurrentHashMap.newKeySet<UUID>()
    private val playerPeriodicities = ConcurrentHashMap<UUID, Periodicity>()
    private val playerModes = ConcurrentHashMap<UUID, DisplayMode>()
    private var eventNode: EventNode<*>? = null
    private var refreshTask: Task? = null

    private enum class DisplayMode { TOP_LIST, PERSONAL }

    fun spawn() {
        despawn()

        val interaction = Entity(EntityType.INTERACTION)
        val meta = interaction.entityMeta as InteractionMeta
        meta.width = 2.5f
        meta.height = (entriesShown + 4) * 0.3f
        meta.setHasNoGravity(true)
        interaction.setInstance(instance, position.add(0.0, -0.5, 0.0))
        interactionEntity = interaction

        for (player in instance.players) {
            showTo(player)
        }

        val node = EventNode.all("leaderboard-text-${System.identityHashCode(this)}")

        node.addListener(PlayerSpawnEvent::class.java) { event ->
            if (event.player.instance === instance) showTo(event.player)
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            viewers.remove(event.player.uuid)
            playerPeriodicities.remove(event.player.uuid)
            playerModes.remove(event.player.uuid)
        }

        node.addListener(EntityAttackEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            if (event.target !== interaction) return@addListener
            cyclePeriodicity(player)
        }

        node.addListener(PlayerEntityInteractEvent::class.java) { event ->
            if (event.target !== interaction) return@addListener
            toggleDisplayMode(event.player)
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node

        if (refreshTicks > 0) {
            refreshTask = repeat(refreshTicks) { refreshAll() }
        }
    }

    fun despawn() {
        refreshTask?.cancel()
        refreshTask = null
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        interactionEntity?.let { if (!it.isRemoved) it.remove() }
        interactionEntity = null

        val destroyPacket = DestroyEntitiesPacket(listOf(textEntityId))
        forEachViewer { it.sendPacket(destroyPacket) }

        viewers.clear()
        playerPeriodicities.clear()
        playerModes.clear()
    }

    fun refreshAll() {
        forEachViewer { player ->
            val periodicity = playerPeriodicities.getOrDefault(player.uuid, Periodicity.ALL_TIME)
            val mode = playerModes.getOrDefault(player.uuid, DisplayMode.TOP_LIST)
            updateTextFor(player, periodicity, mode)
        }
    }

    private fun showTo(player: Player) {
        if (!viewers.add(player.uuid)) return

        player.sendPacket(SpawnEntityPacket(
            textEntityId, textEntityUuid, EntityType.TEXT_DISPLAY,
            position, position.yaw(), 0, Vec.ZERO,
        ))
        player.sendPacket(buildTextMetadata(buildDisplayText(Periodicity.ALL_TIME, DisplayMode.TOP_LIST, null)))
    }

    private fun cyclePeriodicity(player: Player) {
        val current = playerPeriodicities.getOrDefault(player.uuid, Periodicity.ALL_TIME)
        val nextIndex = (periodicities.indexOf(current) + 1) % periodicities.size
        val next = periodicities[nextIndex]
        playerPeriodicities[player.uuid] = next

        val mode = playerModes.getOrDefault(player.uuid, DisplayMode.TOP_LIST)
        updateTextFor(player, next, mode)

        player.sendMessage(miniMessage.deserialize("<yellow>Period: <gold>${next.name}"))
    }

    private fun toggleDisplayMode(player: Player) {
        val current = playerModes.getOrDefault(player.uuid, DisplayMode.TOP_LIST)
        val next = if (current == DisplayMode.TOP_LIST) DisplayMode.PERSONAL else DisplayMode.TOP_LIST
        playerModes[player.uuid] = next

        val periodicity = playerPeriodicities.getOrDefault(player.uuid, Periodicity.ALL_TIME)
        updateTextFor(player, periodicity, next)

        val label = if (next == DisplayMode.TOP_LIST) "Top $entriesShown" else "Your Position"
        player.sendMessage(miniMessage.deserialize("<yellow>View: <gold>$label"))
    }

    private fun updateTextFor(player: Player, periodicity: Periodicity, mode: DisplayMode) {
        val text = buildDisplayText(periodicity, mode, player.uuid)
        player.sendPacket(EntityMetaDataPacket(textEntityId, mapOf(META_TEXT to Metadata.Component(text))))
    }

    private fun buildDisplayText(periodicity: Periodicity, mode: DisplayMode, viewerUuid: UUID?): Component {
        val entries = display.query(statKey, periodicity)
        val builder = Component.text()

        builder.append(miniMessage.deserialize("<bold><gold>$titleText</bold>"))
        builder.append(Component.newline())
        builder.append(miniMessage.deserialize("<dark_gray>${periodicity.name}"))
        builder.append(Component.newline())
        builder.append(Component.newline())

        when (mode) {
            DisplayMode.TOP_LIST -> {
                if (entries.isEmpty()) {
                    builder.append(miniMessage.deserialize("<gray>No data available"))
                } else {
                    for ((i, entry) in entries.take(entriesShown).withIndex()) {
                        builder.append(formatEntry(entry))
                        if (i < entriesShown - 1 && i < entries.size - 1) builder.append(Component.newline())
                    }
                }
            }
            DisplayMode.PERSONAL -> {
                if (viewerUuid == null) {
                    builder.append(miniMessage.deserialize("<gray>No player data"))
                } else {
                    val playerEntry = entries.firstOrNull { it.uuid == viewerUuid }
                    if (playerEntry != null) {
                        builder.append(miniMessage.deserialize("<yellow>Your Position:"))
                        builder.append(Component.newline())
                        builder.append(formatEntry(playerEntry))

                        val above = entries.getOrNull(playerEntry.position - 1)
                        val below = entries.getOrNull(playerEntry.position + 1)
                        if (above != null) {
                            builder.append(Component.newline())
                            builder.append(Component.newline())
                            builder.append(miniMessage.deserialize("<gray>Above you:"))
                            builder.append(Component.newline())
                            builder.append(formatEntry(above))
                        }
                        if (below != null) {
                            builder.append(Component.newline())
                            builder.append(miniMessage.deserialize("<gray>Below you:"))
                            builder.append(Component.newline())
                            builder.append(formatEntry(below))
                        }
                    } else {
                        builder.append(miniMessage.deserialize("<gray>You are not ranked yet"))
                    }
                }
            }
        }

        builder.append(Component.newline())
        builder.append(Component.newline())
        builder.append(miniMessage.deserialize("<dark_gray><italic>Left-click: period | Right-click: view"))

        return builder.build()
    }

    private fun formatEntry(entry: RankedPlayer): Component {
        val rank = entry.position + 1
        val color = when (rank) {
            1 -> NamedTextColor.GOLD
            2 -> NamedTextColor.GRAY
            3 -> NamedTextColor.DARK_RED
            else -> NamedTextColor.WHITE
        }
        val score = if (entry.score == entry.score.toLong().toDouble()) entry.score.toLong().toString()
        else "%.2f".format(entry.score)

        return Component.text("#$rank ", color)
            .append(Component.text(entry.name, NamedTextColor.WHITE))
            .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
            .append(Component.text(score, NamedTextColor.YELLOW))
    }

    private fun buildTextMetadata(text: Component): EntityMetaDataPacket =
        EntityMetaDataPacket(textEntityId, mapOf(
            META_NO_GRAVITY to Metadata.Boolean(true),
            META_SCALE to Metadata.Vector3(Vec(scale.toDouble(), scale.toDouble(), scale.toDouble())),
            META_BILLBOARD to Metadata.Byte(1),
            META_TEXT to Metadata.Component(text),
            META_BACKGROUND_COLOR to Metadata.VarInt(backgroundColor),
            META_TEXT_OPACITY to Metadata.Byte((-1).toByte()),
            META_TEXT_FLAGS to Metadata.Byte(0),
        ))

    private inline fun forEachViewer(action: (Player) -> Unit) {
        for (uuid in viewers) {
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.let(action)
        }
    }
}

class LeaderboardTextDisplayBuilder @PublishedApi internal constructor(
    @PublishedApi internal val instance: Instance,
) {
    @PublishedApi internal var display: LeaderboardDisplay? = null
    @PublishedApi internal var position: Pos = Pos.ZERO
    @PublishedApi internal var statKey: String = ""
    @PublishedApi internal var titleText: String = "Leaderboard"
    @PublishedApi internal var entriesShown: Int = 10
    @PublishedApi internal var refreshTicks: Int = 600
    @PublishedApi internal var scale: Float = 1.0f
    @PublishedApi internal var backgroundColor: Int = 0x40000000

    fun display(display: LeaderboardDisplay) { this.display = display }
    fun position(pos: Pos) { position = pos }
    fun statKey(key: String) { statKey = key }
    fun title(text: String) { titleText = text }
    fun entriesShown(count: Int) { entriesShown = count }
    fun refreshSeconds(seconds: Int) { refreshTicks = seconds * 20 }
    fun scale(value: Float) { scale = value }
    fun backgroundColor(color: Int) { backgroundColor = color }

    @PublishedApi internal fun build(): LeaderboardTextDisplay {
        requireNotNull(display) { "LeaderboardDisplay must be set" }
        require(statKey.isNotBlank()) { "statKey must be set" }

        return LeaderboardTextDisplay(
            instance = instance,
            display = display!!,
            position = position,
            statKey = statKey,
            titleText = titleText,
            entriesShown = entriesShown,
            refreshTicks = refreshTicks,
            scale = scale,
            backgroundColor = backgroundColor,
        )
    }
}

inline fun leaderboardTextDisplay(
    instance: Instance,
    block: LeaderboardTextDisplayBuilder.() -> Unit,
): LeaderboardTextDisplay = LeaderboardTextDisplayBuilder(instance).apply(block).build()
