package me.nebula.orbit.mechanic.door

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent

class DoorModule : OrbitModule("door") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            val name = block.name()

            val isDoor = name.endsWith("_door") && name != "minecraft:iron_door"
            val isTrapdoor = name.endsWith("_trapdoor") && name != "minecraft:iron_trapdoor"
            val isFenceGate = name.endsWith("_fence_gate")

            if (!isDoor && !isTrapdoor && !isFenceGate) return@addListener

            val currentOpen = block.getProperty("open") == "true"
            val newBlock = block.withProperty("open", (!currentOpen).toString())
            val instance = event.player.instance ?: return@addListener

            instance.setBlock(event.blockPosition, newBlock)

            val sound = if (!currentOpen) {
                when {
                    isDoor -> SoundEvent.BLOCK_WOODEN_DOOR_OPEN
                    isTrapdoor -> SoundEvent.BLOCK_WOODEN_TRAPDOOR_OPEN
                    else -> SoundEvent.BLOCK_FENCE_GATE_OPEN
                }
            } else {
                when {
                    isDoor -> SoundEvent.BLOCK_WOODEN_DOOR_CLOSE
                    isTrapdoor -> SoundEvent.BLOCK_WOODEN_TRAPDOOR_CLOSE
                    else -> SoundEvent.BLOCK_FENCE_GATE_CLOSE
                }
            }

            instance.playSound(
                Sound.sound(sound.key(), Sound.Source.BLOCK, 1f, 1f),
                event.blockPosition.x() + 0.5,
                event.blockPosition.y() + 0.5,
                event.blockPosition.z() + 0.5,
            )

            if (isDoor) syncDoubleDoor(instance, event.blockPosition, newBlock)
        }
    }

    private fun syncDoubleDoor(
        instance: net.minestom.server.instance.Instance,
        pos: net.minestom.server.coordinate.Point,
        changedBlock: Block,
    ) {
        val half = changedBlock.getProperty("half") ?: return
        val hinge = changedBlock.getProperty("hinge") ?: return
        val facing = changedBlock.getProperty("facing") ?: return

        val offsets = when (facing) {
            "north" -> if (hinge == "left") intArrayOf(-1, 0) else intArrayOf(1, 0)
            "south" -> if (hinge == "left") intArrayOf(1, 0) else intArrayOf(-1, 0)
            "west" -> if (hinge == "left") intArrayOf(0, -1) else intArrayOf(0, 1)
            "east" -> if (hinge == "left") intArrayOf(0, 1) else intArrayOf(0, -1)
            else -> return
        }

        val neighborPos = net.minestom.server.coordinate.Vec(
            pos.x() + offsets[0].toDouble(),
            pos.y().toDouble(),
            pos.z() + offsets[1].toDouble(),
        )
        val neighbor = instance.getBlock(neighborPos)
        if (!neighbor.name().endsWith("_door")) return
        if (neighbor.getProperty("half") != half) return

        val oppositeHinge = if (hinge == "left") "right" else "left"
        if (neighbor.getProperty("hinge") != oppositeHinge) return

        val newOpen = changedBlock.getProperty("open") ?: return
        instance.setBlock(neighborPos, neighbor.withProperty("open", newOpen))
    }
}
