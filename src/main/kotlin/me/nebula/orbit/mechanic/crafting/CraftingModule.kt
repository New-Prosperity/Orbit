package me.nebula.orbit.mechanic.crafting

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

class CraftingModule : OrbitModule("crafting") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:crafting_table") return@addListener
            val inventory = Inventory(InventoryType.CRAFTING, event.player.translate("orbit.mechanic.crafting.title"))
            event.player.openInventory(inventory)
        }
    }
}
