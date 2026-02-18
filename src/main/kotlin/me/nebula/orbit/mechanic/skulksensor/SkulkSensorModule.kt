package me.nebula.orbit.mechanic.skulksensor

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private data class SensorKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val SENSOR_BLOCKS = setOf("minecraft:sculk_sensor", "minecraft:calibrated_sculk_sensor")

class SkulkSensorModule : OrbitModule("skulk-sensor") {

    private val sensors = ConcurrentHashMap<SensorKey, Long>()

    override fun onEnable() {
        super.onEnable()

        sensors.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in SENSOR_BLOCKS) return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            sensors[SensorKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())] = 0L
        }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if (event.player.isSneaking) return@addListener
            triggerNearbySensors(event.player.instance?.let { System.identityHashCode(it) } ?: return@addListener,
                event.player.position.blockX(), event.player.position.blockY(), event.player.position.blockZ())
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            triggerNearbySensors(System.identityHashCode(instance),
                event.blockPosition.blockX(), event.blockPosition.blockY(), event.blockPosition.blockZ())
        }
    }

    private fun triggerNearbySensors(instanceHash: Int, x: Int, y: Int, z: Int) {
        val now = System.currentTimeMillis()
        val range = 8

        sensors.entries.forEach { (key, lastTriggered) ->
            if (key.instanceHash != instanceHash) return@forEach
            if (now - lastTriggered < 2000L) return@forEach

            val dx = key.x - x
            val dy = key.y - y
            val dz = key.z - z
            if (dx * dx + dy * dy + dz * dz > range * range) return@forEach

            sensors[key] = now

            val instance = MinecraftServer.getInstanceManager().instances
                .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: return@forEach

            val block = instance.getBlock(key.x, key.y, key.z)
            if (block.name() !in SENSOR_BLOCKS) {
                sensors.remove(key)
                return@forEach
            }

            instance.setBlock(key.x, key.y, key.z, block.withProperty("sculk_sensor_phase", "active"))

            MinecraftServer.getSchedulerManager().buildTask {
                val current = instance.getBlock(key.x, key.y, key.z)
                if (current.name() in SENSOR_BLOCKS) {
                    instance.setBlock(key.x, key.y, key.z, current.withProperty("sculk_sensor_phase", "inactive"))
                }
            }.delay(TaskSchedule.tick(40)).schedule()
        }
    }

    override fun onDisable() {
        sensors.clear()
        super.onDisable()
    }
}
