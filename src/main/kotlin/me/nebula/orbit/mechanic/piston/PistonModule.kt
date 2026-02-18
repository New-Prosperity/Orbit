package me.nebula.orbit.mechanic.piston

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

private const val MAX_PUSH_LIMIT = 12

private val IMMOVABLE_BLOCKS = setOf(
    "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:bedrock",
    "minecraft:reinforced_deepslate", "minecraft:end_portal_frame",
    "minecraft:enchanting_table", "minecraft:ender_chest",
)

class PistonModule : OrbitModule("piston") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            val isPiston = block.name() == "minecraft:piston" || block.name() == "minecraft:sticky_piston"
            if (!isPiston) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val facing = block.getProperty("facing") ?: return@addListener
            val extended = block.getProperty("extended") == "true"
            val sticky = block.name() == "minecraft:sticky_piston"

            val direction = when (facing) {
                "north" -> Vec(0.0, 0.0, -1.0)
                "south" -> Vec(0.0, 0.0, 1.0)
                "west" -> Vec(-1.0, 0.0, 0.0)
                "east" -> Vec(1.0, 0.0, 0.0)
                "up" -> Vec(0.0, 1.0, 0.0)
                "down" -> Vec(0.0, -1.0, 0.0)
                else -> return@addListener
            }

            val vecPos = Vec(pos.x().toDouble(), pos.y().toDouble(), pos.z().toDouble())
            if (!extended) {
                extend(instance, vecPos, direction, facing)
                instance.setBlock(pos, block.withProperty("extended", "true"))
            } else {
                retract(instance, vecPos, direction, sticky)
                instance.setBlock(pos, block.withProperty("extended", "false"))
            }
        }
    }

    private fun extend(instance: Instance, pistonPos: Vec, direction: Vec, facing: String) {
        val headPos = pistonPos.add(direction)
        val blocks = mutableListOf<Pair<Vec, Block>>()
        var checkPos = headPos

        for (i in 0 until MAX_PUSH_LIMIT) {
            val block = instance.getBlock(checkPos)
            if (block == Block.AIR) break
            if (block.name() in IMMOVABLE_BLOCKS) return
            blocks.add(checkPos to block)
            checkPos = checkPos.add(direction)
        }

        if (blocks.size >= MAX_PUSH_LIMIT) return

        for ((pos, _) in blocks.reversed()) {
            val target = pos.add(direction)
            val block = instance.getBlock(pos)
            instance.setBlock(target, block)
        }

        for ((pos, _) in blocks) {
            instance.setBlock(pos, Block.AIR)
        }

        val pistonHead = Block.PISTON_HEAD.withProperty("facing", facing)
        instance.setBlock(headPos, pistonHead)
    }

    private fun retract(instance: Instance, pistonPos: Vec, direction: Vec, sticky: Boolean) {
        val headPos = pistonPos.add(direction)
        instance.setBlock(headPos, Block.AIR)

        if (sticky) {
            val pullPos = headPos.add(direction)
            val block = instance.getBlock(pullPos)
            if (block != Block.AIR && block.name() !in IMMOVABLE_BLOCKS) {
                instance.setBlock(headPos, block)
                instance.setBlock(pullPos, Block.AIR)
            }
        }
    }
}
