package me.nebula.orbit.utils.clickablechat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.entity.Player

private val miniMessage = MiniMessage.miniMessage()

enum class ClickAction {
    RUN_COMMAND,
    SUGGEST_COMMAND,
    OPEN_URL,
    COPY_TO_CLIPBOARD,
}

class ClickableSegmentBuilder @PublishedApi internal constructor(private val text: String) {

    @PublishedApi internal var action: ClickEvent? = null
    @PublishedApi internal var hoverText: Component? = null

    fun action(type: ClickAction, value: String) {
        action = when (type) {
            ClickAction.RUN_COMMAND -> ClickEvent.runCommand(value)
            ClickAction.SUGGEST_COMMAND -> ClickEvent.suggestCommand(value)
            ClickAction.OPEN_URL -> ClickEvent.openUrl(value)
            ClickAction.COPY_TO_CLIPBOARD -> ClickEvent.copyToClipboard(value)
        }
    }

    fun hover(text: String) {
        hoverText = miniMessage.deserialize(text)
    }

    fun hover(component: Component) {
        hoverText = component
    }

    @PublishedApi internal fun build(): Component {
        var component = miniMessage.deserialize(text)
        action?.let { component = component.clickEvent(it) }
        hoverText?.let { component = component.hoverEvent(HoverEvent.showText(it)) }
        return component
    }
}

class ClickableMessageBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val segments = mutableListOf<Component>()

    fun text(text: String) {
        segments.add(miniMessage.deserialize(text))
    }

    fun component(component: Component) {
        segments.add(component)
    }

    inline fun clickText(text: String, block: ClickableSegmentBuilder.() -> Unit) {
        segments.add(ClickableSegmentBuilder(text).apply(block).build())
    }

    fun newLine() {
        segments.add(Component.newline())
    }

    fun space() {
        segments.add(Component.space())
    }

    @PublishedApi internal fun build(): Component {
        if (segments.isEmpty()) return Component.empty()
        var result = segments.first()
        for (i in 1 until segments.size) {
            result = result.append(segments[i])
        }
        return result
    }
}

inline fun clickableMessage(block: ClickableMessageBuilder.() -> Unit): Component =
    ClickableMessageBuilder().apply(block).build()

inline fun clickableMessage(player: Player, block: ClickableMessageBuilder.() -> Unit) {
    player.sendMessage(ClickableMessageBuilder().apply(block).build())
}

inline fun Player.sendClickable(block: ClickableMessageBuilder.() -> Unit) {
    sendMessage(ClickableMessageBuilder().apply(block).build())
}
