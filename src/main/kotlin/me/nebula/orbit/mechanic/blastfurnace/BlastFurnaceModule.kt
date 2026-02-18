package me.nebula.orbit.mechanic.blastfurnace

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translateDefault
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import java.util.concurrent.ConcurrentHashMap

private data class BlastKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class BlastFurnaceModule : OrbitModule("blast-furnace") {

    private val inventories = ConcurrentHashMap<BlastKey, Inventory>()

    override fun onEnable() {
        super.onEnable()
        inventories.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:blast_furnace") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = BlastKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val inv = inventories.getOrPut(key) {
                Inventory(InventoryType.BLAST_FURNACE, translateDefault("orbit.mechanic.blast_furnace.title"))
            }

            event.player.openInventory(inv)
        }
    }

    override fun onDisable() {
        inventories.clear()
        super.onDisable()
    }
}
