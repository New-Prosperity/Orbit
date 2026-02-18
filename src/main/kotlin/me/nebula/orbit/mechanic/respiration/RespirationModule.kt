package me.nebula.orbit.mechanic.respiration

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.tag.Tag

private val WATER_BLOCKS = setOf("minecraft:water", "minecraft:bubble_column")

class RespirationModule : OrbitModule("respiration") {

    private val breathTag = Tag.Integer("mechanic:respiration:breath").defaultValue(300)

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener

            val headPos = Vec(player.position.x(), player.position.y() + 1.62, player.position.z())
            val headBlock = instance.getBlock(headPos)
            val inWater = headBlock.name() in WATER_BLOCKS || headBlock.getProperty("waterlogged") == "true"

            val breath = player.getTag(breathTag)

            if (inWater) {
                val newBreath = (breath - 1).coerceAtLeast(0)
                player.setTag(breathTag, newBreath)
            } else {
                if (breath < 300) {
                    player.setTag(breathTag, (breath + 5).coerceAtMost(300))
                }
            }
        }
    }
}
