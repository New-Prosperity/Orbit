package me.nebula.orbit.mechanic.cobweb

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block

class CobwebModule : OrbitModule("cobweb") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val pos = player.position
            val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())

            if (block != Block.COBWEB) return@addListener

            player.velocity = player.velocity.mul(0.05)
        }
    }
}
