package me.nebula.orbit.mechanic.chain

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockPlaceEvent

class ChainModule : OrbitModule("chain") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:chain") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val above = instance.getBlock(pos.blockX(), pos.blockY() + 1, pos.blockZ())
            val below = instance.getBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ())

            val axis = event.block.getProperty("axis") ?: "y"

            if (above.name() == "minecraft:chain") {
                val aboveAxis = above.getProperty("axis") ?: "y"
                if (aboveAxis != axis) {
                    instance.setBlock(pos, event.block.withProperty("axis", aboveAxis))
                    event.isCancelled = true
                    instance.setBlock(pos, event.block.withProperty("axis", aboveAxis))
                }
            }
        }
    }
}
