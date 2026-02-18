package me.nebula.orbit.mechanic.itemrepair

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

class ItemRepairModule : OrbitModule("item-repair") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:anvil" &&
                event.block.name() != "minecraft:chipped_anvil" &&
                event.block.name() != "minecraft:damaged_anvil"
            ) return@addListener

            event.player.openInventory(Inventory(InventoryType.ANVIL, event.player.translate("orbit.mechanic.item_repair.title")))
        }
    }
}
