package me.nebula.orbit.mechanic.grindstone

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

class GrindstoneModule : OrbitModule("grindstone") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:grindstone") return@addListener
            val inventory = Inventory(InventoryType.GRINDSTONE, event.player.translate("orbit.mechanic.grindstone.title"))
            event.player.openInventory(inventory)
        }
    }
}
