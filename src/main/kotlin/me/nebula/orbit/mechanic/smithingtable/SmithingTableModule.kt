package me.nebula.orbit.mechanic.smithingtable

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

class SmithingTableModule : OrbitModule("smithing-table") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:smithing_table") return@addListener
            event.player.openInventory(Inventory(InventoryType.SMITHING, event.player.translate("orbit.mechanic.smithing_table.title")))
        }
    }
}
