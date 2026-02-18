package me.nebula.orbit.mechanic.scaffolding

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule

private const val MAX_DISTANCE = 6

class ScaffoldingModule : OrbitModule("scaffolding") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:scaffolding") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val distance = computeDistance(instance, pos)

            if (distance > MAX_DISTANCE) {
                event.isCancelled = true
                return@addListener
            }

            MinecraftServer.getSchedulerManager().buildTask {
                checkStability(instance, pos)
            }.delay(TaskSchedule.tick(1)).schedule()
        }
    }

    private fun computeDistance(instance: Instance, pos: Point): Int {
        val below = instance.getBlock(pos.add(0.0, -1.0, 0.0))
        if (below.name() != "minecraft:scaffolding") {
            return if (below == Block.AIR) MAX_DISTANCE + 1 else 0
        }
        val belowDistance = below.getProperty("distance")?.toIntOrNull() ?: 0
        return belowDistance + 1
    }

    private fun checkStability(instance: Instance, pos: Point) {
        val block = instance.getBlock(pos)
        if (block.name() != "minecraft:scaffolding") return
        val below = instance.getBlock(pos.add(0.0, -1.0, 0.0))
        if (below == Block.AIR || (below.name() == "minecraft:scaffolding" &&
                    (below.getProperty("distance")?.toIntOrNull() ?: 0) >= MAX_DISTANCE)
        ) {
            instance.setBlock(pos, Block.AIR)
        }
    }
}
