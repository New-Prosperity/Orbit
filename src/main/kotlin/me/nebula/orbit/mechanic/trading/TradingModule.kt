package me.nebula.orbit.mechanic.trading

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

class TradingModule : OrbitModule("trading") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            val target = event.target
            if (target.entityType != EntityType.VILLAGER && target.entityType != EntityType.WANDERING_TRADER) return@addListener
            val inventory = Inventory(InventoryType.MERCHANT, event.player.translate("orbit.mechanic.trading.title"))
            event.player.openInventory(inventory)
        }
    }
}
