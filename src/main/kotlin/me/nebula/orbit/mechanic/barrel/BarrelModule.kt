package me.nebula.orbit.mechanic.barrel

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translateDefault
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.sound.SoundEvent
import java.util.concurrent.ConcurrentHashMap

private data class BarrelKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class BarrelModule : OrbitModule("barrel") {

    private val inventories = ConcurrentHashMap<BarrelKey, Inventory>()

    override fun onEnable() {
        super.onEnable()
        inventories.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:barrel") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = BarrelKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val inv = inventories.getOrPut(key) {
                Inventory(InventoryType.CHEST_3_ROW, translateDefault("orbit.mechanic.barrel.title"))
            }

            instance.setBlock(pos, event.block.withProperty("open", "true"))

            event.player.openInventory(inv)
            instance.playSound(
                Sound.sound(SoundEvent.BLOCK_BARREL_OPEN.key(), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }

    override fun onDisable() {
        inventories.clear()
        super.onDisable()
    }
}
