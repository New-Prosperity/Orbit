package me.nebula.orbit.utils.chat

import me.nebula.orbit.utils.hud.font.HudSpriteRegistry
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.`object`.ObjectContents
import net.minestom.server.entity.Player

private val HUD_FONT: Key = Key.key("minecraft", "hud")

private val spriteTagResolver = TagResolver.resolver("sprite") { args, _ ->
    val id = args.popOr("sprite tag requires an id").value()
    val sprite = HudSpriteRegistry.getOrNull(id) ?: return@resolver null
    Tag.inserting(Component.text(sprite.columns.first().char.toString()).font(HUD_FONT))
}

private val vanillaSpriteTagResolver = TagResolver.resolver("vsprite") { args, _ ->
    val id = args.popOr("vsprite tag requires a sprite id").value()
    val atlas = if (args.hasNext()) Key.key(args.pop().value()) else Key.key("minecraft", "sprite")
    Tag.inserting(Component.`object`(ObjectContents.sprite(atlas, Key.key(id))))
}

val miniMessage: MiniMessage = MiniMessage.builder()
    .tags(TagResolver.builder()
        .resolver(TagResolver.standard())
        .resolver(spriteTagResolver)
        .resolver(vanillaSpriteTagResolver)
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
