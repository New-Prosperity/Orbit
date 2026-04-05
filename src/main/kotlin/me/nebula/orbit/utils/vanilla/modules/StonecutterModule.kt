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

object StonecutterModule : VanillaModule {

    override val id = "stonecutter"
    override val description = "Open stonecutter interface on interaction"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-stonecutter")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:stonecutter") return@addListener
            val inv = Inventory(InventoryType.STONE_CUTTER, Component.text("Stonecutter"))
            event.player.openInventory(inv)
        }

        return node
    }
}
