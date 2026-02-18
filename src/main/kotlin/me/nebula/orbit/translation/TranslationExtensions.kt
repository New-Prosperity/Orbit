package me.nebula.orbit.translation

import me.nebula.orbit.Orbit
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.minestom.server.entity.Player
import java.util.UUID

fun Player.translate(key: String, vararg args: Pair<String, String>): Component {
    val locale = Orbit.localeOf(uuid)
    val resolvers = args.map { (k, v) -> Placeholder.unparsed(k, v) }.toTypedArray<TagResolver>()
    return Orbit.deserialize(key, locale, *resolvers)
}

fun Player.translateRaw(key: String, vararg args: Pair<String, String>): String {
    val locale = Orbit.localeOf(uuid)
    if (args.isEmpty()) return Orbit.translations.require(key, locale)
    return Orbit.translations.requireFormat(key, locale, *args)
}

fun translateDefault(key: String, vararg args: Pair<String, String>): Component {
    val locale = Orbit.translations.defaultLocale
    val resolvers = args.map { (k, v) -> Placeholder.unparsed(k, v) }.toTypedArray<TagResolver>()
    return Orbit.deserialize(key, locale, *resolvers)
}

fun translateFor(uuid: UUID, key: String, vararg args: Pair<String, String>): Component {
    val locale = Orbit.localeOf(uuid)
    val resolvers = args.map { (k, v) -> Placeholder.unparsed(k, v) }.toTypedArray<TagResolver>()
    return Orbit.deserialize(key, locale, *resolvers)
}
