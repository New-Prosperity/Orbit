package me.nebula.orbit.mechanic.honeyblock

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block

private val STICKY_BLOCKS = setOf("minecraft:honey_block", "minecraft:slime_block")

class HoneyBlockModule : OrbitModule("honey-block") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener

            val belowPos = Vec(player.position.x(), player.position.y() - 0.1, player.position.z())
            val belowBlock = instance.getBlock(belowPos)

            if (belowBlock.name() == "minecraft:honey_block") {
                if (player.velocity.y() < -0.08) {
                    player.velocity = Vec(player.velocity.x(), player.velocity.y() * 0.2, player.velocity.z())
                }
                if (player.velocity.x() != 0.0 || player.velocity.z() != 0.0) {
                    player.velocity = Vec(
                        player.velocity.x() * 0.4,
                        player.velocity.y(),
                        player.velocity.z() * 0.4,
                    )
                }
            }

            if (belowBlock.name() == "minecraft:slime_block") {
                if (player.velocity.y() < 0.0) {
                    player.velocity = Vec(
                        player.velocity.x(),
                        -player.velocity.y() * 0.8,
                        player.velocity.z(),
                    )
                }
            }
        }
    }
}
