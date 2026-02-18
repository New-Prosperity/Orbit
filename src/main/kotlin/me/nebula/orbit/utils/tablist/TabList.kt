package me.nebula.orbit.utils.tablist

import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.entity.Player

private val miniMessage = MiniMessage.miniMessage()

class TabListBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var headerText: String = ""
    @PublishedApi internal var footerText: String = ""

    fun header(text: String) { headerText = text }
    fun footer(text: String) { footerText = text }
}

fun Player.tabList(block: TabListBuilder.() -> Unit) {
    val builder = TabListBuilder().apply(block)
    sendPlayerListHeaderAndFooter(
        miniMessage.deserialize(builder.headerText),
        miniMessage.deserialize(builder.footerText),
    )
}

fun Player.setTabList(header: String, footer: String) {
    sendPlayerListHeaderAndFooter(
        miniMessage.deserialize(header),
        miniMessage.deserialize(footer),
    )
}
