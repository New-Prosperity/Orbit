package me.nebula.orbit.mechanic.totem

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect

class TotemModule : OrbitModule("totem") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            if (player.health - event.damage.amount > 0) return@addListener

            val mainHand = player.getItemInMainHand()
            val offHand = player.getItemInOffHand()

            val isMainHand = mainHand.material() == Material.TOTEM_OF_UNDYING
            val isOffHand = offHand.material() == Material.TOTEM_OF_UNDYING

            if (!isMainHand && !isOffHand) return@addListener

            event.isCancelled = true

            if (isMainHand) {
                player.setItemInMainHand(ItemStack.AIR)
            } else {
                player.setItemInOffHand(ItemStack.AIR)
            }

            player.health = 1f
            player.clearEffects()
            player.addEffect(Potion(PotionEffect.REGENERATION, 1, 900, Potion.ICON_FLAG))
            player.addEffect(Potion(PotionEffect.ABSORPTION, 1, 100, Potion.ICON_FLAG))
            player.addEffect(Potion(PotionEffect.FIRE_RESISTANCE, 0, 800, Potion.ICON_FLAG))
        }
    }
}
