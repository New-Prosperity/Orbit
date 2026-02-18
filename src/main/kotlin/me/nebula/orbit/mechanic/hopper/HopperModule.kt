package me.nebula.orbit.mechanic.hopper

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translateDefault
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import java.util.concurrent.ConcurrentHashMap

private data class HopperKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class HopperModule : OrbitModule("hopper") {

    private val inventories = ConcurrentHashMap<HopperKey, Inventory>()

    override fun onEnable() {
        super.onEnable()
        inventories.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:hopper") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = HopperKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val inventory = inventories.computeIfAbsent(key) {
                Inventory(InventoryType.HOPPER, translateDefault("orbit.mechanic.hopper.title"))
            }
            event.player.openInventory(inventory)
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = HopperKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            inventories.remove(key)
        }
    }

    override fun onDisable() {
        inventories.clear()
        super.onDisable()
    }
}
