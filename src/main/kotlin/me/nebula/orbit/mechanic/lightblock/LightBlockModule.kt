package me.nebula.orbit.mechanic.lightblock

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockInteractEvent

class LightBlockModule : OrbitModule("light-block") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:light") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val block = event.block

            val currentLevel = block.getProperty("level")?.toIntOrNull() ?: 15
            val newLevel = (currentLevel + 1) % 16

            val updatedBlock = block.withProperty("level", newLevel.toString())
            instance.setBlock(pos, updatedBlock)
        }
    }
}
