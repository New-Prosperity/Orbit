package me.nebula.orbit.mechanic.falldamage

import me.nebula.orbit.mechanic.food.addExhaustion
import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.tag.Tag

private val FALL_START_Y_TAG = Tag.Double("mechanic:fall:start_y")

class FallDamageModule : OrbitModule("fall-damage") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val newPos = event.newPosition
            val onGround = event.isOnGround

            if (!onGround) {
                val tracked = player.getTag(FALL_START_Y_TAG)
                val currentY = newPos.y()
                if (tracked == null || currentY > tracked) {
                    player.setTag(FALL_START_Y_TAG, currentY)
                }
            } else if (player.hasTag(FALL_START_Y_TAG)) {
                val startY = player.getTag(FALL_START_Y_TAG)!!
                player.removeTag(FALL_START_Y_TAG)
                val fallDistance = startY - newPos.y() - 3.0
                if (fallDistance > 0) {
                    player.damage(DamageType.FALL, fallDistance.toFloat())
                    player.addExhaustion(0.2f)
                }
            }
        }
    }
}
