package me.nebula.orbit.mechanic.endrod

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockPlaceEvent

class EndRodModule : OrbitModule("end-rod") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:end_rod") return@addListener

            val facing = event.block.getProperty("facing") ?: "up"
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val neighborBlock = when (facing) {
                "up" -> instance.getBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ())
                "down" -> instance.getBlock(pos.blockX(), pos.blockY() + 1, pos.blockZ())
                "north" -> instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ() + 1)
                "south" -> instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ() - 1)
                "east" -> instance.getBlock(pos.blockX() - 1, pos.blockY(), pos.blockZ())
                "west" -> instance.getBlock(pos.blockX() + 1, pos.blockY(), pos.blockZ())
                else -> return@addListener
            }

            if (neighborBlock.name() == "minecraft:end_rod") {
                val neighborFacing = neighborBlock.getProperty("facing")
                if (neighborFacing == facing) {
                    val opposite = when (facing) {
                        "up" -> "down"; "down" -> "up"
                        "north" -> "south"; "south" -> "north"
                        "east" -> "west"; "west" -> "east"
                        else -> facing
                    }
                    instance.setBlock(pos, event.block.withProperty("facing", opposite))
                    event.isCancelled = true
                    instance.setBlock(pos, event.block.withProperty("facing", opposite))
                }
            }
        }
    }
}
