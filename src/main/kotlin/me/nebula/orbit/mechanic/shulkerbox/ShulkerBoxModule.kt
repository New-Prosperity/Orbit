package me.nebula.orbit.mechanic.shulkerbox

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translateDefault
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.sound.SoundEvent
import java.util.concurrent.ConcurrentHashMap

private data class ShulkerKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val SHULKER_BLOCKS = buildSet {
    add("minecraft:shulker_box")
    val colors = listOf(
        "white", "orange", "magenta", "light_blue", "yellow", "lime",
        "pink", "gray", "light_gray", "cyan", "purple", "blue",
        "brown", "green", "red", "black",
    )
    colors.forEach { add("minecraft:${it}_shulker_box") }
}

class ShulkerBoxModule : OrbitModule("shulker-box") {

    private val inventories = ConcurrentHashMap<ShulkerKey, Inventory>()

    override fun onEnable() {
        super.onEnable()
        inventories.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() !in SHULKER_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val above = instance.getBlock(pos.add(0, 1, 0))
            if (above.isSolid) return@addListener

            val key = ShulkerKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            val inventory = inventories.computeIfAbsent(key) {
                Inventory(InventoryType.CHEST_3_ROW, translateDefault("orbit.mechanic.shulker_box.title"))
            }
            event.player.openInventory(inventory)

            instance.playSound(
                Sound.sound(SoundEvent.BLOCK_SHULKER_BOX_OPEN.key(), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = ShulkerKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            inventories.remove(key)
        }
    }

    override fun onDisable() {
        inventories.clear()
        super.onDisable()
    }
}
