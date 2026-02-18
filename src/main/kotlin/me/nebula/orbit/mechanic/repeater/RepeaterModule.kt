package me.nebula.orbit.mechanic.repeater

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockInteractEvent

class RepeaterModule : OrbitModule("repeater") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            when (block.name()) {
                "minecraft:repeater" -> {
                    val delay = block.getProperty("delay")?.toIntOrNull() ?: 1
                    val newDelay = if (delay >= 4) 1 else delay + 1
                    instance.setBlock(pos, block.withProperty("delay", newDelay.toString()))
                }
                "minecraft:comparator" -> {
                    val mode = block.getProperty("mode") ?: "compare"
                    val newMode = if (mode == "compare") "subtract" else "compare"
                    instance.setBlock(pos, block.withProperty("mode", newMode))
                }
            }
        }
    }
}
