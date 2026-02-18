package me.nebula.orbit.mechanic.depthstrider

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.tag.Tag

private val WATER_BLOCKS = setOf(
    "minecraft:water", "minecraft:bubble_column",
)

class DepthStriderModule : OrbitModule("depth-strider") {

    private val activeTag = Tag.Boolean("mechanic:depth_strider:active")

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener

            val feetBlock = instance.getBlock(player.position)
            val inWater = feetBlock.name() in WATER_BLOCKS ||
                    feetBlock.getProperty("waterlogged") == "true"

            if (inWater) {
                val vel = player.velocity
                if (vel.x() != 0.0 || vel.z() != 0.0) {
                    player.velocity = Vec(vel.x() * 1.2, vel.y(), vel.z() * 1.2)
                }
                player.setTag(activeTag, true)
            } else {
                player.setTag(activeTag, false)
            }
        }
    }
}
