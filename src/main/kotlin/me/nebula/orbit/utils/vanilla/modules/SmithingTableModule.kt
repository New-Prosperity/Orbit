package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.kyori.adventure.text.Component
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

object SmithingTableModule : VanillaModule {

    override val id = "smithing-table"
    override val description = "Open smithing table interface on interaction"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-smithing-table")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:smithing_table") return@addListener
            val inv = Inventory(InventoryType.SMITHING, Component.text("Upgrade Gear"))
            event.player.openInventory(inv)
        }

        return node
    }
}
