package me.nebula.orbit.mechanic.lilypad

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

class LilyPadModule : OrbitModule("lily-pad") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:lily_pad") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val below = instance.getBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ())
            if (below.name() != "minecraft:water") {
                event.isCancelled = true
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:lily_pad") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val drop = net.minestom.server.entity.ItemEntity(ItemStack.of(Material.LILY_PAD))
            drop.setInstance(instance, net.minestom.server.coordinate.Vec(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5))
            drop.setPickupDelay(java.time.Duration.ofMillis(500))
        }
    }
}
