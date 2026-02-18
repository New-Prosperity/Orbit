package me.nebula.orbit.mechanic.anvil

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

class AnvilModule : OrbitModule("anvil") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val name = event.block.name()
            if (name != "minecraft:anvil" && name != "minecraft:chipped_anvil" && name != "minecraft:damaged_anvil") return@addListener
            val inventory = Inventory(InventoryType.ANVIL, event.player.translate("orbit.mechanic.anvil.title"))
            event.player.openInventory(inventory)
        }
    }
}
