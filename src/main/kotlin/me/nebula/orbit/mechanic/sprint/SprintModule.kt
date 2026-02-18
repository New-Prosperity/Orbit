package me.nebula.orbit.mechanic.sprint

import me.nebula.orbit.mechanic.food.addExhaustion
import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerMoveEvent

class SprintModule : OrbitModule("sprint") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            if (!player.isSprinting) return@addListener

            val oldPos = player.position
            val newPos = event.newPosition
            val dx = newPos.x() - oldPos.x()
            val dz = newPos.z() - oldPos.z()
            val horizontalDistance = kotlin.math.sqrt(dx * dx + dz * dz)

            if (horizontalDistance > 0.01) {
                player.addExhaustion(0.1f * horizontalDistance.toFloat())
            }

            if (player.food <= 6) {
                player.isSprinting = false
            }
        }
    }
}
