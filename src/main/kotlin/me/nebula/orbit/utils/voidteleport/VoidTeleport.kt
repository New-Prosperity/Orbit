package me.nebula.orbit.utils.voidteleport

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

data class VoidTeleportConfig(
    val threshold: Double = -64.0,
    val destination: (Player) -> Pos = { it.respawnPoint },
    val onTeleport: (Player) -> Unit = {},
)

object VoidTeleportManager {

    private var config: VoidTeleportConfig? = null
    private var installed = false

    fun install(newConfig: VoidTeleportConfig = VoidTeleportConfig()) {
        config = newConfig
        if (installed) return
        installed = true

        MinecraftServer.getGlobalEventHandler().addListener(PlayerMoveEvent::class.java) { event ->
            val cfg = config ?: return@addListener
            val player = event.player
            if (player.position.y() < cfg.threshold) {
                val dest = cfg.destination(player)
                player.teleport(dest)
                cfg.onTeleport(player)
            }
        }
    }

    fun uninstall() {
        config = null
    }
}

fun voidTeleport(block: VoidTeleportBuilder.() -> Unit = {}) {
    val config = VoidTeleportBuilder().apply(block).build()
    VoidTeleportManager.install(config)
}

class VoidTeleportBuilder {
    var threshold: Double = -64.0
    private var destination: (Player) -> Pos = { it.respawnPoint }
    private var onTeleport: (Player) -> Unit = {}

    fun destination(block: (Player) -> Pos) { destination = block }
    fun onTeleport(block: (Player) -> Unit) { onTeleport = block }

    fun build(): VoidTeleportConfig = VoidTeleportConfig(threshold, destination, onTeleport)
}
