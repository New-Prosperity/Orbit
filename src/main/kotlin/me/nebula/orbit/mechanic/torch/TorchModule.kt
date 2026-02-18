package me.nebula.orbit.mechanic.torch

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block

private val TORCH_BLOCKS = setOf(
    "minecraft:torch", "minecraft:wall_torch",
    "minecraft:soul_torch", "minecraft:soul_wall_torch",
    "minecraft:redstone_torch", "minecraft:redstone_wall_torch",
)

class TorchModule : OrbitModule("torch") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in TORCH_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val below = instance.getBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ())

            val isWallTorch = event.block.name().contains("wall")
            if (!isWallTorch && below.isAir) {
                event.isCancelled = true
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val above = instance.getBlock(pos.blockX(), pos.blockY() + 1, pos.blockZ())
            if (above.name() in TORCH_BLOCKS && !above.name().contains("wall")) {
                instance.setBlock(pos.blockX(), pos.blockY() + 1, pos.blockZ(), Block.AIR)
            }
        }
    }
}
