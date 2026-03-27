package me.nebula.orbit.utils.tablist

import me.nebula.orbit.utils.chat.miniMessage
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket.Action
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket.Property
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TabEntryDef(
    val id: String,
    val uuid: UUID,
    var displayName: Component,
    var listOrder: Int,
    val skinValue: String?,
    val skinSignature: String?,
    var ping: Int,
    val visibleWhen: ((Player) -> Boolean)?,
    val contentProvider: ((Player) -> Component)?,
)

class TabEntryBuilder @PublishedApi internal constructor(
    @PublishedApi internal val id: String,
) {
    @PublishedApi internal var displayName: Component = Component.empty()
    @PublishedApi internal var listOrder: Int = 50
    @PublishedApi internal var skinValue: String? = null
    @PublishedApi internal var skinSignature: String? = null
    @PublishedApi internal var ping: Int = 0
    @PublishedApi internal var visibleWhen: ((Player) -> Boolean)? = null
    @PublishedApi internal var contentProvider: ((Player) -> Component)? = null

    fun displayName(text: String) { displayName = miniMessage.deserialize(text) }
    fun displayName(component: Component) { displayName = component }
    fun dynamicName(provider: (Player) -> Component) { contentProvider = provider }
    fun dynamicNameMM(provider: (Player) -> String) { contentProvider = { miniMessage.deserialize(provider(it)) } }
    fun listOrder(order: Int) { listOrder = order }
    fun skin(value: String, signature: String? = null) { skinValue = value; skinSignature = signature }
    fun ping(ms: Int) { ping = ms }
    fun visibleWhen(predicate: (Player) -> Boolean) { visibleWhen = predicate }
}

class PlayerFormatDef(
    val displayName: ((Player) -> Component)?,
    val listOrder: Int,
)

class PlayerFormatBuilder @PublishedApi internal constructor() {
    @PublishedApi internal var displayName: ((Player) -> Component)? = null
    @PublishedApi internal var listOrder: Int = 50

    fun displayName(text: String) { displayName = { miniMessage.deserialize(text) } }
    fun displayName(component: Component) { displayName = { component } }
    fun displayName(provider: (Player) -> Component) { displayName = provider }
    fun displayNameMM(provider: (Player) -> String) { displayName = { miniMessage.deserialize(provider(it)) } }
    fun listOrder(order: Int) { listOrder = order }
}

object TabListManager {

    private val fakeEntries = ConcurrentHashMap<UUID, ConcurrentHashMap<String, TabEntryDef>>()
    private val playerFormats = ConcurrentHashMap<UUID, PlayerFormatDef>()
    private var globalFormat: ((Player) -> PlayerFormatDef)? = null

    fun setGlobalFormat(formatter: (Player) -> PlayerFormatDef) {
        globalFormat = formatter
    }

    fun formatPlayer(player: Player, format: PlayerFormatDef) {
        playerFormats[player.uuid] = format
        applyPlayerFormat(player, format)
    }

    fun addEntry(viewer: Player, id: String, def: TabEntryDef) {
        val entries = fakeEntries.getOrPut(viewer.uuid) { ConcurrentHashMap() }
        entries[id]?.let { old -> removeEntryPacket(viewer, old.uuid) }
        entries[id] = def
        if (def.visibleWhen?.invoke(viewer) != false) {
            sendAddPacket(viewer, def)
        }
    }

    fun updateEntry(viewer: Player, id: String, displayName: Component) {
        val def = fakeEntries[viewer.uuid]?.get(id) ?: return
        def.displayName = displayName
        sendUpdatePacket(viewer, def)
    }

    fun removeEntry(viewer: Player, id: String) {
        val def = fakeEntries[viewer.uuid]?.remove(id) ?: return
        removeEntryPacket(viewer, def.uuid)
    }

    fun getEntry(viewer: Player, id: String): TabEntryDef? =
        fakeEntries[viewer.uuid]?.get(id)

    fun slot(viewer: Player, id: String): TabEntryDef? = getEntry(viewer, id)

    fun clearEntries(viewer: Player) {
        val entries = fakeEntries.remove(viewer.uuid) ?: return
        if (entries.isNotEmpty()) {
            viewer.sendPacket(PlayerInfoRemovePacket(entries.values.map { it.uuid }))
        }
    }

