package me.nebula.orbit.mechanic.dragonbreath

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

class DragonBreathModule : OrbitModule("dragon-breath") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.itemStack.material() != Material.GLASS_BOTTLE) return@addListener

            val player = event.player
            val instance = player.instance ?: return@addListener
            val eyePos = player.position.add(0.0, player.eyeHeight, 0.0)

            val block = instance.getBlock(eyePos)
            if (block.name() != "minecraft:dragon_breath") return@addListener

            val slot = player.heldSlot.toInt()
            val held = player.getItemInMainHand()
            if (held.amount() > 1) {
                player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
            } else {
                player.inventory.setItemStack(slot, ItemStack.AIR)
            }
            player.inventory.addItemStack(ItemStack.of(Material.DRAGON_BREATH))
        }
    }
}
