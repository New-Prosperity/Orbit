package me.nebula.orbit.utils.profilecard

import me.nebula.orbit.utils.chat.miniMessage
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player

data class ProfileLine(
    val icon: String,
    val label: String,
    val valueProvider: (Player) -> String,
)

data class ProfileCard(
    val headerProvider: ((Player) -> String)?,
    val lines: List<ProfileLine>,
    val footerProvider: ((Player) -> String)?,
    val separator: String,
) {

    fun render(target: Player): List<Component> = buildList {
        headerProvider?.let { add(miniMessage.deserialize(it(target))) }
        if (separator.isNotEmpty()) add(miniMessage.deserialize(separator))
        for (line in lines) {
            val value = line.valueProvider(target)
            add(miniMessage.deserialize("<gray>${line.icon} <white>${line.label}: <yellow>$value"))
        }
        if (separator.isNotEmpty()) add(miniMessage.deserialize(separator))
        footerProvider?.let { add(miniMessage.deserialize(it(target))) }
    }

    fun sendTo(viewer: Player, target: Player) {
        render(target).forEach(viewer::sendMessage)
    }
}

class ProfileCardBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var headerProvider: ((Player) -> String)? = null
    @PublishedApi internal var footerProvider: ((Player) -> String)? = null
    @PublishedApi internal var separator: String = "<dark_gray><st>                              "
    @PublishedApi internal val lines: MutableList<ProfileLine> = mutableListOf()

    fun header(provider: (Player) -> String) { headerProvider = provider }
    fun footer(provider: (Player) -> String) { footerProvider = provider }
    fun separator(text: String) { separator = text }

    fun stat(icon: String, label: String, valueProvider: (Player) -> String) {
        lines += ProfileLine(icon, label, valueProvider)
    }

    @PublishedApi internal fun build(): ProfileCard = ProfileCard(
        headerProvider = headerProvider,
        lines = lines.toList(),
        footerProvider = footerProvider,
        separator = separator,
    )
}

inline fun profileCard(block: ProfileCardBuilder.() -> Unit): ProfileCard =
    ProfileCardBuilder().apply(block).build()
