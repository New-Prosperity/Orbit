package me.nebula.orbit.mechanic.chorusflower

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class FlowerKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class ChorusFlowerGrowthModule : OrbitModule("chorus-flower-growth") {

    private val flowers = ConcurrentHashMap.newKeySet<FlowerKey>()

    override fun onEnable() {
        super.onEnable()

        flowers.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(net.minestom.server.event.player.PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:chorus_flower") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            flowers.add(FlowerKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ()))
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = flowers.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (Random.nextFloat() > 0.05f) continue

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }

                val block = instance.getBlock(key.x, key.y, key.z)
                if (block.name() != "minecraft:chorus_flower") {
                    iterator.remove()
                    continue
                }

                val age = block.getProperty("age")?.toIntOrNull() ?: 0
                if (age >= 5) {
                    iterator.remove()
                    continue
                }

                val aboveY = key.y + 1
                if (aboveY > 319) {
                    iterator.remove()
                    continue
                }

                val above = instance.getBlock(key.x, aboveY, key.z)
                if (!above.isAir) {
                    iterator.remove()
                    continue
                }

                val plant = Block.fromKey("minecraft:chorus_plant") ?: continue
                instance.setBlock(key.x, key.y, key.z, plant)

                val newFlower = block.withProperty("age", (age + 1).toString())
                instance.setBlock(key.x, aboveY, key.z, newFlower)

                iterator.remove()
                flowers.add(FlowerKey(key.instanceHash, key.x, aboveY, key.z))
            }
        }.repeat(TaskSchedule.tick(300)).schedule()
    }

    override fun onDisable() {
        flowers.clear()
        super.onDisable()
    }
}
