package me.nebula.orbit.mechanic.ominousbottle

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.sound.SoundEvent

class OminousBottleModule : OrbitModule("ominous-bottle") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            val item = event.itemStack
            if (item.material() != Material.OMINOUS_BOTTLE) return@addListener

            val player = event.player

            player.addEffect(Potion(PotionEffect.BAD_OMEN, 0, 6000))

            val slot = player.heldSlot.toInt()
            val held = player.inventory.getItemStack(slot)
            if (held.amount() > 1) {
                player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
            } else {
                player.inventory.setItemStack(slot, ItemStack.AIR)
            }

            val pos = player.position
            player.instance?.playSound(
                Sound.sound(SoundEvent.ENTITY_GENERIC_DRINK.key(), Sound.Source.PLAYER, 1f, 1f),
                pos.x(), pos.y(), pos.z(),
            )
        }
    }
}
