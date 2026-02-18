package me.nebula.orbit.mechanic.dispenser

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.util.concurrent.ConcurrentHashMap

private data class DispenserKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class DispenserModule : OrbitModule("dispenser") {

    private val dispenserSlots = ConcurrentHashMap<DispenserKey, MutableList<ItemStack>>()

    override fun onEnable() {
        super.onEnable()
        dispenserSlots.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            val isDispenser = block.name() == "minecraft:dispenser"
            val isDropper = block.name() == "minecraft:dropper"
            if (!isDispenser && !isDropper) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val facing = block.getProperty("facing") ?: "north"

            val key = DispenserKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            val items = dispenserSlots[key]
            if (items.isNullOrEmpty()) return@addListener

            val item = items.removeFirst()
            if (items.isEmpty()) dispenserSlots.remove(key)

            val direction = when (facing) {
                "north" -> Vec(0.0, 0.0, -1.0)
                "south" -> Vec(0.0, 0.0, 1.0)
                "west" -> Vec(-1.0, 0.0, 0.0)
                "east" -> Vec(1.0, 0.0, 0.0)
                "up" -> Vec(0.0, 1.0, 0.0)
                "down" -> Vec(0.0, -1.0, 0.0)
                else -> return@addListener
            }

            val spawnPos = Pos(
                pos.x() + 0.5 + direction.x(),
                pos.y() + 0.5 + direction.y(),
                pos.z() + 0.5 + direction.z(),
            )

            val targetBlock = instance.getBlock(spawnPos)
            if (targetBlock.isSolid) return@addListener

            val itemEntity = Entity(EntityType.ITEM)
            itemEntity.setInstance(instance, spawnPos)
            itemEntity.velocity = direction.mul(4.0)

            instance.playSound(
                Sound.sound(SoundEvent.BLOCK_DISPENSER_DISPENSE.key(), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }

    override fun onDisable() {
        dispenserSlots.clear()
        super.onDisable()
    }
}
