package me.nebula.orbit.mechanic.lavaflow

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule

private const val MAX_DEPTH = 3

class LavaFlowModule : OrbitModule("lava-flow") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block != Block.LAVA) return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            scheduleSpread(instance, pos.blockX(), pos.blockY(), pos.blockZ(), 0)
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:lava") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val x = pos.blockX()
            val y = pos.blockY()
            val z = pos.blockZ()

            intArrayOf(-1, 1).forEach { dx ->
                val neighbor = instance.getBlock(x + dx, y, z)
                if (neighbor.name() == "minecraft:lava") {
                    val level = neighbor.getProperty("level")?.toIntOrNull() ?: 0
                    scheduleSpread(instance, x + dx, y, z, level)
                }
            }
            intArrayOf(-1, 1).forEach { dz ->
                val neighbor = instance.getBlock(x, y, z + dz)
                if (neighbor.name() == "minecraft:lava") {
                    val level = neighbor.getProperty("level")?.toIntOrNull() ?: 0
                    scheduleSpread(instance, x, y, z + dz, level)
                }
            }
        }
    }

    private fun scheduleSpread(instance: Instance, x: Int, y: Int, z: Int, level: Int) {
        if (level >= MAX_DEPTH) return

        instance.scheduler().buildTask {
            spreadLava(instance, x, y, z, level)
        }.delay(TaskSchedule.tick(30)).schedule()
    }

    private fun spreadLava(instance: Instance, x: Int, y: Int, z: Int, level: Int) {
        val nextLevel = level + 1
        val nextBlock = Block.LAVA.withProperty("level", nextLevel.toString())

        val downBlock = instance.getBlock(x, y - 1, z)
        if (downBlock == Block.AIR) {
            instance.setBlock(x, y - 1, z, Block.LAVA.withProperty("level", "1"))
            scheduleSpread(instance, x, y - 1, z, 1)
            return
        }

        val offsets = intArrayOf(-1, 1)
        for (dx in offsets) {
            trySpreadTo(instance, x + dx, y, z, nextBlock, nextLevel)
        }
        for (dz in offsets) {
            trySpreadTo(instance, x, y, z + dz, nextBlock, nextLevel)
        }
    }

    private fun trySpreadTo(instance: Instance, x: Int, y: Int, z: Int, lavaBlock: Block, level: Int) {
        val existing = instance.getBlock(x, y, z)

        if (existing == Block.AIR) {
            instance.setBlock(x, y, z, lavaBlock)
            scheduleSpread(instance, x, y, z, level)
            return
        }

        if (existing.name() == "minecraft:water") {
            val waterLevel = existing.getProperty("level")?.toIntOrNull() ?: 0
            val lavaLevel = lavaBlock.getProperty("level")?.toIntOrNull() ?: 0

            if (waterLevel == 0 && lavaLevel > 0) {
                instance.setBlock(x, y, z, Block.OBSIDIAN)
            } else if (waterLevel == 0 && lavaLevel == 0) {
                instance.setBlock(x, y, z, Block.OBSIDIAN)
            } else {
                instance.setBlock(x, y, z, Block.COBBLESTONE)
            }
        }
    }
}
