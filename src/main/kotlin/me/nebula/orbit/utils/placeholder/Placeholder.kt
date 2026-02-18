package me.nebula.orbit.utils.placeholder

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.minestom.server.entity.Player
import java.util.concurrent.ConcurrentHashMap

typealias PlaceholderResolver = (Player) -> String

object PlaceholderRegistry {

    private val placeholders = ConcurrentHashMap<String, PlaceholderResolver>()
    private val miniMessage = MiniMessage.miniMessage()

    fun register(name: String, resolver: PlaceholderResolver) {
        placeholders[name] = resolver
    }

    fun unregister(name: String) = placeholders.remove(name)

    fun resolve(text: String, player: Player): Component {
        val resolvers = placeholders.map { (name, resolver) ->
            Placeholder.parsed(name, resolver(player))
        }
        return miniMessage.deserialize(text, *resolvers.toTypedArray())
    }

    fun resolveString(text: String, player: Player): String {
        var result = text
        placeholders.forEach { (name, resolver) ->
            result = result.replace("<$name>", resolver(player))
        }
        return result
    }

    fun resolverFor(player: Player): TagResolver {
        val resolvers = placeholders.map { (name, resolver) ->
            Placeholder.parsed(name, resolver(player))
        }
        return TagResolver.resolver(resolvers)
    }

    fun all(): Map<String, PlaceholderResolver> = placeholders.toMap()
    fun names(): Set<String> = placeholders.keys.toSet()
    fun clear() = placeholders.clear()
}

fun Player.resolvePlaceholders(text: String): Component =
    PlaceholderRegistry.resolve(text, this)
