package me.nebula.orbit.translation

import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.orbit.Orbit
import me.nebula.orbit.localeCode
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.mode.config.PlaceholderResolver
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import java.util.UUID

private fun applyPlaceholders(template: String, args: Array<out Pair<String, String>>): String {
    var result = template
    for ((name, value) in args) {
        result = result.replace("{$name}", value)
    }
    return result
}

fun Player.translate(key: String, vararg args: Pair<String, String>): Component {
    val locale = localeCode
    val template = applyPlaceholders(Orbit.translations.require(key, locale), args)
    return miniMessage.deserialize(template)
}

fun Player.translateRaw(key: String, vararg args: Pair<String, String>): String {
    val locale = localeCode
    if (args.isEmpty()) return Orbit.translations.require(key, locale)
    return applyPlaceholders(Orbit.translations.require(key, locale), args)
}

fun translateDefault(key: String, vararg args: Pair<String, String>): Component {
    val locale = Orbit.translations.defaultLocale
    val template = applyPlaceholders(Orbit.translations.require(key, locale), args)
    return miniMessage.deserialize(template)
}

fun translateFor(uuid: UUID, key: String, vararg args: Pair<String, String>): Component {
    val locale = uuid.localeCode
    val template = applyPlaceholders(Orbit.translations.require(key, locale), args)
    return miniMessage.deserialize(template)
}

fun PlaceholderResolver.resolveTranslated(key: String, player: Player): String {
    val locale = player.localeCode
    val template = Orbit.translations.get(key, locale) ?: key
    return resolve(template, player)
}

fun Player.translate(key: TranslationKey, vararg args: Pair<String, String>): Component =
    translate(key.value, *args)

fun Player.translateRaw(key: TranslationKey, vararg args: Pair<String, String>): String =
    translateRaw(key.value, *args)

fun translateDefault(key: TranslationKey, vararg args: Pair<String, String>): Component =
    translateDefault(key.value, *args)

fun translateFor(uuid: UUID, key: TranslationKey, vararg args: Pair<String, String>): Component =
    translateFor(uuid, key.value, *args)