    fun setPlayerListed(target: Player, listed: Boolean) {
        val entry = PlayerInfoUpdatePacket.Entry(
            target.uuid, target.username, emptyList(),
            listed, target.latency, target.gameMode,
            target.displayName, null, 0, true,
        )
        target.sendPacketToViewersAndSelf(
            PlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_LISTED), listOf(entry))
        )
    }

    fun updateEntryListOrder(viewer: Player, id: String, listOrder: Int) {
        val def = fakeEntries[viewer.uuid]?.get(id) ?: return
        def.listOrder = listOrder
        val entry = PlayerInfoUpdatePacket.Entry(
            def.uuid, def.id, emptyList(),
            true, def.ping, GameMode.SURVIVAL,
            def.displayName, null, listOrder, false,
        )
        viewer.sendPacket(
            PlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_LIST_ORDER), listOf(entry))
        )
    }

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            fakeEntries.remove(event.player.uuid)
            playerFormats.remove(event.player.uuid)
        }
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            val player = event.player
            val format = globalFormat?.invoke(player)
            if (format != null) {
                playerFormats[player.uuid] = format
                applyPlayerFormat(player, format)
            }
        }
    }

    fun tick() {
        for (viewer in MinecraftServer.getConnectionManager().onlinePlayers) {
            val entries = fakeEntries[viewer.uuid] ?: continue
            for (def in entries.values) {
                val shouldBeVisible = def.visibleWhen?.invoke(viewer) != false
                val provider = def.contentProvider
                if (provider != null && shouldBeVisible) {
                    def.displayName = provider(viewer)
                    sendUpdatePacket(viewer, def)
                }
            }
        }

        val formatter = globalFormat ?: return
        for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
            val format = formatter(player)
            val existing = playerFormats[player.uuid]
            if (existing == null || existing.listOrder != format.listOrder) {
                playerFormats[player.uuid] = format
                applyPlayerFormat(player, format)
            } else {
                val newName = format.displayName?.invoke(player)
                if (newName != null && newName != player.displayName) {
                    player.displayName = newName
                }
            }
        }
    }

    private fun applyPlayerFormat(player: Player, format: PlayerFormatDef) {
        format.displayName?.invoke(player)?.let { player.displayName = it }
        val entry = PlayerInfoUpdatePacket.Entry(
            player.uuid, player.username, emptyList(),
            true, player.latency, player.gameMode,
            player.displayName, null, format.listOrder, true,
        )
        player.sendPacketToViewersAndSelf(
            PlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_LIST_ORDER, Action.UPDATE_DISPLAY_NAME), listOf(entry))
        )
    }

    private fun sendAddPacket(viewer: Player, def: TabEntryDef) {
        val properties = buildSkinProperties(def.skinValue, def.skinSignature)
        val resolved = def.contentProvider?.invoke(viewer) ?: def.displayName
        val entry = PlayerInfoUpdatePacket.Entry(
            def.uuid, def.id, properties,
            true, def.ping, GameMode.SURVIVAL,
            resolved, null, def.listOrder, false,
        )
        viewer.sendPacket(
            PlayerInfoUpdatePacket(
                EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_DISPLAY_NAME, Action.UPDATE_LISTED, Action.UPDATE_LIST_ORDER),
                listOf(entry),
            )
        )
    }

    private fun sendUpdatePacket(viewer: Player, def: TabEntryDef) {
        val resolved = def.contentProvider?.invoke(viewer) ?: def.displayName
        val entry = PlayerInfoUpdatePacket.Entry(
            def.uuid, def.id, emptyList(),
            true, def.ping, GameMode.SURVIVAL,
            resolved, null, def.listOrder, false,
        )
        viewer.sendPacket(
            PlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_DISPLAY_NAME), listOf(entry))
        )
    }

    private fun removeEntryPacket(viewer: Player, uuid: UUID) {
        viewer.sendPacket(PlayerInfoRemovePacket(listOf(uuid)))
    }

    private fun buildSkinProperties(value: String?, signature: String?): List<Property> {
        if (value == null) return emptyList()
        return listOf(Property("textures", value, signature))
    }
}

inline fun Player.addTabEntry(id: String, block: TabEntryBuilder.() -> Unit) {
    val builder = TabEntryBuilder(id).apply(block)
    TabListManager.addEntry(this, id, TabEntryDef(
        id = id,
        uuid = UUID.nameUUIDFromBytes("tabentry:$id".toByteArray()),
        displayName = builder.displayName,
        listOrder = builder.listOrder,
        skinValue = builder.skinValue,
        skinSignature = builder.skinSignature,
        ping = builder.ping,
        visibleWhen = builder.visibleWhen,
        contentProvider = builder.contentProvider,
    ))
}

fun Player.updateTabEntry(id: String, displayName: String) =
    TabListManager.updateEntry(this, id, miniMessage.deserialize(displayName))

fun Player.updateTabEntry(id: String, displayName: Component) =
    TabListManager.updateEntry(this, id, displayName)

fun Player.removeTabEntry(id: String) =
    TabListManager.removeEntry(this, id)

fun Player.clearTabEntries() =
    TabListManager.clearEntries(this)

inline fun Player.formatTabEntry(block: PlayerFormatBuilder.() -> Unit) {
    val builder = PlayerFormatBuilder().apply(block)
    TabListManager.formatPlayer(this, PlayerFormatDef(builder.displayName, builder.listOrder))
}
