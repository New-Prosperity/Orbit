package me.nebula.orbit.mechanic.glowlichen

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.item.Material

class GlowLichenModule : OrbitModule("glow-lichen") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (block.name() != "minecraft:glow_lichen") return@addListener
            if (event.player.getItemInMainHand().material() != Material.SHEARS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val drop = net.minestom.server.entity.ItemEntity(
                net.minestom.server.item.ItemStack.of(Material.GLOW_LICHEN)
            )
            drop.setInstance(instance, net.minestom.server.coordinate.Vec(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5))
            drop.setPickupDelay(java.time.Duration.ofMillis(500))
        }
    }
}
