package me.nebula.orbit.mechanic.tintedglass

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

class TintedGlassModule : OrbitModule("tinted-glass") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:tinted_glass") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val drop = ItemEntity(ItemStack.of(Material.TINTED_GLASS))
            drop.setInstance(instance, net.minestom.server.coordinate.Vec(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5))
            drop.setPickupDelay(java.time.Duration.ofMillis(500))
        }
    }
}
