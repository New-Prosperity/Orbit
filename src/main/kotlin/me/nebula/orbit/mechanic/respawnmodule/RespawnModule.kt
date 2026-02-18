package me.nebula.orbit.mechanic.respawnmodule

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.PlayerRespawnEvent

class RespawnModule : OrbitModule("respawn-module") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerRespawnEvent::class.java) { event ->
            val player = event.player

            val respawnPos = player.respawnPoint ?: Pos(0.0, 64.0, 0.0)
            event.respawnPosition = respawnPos

            player.inventory.clear()
            player.heal()
            player.food = 20
            player.foodSaturation = 5f

            player.clearEffects()
        }
    }
}
