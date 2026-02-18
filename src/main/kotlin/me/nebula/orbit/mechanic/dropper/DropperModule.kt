package me.nebula.orbit.mechanic.dropper

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translateDefault
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import java.util.concurrent.ConcurrentHashMap

private data class DropperKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class DropperModule : OrbitModule("dropper") {

    private val inventories = ConcurrentHashMap<DropperKey, Inventory>()

    override fun onEnable() {
        super.onEnable()
        inventories.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:dropper") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = DropperKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val inv = inventories.getOrPut(key) {
                Inventory(InventoryType.CHEST_3_ROW, translateDefault("orbit.mechanic.dropper.title"))
            }

            event.player.openInventory(inv)
        }
    }

    override fun onDisable() {
        inventories.clear()
        super.onDisable()
    }
}
