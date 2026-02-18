package me.nebula.orbit.mechanic.lightningrod

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private data class RodKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class LightningRodModule : OrbitModule("lightning-rod") {

    private val rods = ConcurrentHashMap.newKeySet<RodKey>()

    override fun onEnable() {
        super.onEnable()
        rods.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:lightning_rod") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = RodKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            rods.add(key)
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = rods.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }
                val pos = net.minestom.server.coordinate.Vec(key.x.toDouble(), key.y.toDouble(), key.z.toDouble())
                val block = instance.getBlock(pos)
                if (block.name() != "minecraft:lightning_rod") {
                    iterator.remove()
                    continue
                }

                val powered = block.getProperty("powered") == "true"
                if (powered) {
                    instance.setBlock(pos, block.withProperty("powered", "false"))
                }
            }
        }.repeat(TaskSchedule.tick(20)).schedule()
    }

    fun findNearestRod(instanceHash: Int, x: Double, y: Double, z: Double, range: Double = 128.0): Triple<Int, Int, Int>? =
        rods.filter { it.instanceHash == instanceHash }
            .minByOrNull {
                val dx = it.x - x; val dy = it.y - y; val dz = it.z - z
                dx * dx + dy * dy + dz * dz
            }
            ?.takeIf {
                val dx = it.x - x; val dy = it.y - y; val dz = it.z - z
                dx * dx + dy * dy + dz * dz <= range * range
            }
            ?.let { Triple(it.x, it.y, it.z) }

    override fun onDisable() {
        rods.clear()
        super.onDisable()
    }
}
