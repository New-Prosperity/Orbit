package me.nebula.orbit.utils.nametag

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class NameTagConfig(
    val prefix: Component = Component.empty(),
    val suffix: Component = Component.empty(),
    val displayName: Component? = null,
)

object NameTagManager {

    private val configs = ConcurrentHashMap<UUID, NameTagConfig>()

    fun set(player: Player, config: NameTagConfig) {
        configs[player.uuid] = config
        apply(player)
    }

    fun get(player: Player): NameTagConfig? = configs[player.uuid]

    fun clear(player: Player) {
        configs.remove(player.uuid)
        player.customName = null
        player.isCustomNameVisible = false
    }

    fun apply(player: Player) {
        val config = configs[player.uuid] ?: return
        val name = config.displayName ?: Component.text(player.username)
        val fullName = Component.text()
            .append(config.prefix)
            .append(name)
            .append(config.suffix)
            .build()
        player.customName = fullName
        player.isCustomNameVisible = true
    }
}

fun Player.setNameTag(block: NameTagConfigBuilder.() -> Unit) {
    val config = NameTagConfigBuilder().apply(block).build()
    NameTagManager.set(this, config)
}

fun Player.clearNameTag() = NameTagManager.clear(this)

class NameTagConfigBuilder {
    private var prefix: Component = Component.empty()
    private var suffix: Component = Component.empty()
    private var displayName: Component? = null

    fun prefix(text: String) {
        prefix = MiniMessage.miniMessage().deserialize(text)
    }

    fun prefix(component: Component) {
        prefix = component
    }

    fun suffix(text: String) {
        suffix = MiniMessage.miniMessage().deserialize(text)
    }

    fun suffix(component: Component) {
        suffix = component
    }

    fun displayName(text: String) {
        displayName = MiniMessage.miniMessage().deserialize(text)
    }

    fun displayName(component: Component) {
        displayName = component
    }

    fun build(): NameTagConfig = NameTagConfig(prefix, suffix, displayName)
}
