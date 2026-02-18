package me.nebula.orbit.mechanic.fletchingtable

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

class FletchingTableModule : OrbitModule("fletching-table") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:fletching_table") return@addListener

            event.player.openInventory(
                Inventory(InventoryType.CRAFTING, event.player.translate("orbit.mechanic.fletching_table.title"))
            )
        }
    }
}
