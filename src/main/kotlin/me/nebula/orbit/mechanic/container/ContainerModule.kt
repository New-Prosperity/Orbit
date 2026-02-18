package me.nebula.orbit.mechanic.container

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.text.Component
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import java.util.concurrent.ConcurrentHashMap

private data class ContainerKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class ContainerModule : OrbitModule("container") {

    private val inventories = ConcurrentHashMap<ContainerKey, Inventory>()

    private val containerTypes = mapOf(
        "minecraft:chest" to InventoryType.CHEST_3_ROW,
        "minecraft:trapped_chest" to InventoryType.CHEST_3_ROW,
        "minecraft:barrel" to InventoryType.CHEST_3_ROW,
        "minecraft:hopper" to InventoryType.HOPPER,
        "minecraft:dispenser" to InventoryType.CHEST_1_ROW,
        "minecraft:dropper" to InventoryType.CHEST_1_ROW,
        "minecraft:furnace" to InventoryType.FURNACE,
        "minecraft:blast_furnace" to InventoryType.BLAST_FURNACE,
        "minecraft:smoker" to InventoryType.SMOKER,
        "minecraft:brewing_stand" to InventoryType.BREWING_STAND,
    )

    override fun onEnable() {
        super.onEnable()
        inventories.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val type = containerTypes[event.block.name()] ?: return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = ContainerKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            val title = event.block.name().substringAfter("minecraft:").replace('_', ' ')
                .replaceFirstChar { it.uppercase() }

            val inventory = inventories.computeIfAbsent(key) {
                Inventory(type, Component.text(title))
            }
            event.player.openInventory(inventory)
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = ContainerKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            inventories.remove(key)
        }
    }

    override fun onDisable() {
        inventories.clear()
        super.onDisable()
    }
}
