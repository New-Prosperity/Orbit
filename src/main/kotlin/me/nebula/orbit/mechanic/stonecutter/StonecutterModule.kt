package me.nebula.orbit.mechanic.stonecutter

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

class StonecutterModule : OrbitModule("stonecutter") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:stonecutter") return@addListener
            event.player.openInventory(Inventory(InventoryType.STONE_CUTTER, event.player.translate("orbit.mechanic.stonecutter.title")))
        }
    }
}
