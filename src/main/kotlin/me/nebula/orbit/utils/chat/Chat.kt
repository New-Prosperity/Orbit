package me.nebula.orbit.utils.chat

import me.nebula.orbit.utils.hud.font.HudSpriteRegistry
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance

private val HUD_FONT: Key = Key.key("minecraft", "hud")

private val spriteTagResolver = TagResolver.resolver("sprite") { args, _ ->
    val id = args.popOr("sprite tag requires an id").value()
    val sprite = HudSpriteRegistry.getOrNull(id) ?: return@resolver null
    Tag.inserting(Component.text(sprite.columns.first().char.toString()).font(HUD_FONT))
}

val miniMessage: MiniMessage = MiniMessage.builder()
    .tags(TagResolver.builder()
        .resolver(TagResolver.standard())
        .resolver(spriteTagResolver)
        .build())
    .build()

fun mm(text: String, vararg resolvers: TagResolver): Component =
    miniMessage.deserialize(text, *resolvers)

fun mm(text: String, placeholders: Map<String, String>): Component {
    val resolvers = placeholders.map { (key, value) -> Placeholder.parsed(key, value) }
    return miniMessage.deserialize(text, *resolvers.toTypedArray())
}

fun Player.sendMM(text: String, vararg resolvers: TagResolver) {
    sendMessage(miniMessage.deserialize(text, *resolvers))
}

fun Player.sendMM(text: String, placeholders: Map<String, String>) {
    sendMessage(mm(text, placeholders))
}

fun Instance.broadcast(message: Component) {
    players.forEach { it.sendMessage(message) }
}

fun Instance.broadcastMM(text: String, vararg resolvers: TagResolver) {
    val component = miniMessage.deserialize(text, *resolvers)
    players.forEach { it.sendMessage(component) }
}

fun broadcastAll(message: Component) {
    MinecraftServer.getConnectionManager().onlinePlayers.forEach { it.sendMessage(message) }
}

fun broadcastAllMM(text: String, vararg resolvers: TagResolver) {
    val component = miniMessage.deserialize(text, *resolvers)
    MinecraftServer.getConnectionManager().onlinePlayers.forEach { it.sendMessage(component) }
}

fun Player.sendPrefixed(prefix: String, message: String) {
    sendMessage(miniMessage.deserialize("$prefix $message"))
}

class MessageBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val parts = mutableListOf<Component>()

    fun text(text: String) { parts.add(miniMessage.deserialize(text)) }
    fun component(component: Component) { parts.add(component) }
    fun newLine() { parts.add(Component.newline()) }
    fun space() { parts.add(Component.space()) }

    @PublishedApi internal fun build(): Component {
        if (parts.isEmpty()) return Component.empty()
        var result = parts.first()
        for (i in 1 until parts.size) {
            result = result.append(parts[i])
        }
        return result
    }
}

inline fun message(block: MessageBuilder.() -> Unit): Component =
    MessageBuilder().apply(block).build()
