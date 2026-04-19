package me.nebula.orbit.translation

import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.user.NebulaUser
import me.nebula.orbit.Orbit
import me.nebula.orbit.localeCode
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.mode.config.PlaceholderResolver
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player

private fun applyPlaceholders(template: String, args: Array<out Pair<String, String>>): String {
    var result = template
    for ((name, value) in args) {
        result = result.replace("{$name}", value)
    }
    return result
}

fun Player.translate(key: TranslationKey, vararg args: Pair<String, String>): Component {
    val locale = localeCode
    val template = applyPlaceholders(Orbit.translations.require(key.value, locale), args)
    return miniMessage.deserialize(template)
}

fun Player.translateRaw(key: TranslationKey, vararg args: Pair<String, String>): String {
    val locale = localeCode
    if (args.isEmpty()) return Orbit.translations.require(key.value, locale)
    return applyPlaceholders(Orbit.translations.require(key.value, locale), args)
}

fun translateDefault(key: TranslationKey, vararg args: Pair<String, String>): Component {
    val locale = Orbit.translations.defaultLocale
    val template = applyPlaceholders(Orbit.translations.require(key.value, locale), args)
    return miniMessage.deserialize(template)
}

@Deprecated(
    "Prefer the TranslationKey overload — use generated Keys.* (e.g. Keys.Orbit.Command.Heal.Self) or wrap with .asTranslationKey().",
    ReplaceWith("translate(key.asTranslationKey(), *args)", "me.nebula.ether.utils.translation.asTranslationKey"),
)
fun Player.translate(key: String, vararg args: Pair<String, String>): Component =
    translate(key.asTranslationKey(), *args)

@Deprecated(
    "Prefer the TranslationKey overload — use generated Keys.* or wrap with .asTranslationKey().",
    ReplaceWith("translateRaw(key.asTranslationKey(), *args)", "me.nebula.ether.utils.translation.asTranslationKey"),
)
fun Player.translateRaw(key: String, vararg args: Pair<String, String>): String =
    translateRaw(key.asTranslationKey(), *args)

@Deprecated(
    "Prefer the TranslationKey overload — use generated Keys.* or wrap with .asTranslationKey().",
    ReplaceWith("translateDefault(key.asTranslationKey(), *args)", "me.nebula.ether.utils.translation.asTranslationKey"),
)
fun translateDefault(key: String, vararg args: Pair<String, String>): Component =
    translateDefault(key.asTranslationKey(), *args)

fun PlaceholderResolver.resolveTranslated(key: String, player: Player): String {
    val locale = player.localeCode
    val template = Orbit.translations.get(key, locale) ?: key
    return resolve(template, player)
}

fun NebulaUser.Online.translate(
    key: TranslationKey,
    vararg args: Pair<String, String>,
): Component {
    val localeTag = locale.toLanguageTag()
    val template = Orbit.translations.require(key.value, localeTag)
    val resolved = if (args.isEmpty()) template else {
        var result = template
        for ((name, value) in args) result = result.replace("{$name}", value)
        result
    }
    return miniMessage.deserialize(resolved)
}

fun NebulaUser.Online.translateRaw(
    key: TranslationKey,
    vararg args: Pair<String, String>,
): String {
    val localeTag = locale.toLanguageTag()
    val template = Orbit.translations.require(key.value, localeTag)
    if (args.isEmpty()) return template
    var result = template
    for ((name, value) in args) result = result.replace("{$name}", value)
    return result
}
