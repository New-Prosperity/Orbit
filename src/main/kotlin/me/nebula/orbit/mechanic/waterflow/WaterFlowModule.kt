package me.nebula.orbit.mechanic.waterflow

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule

private const val MAX_DEPTH = 7

class WaterFlowModule : OrbitModule("water-flow") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block != Block.WATER) return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            scheduleSpread(instance, pos.blockX(), pos.blockY(), pos.blockZ(), 0)
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:water") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val x = pos.blockX()
            val y = pos.blockY()
            val z = pos.blockZ()

            intArrayOf(-1, 1).forEach { dx ->
                val neighbor = instance.getBlock(x + dx, y, z)
                if (neighbor.name() == "minecraft:water") {
                    val level = neighbor.getProperty("level")?.toIntOrNull() ?: 0
                    scheduleSpread(instance, x + dx, y, z, level)
                }
            }
            intArrayOf(-1, 1).forEach { dz ->
                val neighbor = instance.getBlock(x, y, z + dz)
                if (neighbor.name() == "minecraft:water") {
                    val level = neighbor.getProperty("level")?.toIntOrNull() ?: 0
                    scheduleSpread(instance, x, y, z + dz, level)
                }
            }
        }
    }

    private fun scheduleSpread(instance: Instance, x: Int, y: Int, z: Int, level: Int) {
        if (level >= MAX_DEPTH) return

        instance.scheduler().buildTask {
            spreadWater(instance, x, y, z, level)
        }.delay(TaskSchedule.tick(5)).schedule()
    }

    private fun spreadWater(instance: Instance, x: Int, y: Int, z: Int, level: Int) {
        val nextLevel = level + 1
        val nextBlock = Block.WATER.withProperty("level", nextLevel.toString())

        val downBlock = instance.getBlock(x, y - 1, z)
        if (downBlock == Block.AIR) {
            instance.setBlock(x, y - 1, z, Block.WATER.withProperty("level", "1"))
            scheduleSpread(instance, x, y - 1, z, 1)
            return
        }

        val offsets = intArrayOf(-1, 1)
        for (dx in offsets) {
            val neighbor = instance.getBlock(x + dx, y, z)
            if (neighbor == Block.AIR) {
                instance.setBlock(x + dx, y, z, nextBlock)
                scheduleSpread(instance, x + dx, y, z, nextLevel)
            }
        }
        for (dz in offsets) {
            val neighbor = instance.getBlock(x, y, z + dz)
            if (neighbor == Block.AIR) {
                instance.setBlock(x, y, z + dz, nextBlock)
                scheduleSpread(instance, x, y, z + dz, nextLevel)
            }
        }
    }
}
