package me.nebula.orbit.mechanic.soulspeed

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.tag.Tag

private val SOUL_BLOCKS = setOf("minecraft:soul_sand", "minecraft:soul_soil")

class SoulSpeedModule : OrbitModule("soul-speed") {

    private val activeTag = Tag.Boolean("mechanic:soul_speed:active")

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener

            val belowPos = Vec(player.position.x(), player.position.y() - 0.1, player.position.z())
            val block = instance.getBlock(belowPos)
            val onSoulBlock = block.name() in SOUL_BLOCKS

            if (onSoulBlock) {
                val vel = player.velocity
                if (vel.x() != 0.0 || vel.z() != 0.0) {
                    player.velocity = Vec(vel.x() * 1.3, vel.y(), vel.z() * 1.3)
                }
                player.setTag(activeTag, true)
            } else {
                player.setTag(activeTag, false)
            }
        }
    }
}
