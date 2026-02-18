package me.nebula.orbit.mechanic.bubblecolumn

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block

class BubbleColumnModule : OrbitModule("bubble-column") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val pos = player.position
            val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())

            if (block != Block.BUBBLE_COLUMN) return@addListener

            val below = instance.getBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ())
            val velocity = player.velocity

            when {
                below == Block.SOUL_SAND -> player.velocity = Vec(velocity.x(), velocity.y() + 0.5, velocity.z())
                below == Block.MAGMA_BLOCK -> player.velocity = Vec(velocity.x(), velocity.y() - 0.06, velocity.z())
            }
        }
    }
}
