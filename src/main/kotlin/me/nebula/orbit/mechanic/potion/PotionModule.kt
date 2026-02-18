package me.nebula.orbit.mechanic.potion

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.event.item.PlayerFinishItemUseEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion

class PotionModule : OrbitModule("potion") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerFinishItemUseEvent::class.java) { event ->
            val item = event.itemStack
            if (item.material() != Material.POTION) return@addListener

            val contents = item.get(DataComponents.POTION_CONTENTS) ?: return@addListener
            contents.customEffects().forEach { effect ->
                event.player.addEffect(Potion(effect.id(), effect.amplifier().toInt(), effect.duration()))
            }
            replaceWithBottle(event.player)
        }
    }

    private fun replaceWithBottle(player: Player) {
        if (player.getItemInMainHand().material() == Material.POTION) {
            player.setItemInMainHand(ItemStack.of(Material.GLASS_BOTTLE))
        } else if (player.getItemInOffHand().material() == Material.POTION) {
            player.setItemInOffHand(ItemStack.of(Material.GLASS_BOTTLE))
        }
    }
}
