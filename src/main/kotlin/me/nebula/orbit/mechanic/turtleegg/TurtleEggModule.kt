package me.nebula.orbit.mechanic.turtleegg

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class EggKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class TurtleEggModule : OrbitModule("turtle-egg") {

    private val eggHatchProgress = ConcurrentHashMap<EggKey, Int>()

    override fun onEnable() {
        super.onEnable()
        eggHatchProgress.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val below = player.position.sub(0.0, 0.1, 0.0)
            val block = instance.getBlock(below)

            if (block.name() != "minecraft:turtle_egg") return@addListener

            val eggs = block.getProperty("eggs")?.toIntOrNull() ?: 1
            if (eggs > 1) {
                instance.setBlock(below, block.withProperty("eggs", (eggs - 1).toString()))
            } else {
                instance.setBlock(below, Block.AIR)
            }
        }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:turtle_egg") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = EggKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            eggHatchProgress[key] = 0
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = eggHatchProgress.entries.iterator()
            while (iterator.hasNext()) {
                val (key, hatch) = iterator.next()
                if (Random.nextFloat() > 0.02f) continue

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }
                val pos = net.minestom.server.coordinate.Vec(key.x.toDouble(), key.y.toDouble(), key.z.toDouble())
                val block = instance.getBlock(pos)

                if (block.name() != "minecraft:turtle_egg") {
                    iterator.remove()
                    continue
                }

                val newHatch = hatch + 1
                if (newHatch >= 2) {
                    val eggs = block.getProperty("eggs")?.toIntOrNull() ?: 1
                    if (eggs > 1) {
                        instance.setBlock(pos, block.withProperty("eggs", (eggs - 1).toString()))
                    } else {
                        instance.setBlock(pos, Block.AIR)
                        iterator.remove()
                    }
                } else {
                    eggHatchProgress[key] = newHatch
                    instance.setBlock(pos, block.withProperty("hatch", newHatch.toString()))
                }
            }
        }.repeat(TaskSchedule.tick(100)).schedule()
    }

    override fun onDisable() {
        eggHatchProgress.clear()
        super.onDisable()
    }
}
