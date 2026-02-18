package me.nebula.orbit.mechanic.sealantern

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import kotlin.random.Random

class SeaLanternModule : OrbitModule("sea-lantern") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:sea_lantern") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val count = Random.nextInt(2, 4)

            val drop = ItemEntity(ItemStack.of(Material.PRISMARINE_CRYSTALS, count))
            drop.setInstance(instance, net.minestom.server.coordinate.Vec(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5))
            drop.setPickupDelay(java.time.Duration.ofMillis(500))
        }
    }
}
