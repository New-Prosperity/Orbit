package me.nebula.orbit.mechanic.tripwire

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerMoveEvent

class TripwireModule : OrbitModule("tripwire") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val block = instance.getBlock(player.position)

            if (block.name() != "minecraft:tripwire") return@addListener

            val powered = block.getProperty("powered") == "true"
            if (powered) return@addListener

            instance.setBlock(player.position, block.withProperty("powered", "true"))

            val hookPositions = listOf(
                player.position.add(1.0, 0.0, 0.0),
                player.position.add(-1.0, 0.0, 0.0),
                player.position.add(0.0, 0.0, 1.0),
                player.position.add(0.0, 0.0, -1.0),
            )

            for (pos in hookPositions) {
                val hookBlock = instance.getBlock(pos)
                if (hookBlock.name() == "minecraft:tripwire_hook") {
                    instance.setBlock(pos, hookBlock.withProperty("powered", "true"))
                }
            }
        }
    }
}
