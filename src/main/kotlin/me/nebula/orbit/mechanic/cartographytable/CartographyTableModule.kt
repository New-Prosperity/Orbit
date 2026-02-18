package me.nebula.orbit.mechanic.cartographytable

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

class CartographyTableModule : OrbitModule("cartography-table") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:cartography_table") return@addListener
            event.player.openInventory(Inventory(InventoryType.CARTOGRAPHY, event.player.translate("orbit.mechanic.cartography_table.title")))
        }
    }
}
