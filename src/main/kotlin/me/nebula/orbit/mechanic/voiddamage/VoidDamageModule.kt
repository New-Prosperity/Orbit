package me.nebula.orbit.mechanic.voiddamage

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.player.PlayerMoveEvent

class VoidDamageModule : OrbitModule("void-damage") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if (event.newPosition.y() < -64) {
                event.player.damage(DamageType.OUT_OF_WORLD, Float.MAX_VALUE)
            }
        }
    }
}
