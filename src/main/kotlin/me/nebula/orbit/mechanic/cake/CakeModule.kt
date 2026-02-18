package me.nebula.orbit.mechanic.cake

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block

class CakeModule : OrbitModule("cake") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (block.name() != "minecraft:cake") return@addListener

            val player = event.player
            if (player.food >= 20) return@addListener

            val instance = player.instance ?: return@addListener
            val pos = event.blockPosition
            val bites = block.getProperty("bites")?.toIntOrNull() ?: 0

            player.food = (player.food + 2).coerceAtMost(20)
            player.foodSaturation = (player.foodSaturation + 0.4f).coerceAtMost(player.food.toFloat())

            if (bites >= 6) {
                instance.setBlock(pos, Block.AIR)
            } else {
                instance.setBlock(pos, block.withProperty("bites", (bites + 1).toString()))
            }
        }
    }
}
