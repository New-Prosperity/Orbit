package me.nebula.orbit.mechanic.enchanting

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

class EnchantingModule : OrbitModule("enchanting") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:enchanting_table") return@addListener
            val inventory = Inventory(InventoryType.ENCHANTMENT, event.player.translate("orbit.mechanic.enchanting.title"))
            event.player.openInventory(inventory)
        }
    }
}
