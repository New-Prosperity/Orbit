package me.nebula.orbit.mechanic.smoker

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translateDefault
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import java.util.concurrent.ConcurrentHashMap

private data class SmokerKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class SmokerModule : OrbitModule("smoker") {

    private val inventories = ConcurrentHashMap<SmokerKey, Inventory>()

    override fun onEnable() {
        super.onEnable()
        inventories.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:smoker") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = SmokerKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val inv = inventories.getOrPut(key) {
                Inventory(InventoryType.SMOKER, translateDefault("orbit.mechanic.smoker.title"))
            }

            event.player.openInventory(inv)
        }
    }

    override fun onDisable() {
        inventories.clear()
        super.onDisable()
    }
}
