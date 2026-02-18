package me.nebula.orbit.mechanic.hangingblock

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block

private val HANGING_BLOCKS = setOf(
    "minecraft:mangrove_propagule",
    "minecraft:spore_blossom",
    "minecraft:hanging_roots",
    "minecraft:cave_vines",
    "minecraft:cave_vines_plant",
    "minecraft:weeping_vines",
    "minecraft:weeping_vines_plant",
)

class HangingBlockModule : OrbitModule("hanging-block") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in HANGING_BLOCKS) return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val above = instance.getBlock(pos.add(0, 1, 0))
            if (above.isAir) {
                event.isCancelled = true
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val below = instance.getBlock(pos.add(0, -1, 0))
            if (below.name() in HANGING_BLOCKS) {
                instance.setBlock(pos.add(0, -1, 0), Block.AIR)
                cascadeBreak(instance, pos.blockX(), pos.blockY() - 2, pos.blockZ())
            }
        }
    }

    private fun cascadeBreak(instance: net.minestom.server.instance.Instance, x: Int, y: Int, z: Int) {
        val block = instance.getBlock(x, y, z)
        if (block.name() !in HANGING_BLOCKS) return
        instance.setBlock(x, y, z, Block.AIR)
        cascadeBreak(instance, x, y - 1, z)
    }
}
