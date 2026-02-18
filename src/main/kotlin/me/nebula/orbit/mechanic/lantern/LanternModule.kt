package me.nebula.orbit.mechanic.lantern

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block

private val LANTERN_BLOCKS = setOf("minecraft:lantern", "minecraft:soul_lantern")

class LanternModule : OrbitModule("lantern") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in LANTERN_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val hanging = event.block.getProperty("hanging") ?: "false"

            if (hanging == "true") {
                val above = instance.getBlock(pos.blockX(), pos.blockY() + 1, pos.blockZ())
                if (above.isAir) {
                    event.isCancelled = true
                }
            } else {
                val below = instance.getBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ())
                if (below.isAir) {
                    event.isCancelled = true
                }
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val above = instance.getBlock(pos.blockX(), pos.blockY() + 1, pos.blockZ())
            if (above.name() in LANTERN_BLOCKS) {
                val hanging = above.getProperty("hanging") ?: "false"
                if (hanging == "false") {
                    instance.setBlock(pos.blockX(), pos.blockY() + 1, pos.blockZ(), Block.AIR)
                }
            }

            val below = instance.getBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ())
            if (below.name() in LANTERN_BLOCKS) {
                val hanging = below.getProperty("hanging") ?: "false"
                if (hanging == "true") {
                    instance.setBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ(), Block.AIR)
                }
            }
        }
    }
}
