package me.nebula.orbit.mechanic.daylightdetector

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private data class DetectorKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class DaylightDetectorModule : OrbitModule("daylight-detector") {

    private val detectors = ConcurrentHashMap.newKeySet<DetectorKey>()

    override fun onEnable() {
        super.onEnable()

        detectors.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:daylight_detector") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            detectors.add(DetectorKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ()))
        }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (block.name() != "minecraft:daylight_detector") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val inverted = block.getProperty("inverted") == "true"
            instance.setBlock(pos, block.withProperty("inverted", (!inverted).toString()))
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = detectors.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }

                val block = instance.getBlock(key.x, key.y, key.z)
                if (block.name() != "minecraft:daylight_detector") {
                    iterator.remove()
                    continue
                }

                val time = instance.time % 24000
                val inverted = block.getProperty("inverted") == "true"
                val power = calculatePower(time, inverted)
                instance.setBlock(key.x, key.y, key.z, block.withProperty("power", power.toString()))
            }
        }.repeat(TaskSchedule.tick(20)).schedule()
    }

    override fun onDisable() {
        detectors.clear()
        super.onDisable()
    }

    private fun calculatePower(time: Long, inverted: Boolean): Int {
        val isDaytime = time in 0..12000
        val basePower = when {
            isDaytime -> {
                val progress = if (time < 6000) time / 6000.0 else (12000 - time) / 6000.0
                (progress * 15).toInt().coerceIn(0, 15)
            }
            else -> 0
        }
        return if (inverted) 15 - basePower else basePower
    }
}
