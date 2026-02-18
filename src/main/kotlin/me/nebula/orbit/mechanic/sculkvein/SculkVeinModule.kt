package me.nebula.orbit.mechanic.sculkvein

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block

private val FACE_PROPERTIES = listOf("north", "south", "east", "west", "up", "down")

class SculkVeinModule : OrbitModule("sculk-vein") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:sculk_vein") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val hasAdjacentSolid = ADJACENT_OFFSETS.any { (dx, dy, dz) ->
                val neighbor = instance.getBlock(pos.blockX() + dx, pos.blockY() + dy, pos.blockZ() + dz)
                !neighbor.isAir && neighbor.name() != "minecraft:sculk_vein"
            }

            if (!hasAdjacentSolid) {
                event.isCancelled = true
                return@addListener
            }

            var vein = Block.SCULK_VEIN
            FACE_PROPERTIES.forEachIndexed { index, property ->
                val (dx, dy, dz) = ADJACENT_OFFSETS[index]
                val neighbor = instance.getBlock(pos.blockX() + dx, pos.blockY() + dy, pos.blockZ() + dz)
                val value = if (!neighbor.isAir && neighbor.name() != "minecraft:sculk_vein") "true" else "false"
                vein = vein.withProperty(property, value)
            }

            event.block = vein
        }
    }
}

private val ADJACENT_OFFSETS = listOf(
    Triple(0, 0, -1),
    Triple(0, 0, 1),
    Triple(1, 0, 0),
    Triple(-1, 0, 0),
    Triple(0, 1, 0),
    Triple(0, -1, 0),
)
