package me.nebula.orbit.utils.motd

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.event.server.ServerListPingEvent
import java.util.concurrent.atomic.AtomicReference

data class MotdConfig(
    val line1: String,
    val line2: String,
    val maxPlayers: Int = 100,
)

object MotdManager {

    private val config = AtomicReference(MotdConfig("<gradient:gold:yellow>Nebula Network</gradient>", "<gray>Play now!"))
    private var installed = false

    fun setConfig(newConfig: MotdConfig) {
        config.set(newConfig)
    }

    fun getConfig(): MotdConfig = config.get()

    fun install() {
        if (installed) return
        installed = true

        MinecraftServer.getGlobalEventHandler().addListener(ServerListPingEvent::class.java) { event ->
            val cfg = config.get()
            val mm = MiniMessage.miniMessage()
            val description = Component.text()
                .append(mm.deserialize(cfg.line1))
                .append(Component.newline())
                .append(mm.deserialize(cfg.line2))
                .build()

            val status = net.minestom.server.ping.Status.builder()
                .description(description)
                .build()
            event.setStatus(status)
        }
    }
}

fun motd(block: MotdConfigBuilder.() -> Unit) {
    val config = MotdConfigBuilder().apply(block).build()
    MotdManager.setConfig(config)
    MotdManager.install()
}

class MotdConfigBuilder {
    var line1: String = ""
    var line2: String = ""
    var maxPlayers: Int = 100

    fun build(): MotdConfig = MotdConfig(line1, line2, maxPlayers)
}
