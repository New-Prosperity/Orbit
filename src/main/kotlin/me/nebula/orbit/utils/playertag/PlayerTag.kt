package me.nebula.orbit.utils.playertag

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.TeamsPacket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet

private val miniMessage = MiniMessage.miniMessage()

data class PlayerTag(
    val id: String,
    val prefix: Component = Component.empty(),
    val suffix: Component = Component.empty(),
    val nameColor: NamedTextColor = NamedTextColor.WHITE,
    val priority: Int = 0,
)

object PlayerTagManager {

    private val playerTags = ConcurrentHashMap<UUID, ConcurrentSkipListSet<PlayerTag>>()

    fun addTag(player: Player, tag: PlayerTag) {
        playerTags.getOrPut(player.uuid) {
            ConcurrentSkipListSet(compareByDescending<PlayerTag> { it.priority }.thenBy { it.id })
        }.add(tag)
        applyTopTag(player)
    }

    fun removeTag(player: Player, tagId: String) {
        playerTags[player.uuid]?.removeIf { it.id == tagId }
        applyTopTag(player)
    }

    fun clearTags(player: Player) {
        playerTags.remove(player.uuid)
        player.customName = null
        player.isCustomNameVisible = false
        player.displayName = null
    }

    fun getActiveTag(player: Player): PlayerTag? =
        playerTags[player.uuid]?.firstOrNull()

    fun getAllTags(player: Player): List<PlayerTag> =
        playerTags[player.uuid]?.toList() ?: emptyList()

    fun applyTopTag(player: Player) {
        val tag = getActiveTag(player)
        if (tag == null) {
            player.customName = null
            player.isCustomNameVisible = false
            player.displayName = Component.text(player.username)
            return
        }
        val fullName = Component.text()
            .append(tag.prefix)
            .append(Component.text(player.username, tag.nameColor))
            .append(tag.suffix)
            .build()
        player.customName = fullName
        player.isCustomNameVisible = true
        player.displayName = fullName
    }

    fun cleanup(uuid: UUID) {
        playerTags.remove(uuid)
    }
}

class PlayerTagBuilder @PublishedApi internal constructor(
    private val player: Player,
) {
    @PublishedApi internal var id: String = "default"
    @PublishedApi internal var prefix: Component = Component.empty()
    @PublishedApi internal var suffix: Component = Component.empty()
    @PublishedApi internal var nameColor: NamedTextColor = NamedTextColor.WHITE
    @PublishedApi internal var priority: Int = 0

    fun id(id: String) { this.id = id }
    fun prefix(text: String) { prefix = miniMessage.deserialize(text) }
    fun prefix(component: Component) { prefix = component }
    fun suffix(text: String) { suffix = miniMessage.deserialize(text) }
    fun suffix(component: Component) { suffix = component }
    fun nameColor(color: NamedTextColor) { nameColor = color }
    fun priority(priority: Int) { this.priority = priority }

    @PublishedApi internal fun apply() {
        val tag = PlayerTag(id, prefix, suffix, nameColor, priority)
        PlayerTagManager.addTag(player, tag)
    }
}

inline fun playerTag(player: Player, block: PlayerTagBuilder.() -> Unit) {
    PlayerTagBuilder(player).apply(block).apply()
}

fun Player.addPlayerTag(block: PlayerTagBuilder.() -> Unit) {
    PlayerTagBuilder(this).apply(block).apply()
}

fun Player.removePlayerTag(tagId: String) {
    PlayerTagManager.removeTag(this, tagId)
}

fun Player.clearPlayerTags() {
    PlayerTagManager.clearTags(this)
}
