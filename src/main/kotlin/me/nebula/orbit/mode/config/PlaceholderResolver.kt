package me.nebula.orbit.mode.config

import net.minestom.server.entity.Player

private val PLACEHOLDER_REGEX = """\{(\w+)}""".toRegex()

class PlaceholderResolver @PublishedApi internal constructor(
    private val global: Map<String, () -> String>,
    private val perPlayer: Map<String, (Player) -> String>,
) {

    fun resolve(template: String, player: Player? = null): String =
        PLACEHOLDER_REGEX.replace(template) { match ->
            val name = match.groupValues[1]
            perPlayer[name]?.let { provider -> player?.let(provider) }
                ?: global[name]?.invoke()
                ?: match.value
        }

    fun hasPlaceholders(template: String): Boolean =
        PLACEHOLDER_REGEX.containsMatchIn(template)

}

class PlaceholderResolverBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val global = mutableMapOf<String, () -> String>()
    @PublishedApi internal val perPlayer = mutableMapOf<String, (Player) -> String>()

    fun global(name: String, provider: () -> String) {
        global[name] = provider
    }

    fun perPlayer(name: String, provider: (Player) -> String) {
        perPlayer[name] = provider
    }
}

inline fun placeholderResolver(block: PlaceholderResolverBuilder.() -> Unit): PlaceholderResolver {
    val builder = PlaceholderResolverBuilder().apply(block)
    return PlaceholderResolver(builder.global.toMap(), builder.perPlayer.toMap())
}
