package me.nebula.orbit.mechanic.observer

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule

private val FACING_OFFSETS = mapOf(
    "north" to Triple(0, 0, -1),
    "south" to Triple(0, 0, 1),
    "east" to Triple(1, 0, 0),
    "west" to Triple(-1, 0, 0),
    "up" to Triple(0, 1, 0),
    "down" to Triple(0, -1, 0),
)

class ObserverModule : OrbitModule("observer") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            checkAdjacentObservers(instance, pos.blockX(), pos.blockY(), pos.blockZ())
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            MinecraftServer.getSchedulerManager().buildTask {
                checkAdjacentObservers(instance, pos.blockX(), pos.blockY(), pos.blockZ())
            }.delay(TaskSchedule.tick(1)).schedule()
        }
    }

    private fun checkAdjacentObservers(instance: Instance, bx: Int, by: Int, bz: Int) {
        for ((facing, offset) in FACING_OFFSETS) {
            val ox = bx + offset.first
            val oy = by + offset.second
            val oz = bz + offset.third
            val block = instance.getBlock(ox, oy, oz)

            if (block.name() != "minecraft:observer") continue

            val observerFacing = block.getProperty("facing") ?: continue
            val (dx, dy, dz) = FACING_OFFSETS[observerFacing] ?: continue

            if (ox + dx == bx && oy + dy == by && oz + dz == bz) {
                val powered = block.getProperty("powered") ?: "false"
                if (powered == "true") continue

                instance.setBlock(ox, oy, oz, block.withProperty("powered", "true"))

                MinecraftServer.getSchedulerManager().buildTask {
                    val current = instance.getBlock(ox, oy, oz)
                    if (current.name() == "minecraft:observer" && current.getProperty("powered") == "true") {
                        instance.setBlock(ox, oy, oz, current.withProperty("powered", "false"))
                    }
                }.delay(TaskSchedule.tick(2)).schedule()
            }
        }
    }
}
