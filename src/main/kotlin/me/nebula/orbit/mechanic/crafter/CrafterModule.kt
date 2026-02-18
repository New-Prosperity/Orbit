package me.nebula.orbit.mechanic.crafter

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translateDefault
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import java.util.concurrent.ConcurrentHashMap

private data class CrafterKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class CrafterModule : OrbitModule("crafter") {

    private val inventories = ConcurrentHashMap<CrafterKey, Inventory>()

    override fun onEnable() {
        super.onEnable()
        inventories.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:crafter") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = CrafterKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val invType = runCatching { InventoryType.CRAFTER_3X3 }.getOrElse { InventoryType.CHEST_3_ROW }
            val inventory = inventories.computeIfAbsent(key) {
                Inventory(invType, translateDefault("orbit.mechanic.crafter.title"))
            }
            event.player.openInventory(inventory)
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:crafter") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = CrafterKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            inventories.remove(key)
        }
    }

    override fun onDisable() {
        inventories.clear()
        super.onDisable()
    }
}
