package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.VanillaModules
import me.nebula.orbit.utils.vanilla.dropInventoryContents
import me.nebula.orbit.utils.vanilla.packBlockPos
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import java.util.concurrent.ConcurrentHashMap

private val CHEST_BLOCKS = setOf("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel")

private val DOUBLE_CHEST_OFFSETS = arrayOf(
    intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1)
)

private class ChestBlockHandler(
    private val key: Key,
    private val inventories: ConcurrentHashMap<Long, Inventory>,
) : BlockHandler {

    override fun getKey(): Key = key

    override fun onInteract(interaction: BlockHandler.Interaction): Boolean {
        if (!VanillaModules.isEnabled(interaction.instance, "chests")) return true
        val blockName = interaction.block.name()
        val pos = interaction.blockPosition
        val x = pos.blockX()
        val y = pos.blockY()
        val z = pos.blockZ()

        if (blockName == "minecraft:barrel") {
            val packed = packBlockPos(x, y, z)
            val inv = inventories.getOrPut(packed) {
                Inventory(InventoryType.CHEST_3_ROW, Component.text("Barrel"))
            }
            interaction.player.openInventory(inv)
            return false
        }

        val adjacent = findAdjacentChest(interaction.instance, x, y, z, blockName)
        if (adjacent != null) {
            val (ax, az) = adjacent
            val key1 = packBlockPos(minOf(x, ax), y, minOf(z, az))
            val key2 = packBlockPos(maxOf(x, ax), y, maxOf(z, az))
            val doubleKey = key1 xor (key2 shl 1)
            val inv = inventories.getOrPut(doubleKey) {
                val title = if (blockName == "minecraft:trapped_chest") "Large Trapped Chest" else "Large Chest"
                Inventory(InventoryType.CHEST_6_ROW, Component.text(title))
            }
            interaction.player.openInventory(inv)
        } else {
            val packed = packBlockPos(x, y, z)
            val inv = inventories.getOrPut(packed) {
                val title = if (blockName == "minecraft:trapped_chest") "Trapped Chest" else "Chest"
                Inventory(InventoryType.CHEST_3_ROW, Component.text(title))
            }
            interaction.player.openInventory(inv)
        }
        return false
    }

    override fun onDestroy(destroy: BlockHandler.Destroy) {
        if (!VanillaModules.isEnabled(destroy.instance, "chests")) return
        val blockName = destroy.block.name()
        val pos = destroy.blockPosition
        val x = pos.blockX()
        val y = pos.blockY()
        val z = pos.blockZ()

        val packed = packBlockPos(x, y, z)
        val inv = inventories.remove(packed)

        val adjacent = findAdjacentChest(destroy.instance, x, y, z, blockName)
        if (adjacent != null) {
            val (ax, az) = adjacent
            val key1 = packBlockPos(minOf(x, ax), y, minOf(z, az))
            val key2 = packBlockPos(maxOf(x, ax), y, maxOf(z, az))
            val doubleKey = key1 xor (key2 shl 1)
            val doubleInv = inventories.remove(doubleKey)
            if (doubleInv != null) dropInventoryContents(destroy.instance, doubleInv, x, y, z)
        }

        if (inv != null) dropInventoryContents(destroy.instance, inv, x, y, z)
    }

    private fun findAdjacentChest(instance: Instance, x: Int, y: Int, z: Int, blockName: String): Pair<Int, Int>? {
        for (offset in DOUBLE_CHEST_OFFSETS) {
            val ax = x + offset[0]
            val az = z + offset[1]
            if (instance.getBlock(ax, y, az).name() == blockName) {
                return ax to az
            }
        }
        return null
    }

}

object ChestModule : VanillaModule {

    override val id = "chests"
    override val description = "Open and store items in chest, trapped chest, and barrel containers with double chest support"

    private val chestInventories = ConcurrentHashMap<Long, Inventory>()

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val blockManager = MinecraftServer.getBlockManager()
        val chestHandler = ChestBlockHandler(Key.key("minecraft:chest"), chestInventories)
        val trappedChestHandler = ChestBlockHandler(Key.key("minecraft:trapped_chest"), chestInventories)
        val barrelHandler = ChestBlockHandler(Key.key("minecraft:barrel"), chestInventories)

        blockManager.registerHandler("minecraft:chest") { chestHandler }
        blockManager.registerHandler("minecraft:trapped_chest") { trappedChestHandler }
        blockManager.registerHandler("minecraft:barrel") { barrelHandler }

        val node = EventNode.all("vanilla-chests")

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val blockName = event.block.name()
            if (blockName !in CHEST_BLOCKS) return@addListener
            val handler = when (blockName) {
                "minecraft:trapped_chest" -> trappedChestHandler
                "minecraft:barrel" -> barrelHandler
                else -> chestHandler
            }
            event.setBlock(event.block.withHandler(handler))
        }

        return node
    }
}
