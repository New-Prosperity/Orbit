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

private val SHULKER_BOX_NAMES = setOf(
    "minecraft:shulker_box",
    "minecraft:white_shulker_box", "minecraft:orange_shulker_box",
    "minecraft:magenta_shulker_box", "minecraft:light_blue_shulker_box",
    "minecraft:yellow_shulker_box", "minecraft:lime_shulker_box",
    "minecraft:pink_shulker_box", "minecraft:gray_shulker_box",
    "minecraft:light_gray_shulker_box", "minecraft:cyan_shulker_box",
    "minecraft:purple_shulker_box", "minecraft:blue_shulker_box",
    "minecraft:brown_shulker_box", "minecraft:green_shulker_box",
    "minecraft:red_shulker_box", "minecraft:black_shulker_box",
)

private class ShulkerBoxHandler(
    private val key: Key,
    private val inventories: ConcurrentHashMap<Long, Inventory>,
) : BlockHandler {

    override fun getKey(): Key = key

    override fun onInteract(interaction: BlockHandler.Interaction): Boolean {
        if (!VanillaModules.isEnabled(interaction.instance, "shulker-box")) return true
        val pos = interaction.blockPosition
        val packed = packBlockPos(pos.blockX(), pos.blockY(), pos.blockZ())
        val inv = inventories.getOrPut(packed) {
            Inventory(InventoryType.SHULKER_BOX, Component.text("Shulker Box"))
        }
        interaction.player.openInventory(inv)
        return false
    }

    override fun onDestroy(destroy: BlockHandler.Destroy) {
        if (!VanillaModules.isEnabled(destroy.instance, "shulker-box")) return
        val pos = destroy.blockPosition
        val packed = packBlockPos(pos.blockX(), pos.blockY(), pos.blockZ())
        val inv = inventories.remove(packed) ?: return
        dropInventoryContents(destroy.instance, inv, pos.blockX(), pos.blockY(), pos.blockZ())
    }
}

object ShulkerBoxModule : VanillaModule {

    override val id = "shulker-box"
    override val description = "Open shulker boxes as 3-row inventory, preserve contents on break"

    private val shulkerInventories = ConcurrentHashMap<Long, Inventory>()

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val blockManager = MinecraftServer.getBlockManager()
        val handler = ShulkerBoxHandler(Key.key("minecraft:shulker_box"), shulkerInventories)
        blockManager.registerHandler("minecraft:shulker_box") { handler }

        val node = EventNode.all("vanilla-shulker-box")

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in SHULKER_BOX_NAMES) return@addListener
            event.block = event.block.withHandler(handler)
        }

        return node
    }
}
