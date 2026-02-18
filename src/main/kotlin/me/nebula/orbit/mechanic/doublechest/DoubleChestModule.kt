package me.nebula.orbit.mechanic.doublechest

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translateDefault
import net.minestom.server.coordinate.Point
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import java.util.concurrent.ConcurrentHashMap

private data class ChestKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val CHEST_BLOCKS = setOf("minecraft:chest", "minecraft:trapped_chest")

private val ADJACENT_OFFSETS = arrayOf(
    intArrayOf(-1, 0),
    intArrayOf(1, 0),
    intArrayOf(0, -1),
    intArrayOf(0, 1),
)

class DoubleChestModule : OrbitModule("double-chest") {

    private val inventories = ConcurrentHashMap<ChestKey, Inventory>()

    override fun onEnable() {
        super.onEnable()
        inventories.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val blockName = event.block.name()
            if (blockName !in CHEST_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val hash = System.identityHashCode(instance)

            val adjacentChestPos = findAdjacentChest(instance, pos, blockName)

            if (adjacentChestPos != null) {
                val (firstKey, _) = orderedKeys(hash, pos, adjacentChestPos)
                val inventory = inventories.computeIfAbsent(firstKey) {
                    Inventory(InventoryType.CHEST_6_ROW, translateDefault("orbit.mechanic.double_chest.title"))
                }
                event.player.openInventory(inventory)
            } else {
                val key = ChestKey(hash, pos.blockX(), pos.blockY(), pos.blockZ())
                val inventory = inventories.computeIfAbsent(key) {
                    Inventory(InventoryType.CHEST_3_ROW, translateDefault("orbit.mechanic.chest.title"))
                }
                event.player.openInventory(inventory)
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val hash = System.identityHashCode(instance)
            val key = ChestKey(hash, pos.blockX(), pos.blockY(), pos.blockZ())
            inventories.remove(key)
        }
    }

    override fun onDisable() {
        inventories.clear()
        super.onDisable()
    }

    private fun findAdjacentChest(instance: Instance, pos: Point, blockName: String): Point? {
        for (offset in ADJACENT_OFFSETS) {
            val nx = pos.blockX() + offset[0]
            val nz = pos.blockZ() + offset[1]
            val neighbor = instance.getBlock(nx, pos.blockY(), nz)
            if (neighbor.name() == blockName) {
                return net.minestom.server.coordinate.Vec(nx.toDouble(), pos.y(), nz.toDouble())
            }
        }
        return null
    }

    private fun orderedKeys(hash: Int, a: Point, b: Point): Pair<ChestKey, ChestKey> {
        val keyA = ChestKey(hash, a.blockX(), a.blockY(), a.blockZ())
        val keyB = ChestKey(hash, b.blockX(), b.blockY(), b.blockZ())
        return if (keyA.x < keyB.x || (keyA.x == keyB.x && keyA.z < keyB.z)) keyA to keyB else keyB to keyA
    }
}
