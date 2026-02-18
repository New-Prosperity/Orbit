package me.nebula.orbit.mechanic.infinity

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.timer.TaskSchedule

class InfinityModule : OrbitModule("infinity") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerCancelItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.BOW) return@addListener

            val item = event.itemStack
            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener
            val level = enchantments.level(Enchantment.INFINITY)
            if (level <= 0) return@addListener

            val player = event.player

            player.scheduler().buildTask {
                val hasArrow = (0 until player.inventory.size).any {
                    player.inventory.getItemStack(it).material() == Material.ARROW
                }
                if (!hasArrow) {
                    for (slot in 0 until player.inventory.size) {
                        if (player.inventory.getItemStack(slot).isAir) {
                            player.inventory.setItemStack(slot, ItemStack.of(Material.ARROW))
                            break
                        }
                    }
                }
            }.delay(TaskSchedule.tick(1)).schedule()
        }
    }
}
