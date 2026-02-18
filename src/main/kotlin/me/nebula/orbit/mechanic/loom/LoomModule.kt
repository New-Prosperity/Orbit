package me.nebula.orbit.mechanic.loom

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

class LoomModule : OrbitModule("loom") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:loom") return@addListener
            event.player.openInventory(Inventory(InventoryType.LOOM, event.player.translate("orbit.mechanic.loom.title")))
        }
    }
}
