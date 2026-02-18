package me.nebula.orbit.utils.title

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import net.minestom.server.entity.Player
import java.time.Duration

private val miniMessage = MiniMessage.miniMessage()

class TitleBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var titleText: String = ""
    @PublishedApi internal var subtitleText: String = ""
    @PublishedApi internal var fadeIn: Duration = Duration.ofMillis(500)
    @PublishedApi internal var stay: Duration = Duration.ofSeconds(3)
    @PublishedApi internal var fadeOut: Duration = Duration.ofMillis(500)

    fun title(text: String) { titleText = text }
    fun subtitle(text: String) { subtitleText = text }
    fun fadeIn(ms: Long) { fadeIn = Duration.ofMillis(ms) }
    fun stay(ms: Long) { stay = Duration.ofMillis(ms) }
    fun fadeOut(ms: Long) { fadeOut = Duration.ofMillis(ms) }

    @PublishedApi internal fun build(): Title {
        val title = if (titleText.isNotEmpty()) miniMessage.deserialize(titleText) else Component.empty()
        val subtitle = if (subtitleText.isNotEmpty()) miniMessage.deserialize(subtitleText) else Component.empty()
        return Title.title(title, subtitle, Title.Times.times(fadeIn, stay, fadeOut))
    }
}

inline fun Player.showTitle(block: TitleBuilder.() -> Unit) {
    showTitle(TitleBuilder().apply(block).build())
}

fun Player.showTitle(title: String, subtitle: String = "", fadeInMs: Long = 500, stayMs: Long = 3000, fadeOutMs: Long = 500) {
    val t = if (title.isNotEmpty()) miniMessage.deserialize(title) else Component.empty()
    val s = if (subtitle.isNotEmpty()) miniMessage.deserialize(subtitle) else Component.empty()
    showTitle(Title.title(t, s, Title.Times.times(
        Duration.ofMillis(fadeInMs),
        Duration.ofMillis(stayMs),
        Duration.ofMillis(fadeOutMs),
    )))
}

fun Player.clearTitle() = clearTitle()

fun Player.resetTitle() = resetTitle()
