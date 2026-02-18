package me.nebula.orbit.mechanic.map

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material

class MapModule : OrbitModule("map") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:cartography_table") return@addListener

            val held = event.player.getItemInMainHand()
            if (held.material() != Material.MAP && held.material() != Material.FILLED_MAP) return@addListener

            event.player.openInventory(Inventory(InventoryType.CARTOGRAPHY, event.player.translate("orbit.mechanic.map.title")))
        }
    }
}
