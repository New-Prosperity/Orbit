package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.item.PickupItemEvent
import net.minestom.server.instance.Instance

object ItemPickupModule : VanillaModule {

    override val id = "item-pickup"
    override val description = "Pick up dropped item entities and XP orbs on proximity"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-item-pickup")

        node.addListener(PickupItemEvent::class.java) { event ->
            val player = event.livingEntity as? Player ?: return@addListener
            if (player.gameMode == GameMode.SPECTATOR) {
                event.isCancelled = true
                return@addListener
            }
            val itemStack = event.itemEntity.itemStack
            val canFit = player.inventory.addItemStack(itemStack)
            if (!canFit) {
                event.isCancelled = true
            }
        }

        node.addListener(ItemDropEvent::class.java) { event ->
            if (event.player.gameMode == GameMode.SPECTATOR) {
                event.isCancelled = true
            }
        }

        return node
    }
}
