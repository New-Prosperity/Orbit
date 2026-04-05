package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.kyori.adventure.text.Component
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.instance.Instance
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EnderChestModule : VanillaModule {

    override val id = "ender-chest"
    override val description = "Per-player ender chest inventory persisted across interactions"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val enderChests = ConcurrentHashMap<UUID, Inventory>()

        val node = EventNode.all("vanilla-ender-chest")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:ender_chest") return@addListener
            val inv = enderChests.getOrPut(event.player.uuid) {
                Inventory(InventoryType.CHEST_3_ROW, Component.text("Ender Chest"))
            }
            event.player.openInventory(inv)
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            enderChests.remove(event.player.uuid)
        }

        return node
    }
}
