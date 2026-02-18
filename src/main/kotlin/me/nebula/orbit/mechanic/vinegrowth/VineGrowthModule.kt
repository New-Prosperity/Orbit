package me.nebula.orbit.mechanic.vinegrowth

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class VineKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class VineGrowthModule : OrbitModule("vine-growth") {

    private val vines = ConcurrentHashMap.newKeySet<VineKey>()

    override fun onEnable() {
        super.onEnable()

        vines.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:vine") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            vines.add(VineKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ()))
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = vines.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (Random.nextFloat() > 0.02f) continue

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }

                val block = instance.getBlock(key.x, key.y, key.z)
                if (block.name() != "minecraft:vine") {
                    iterator.remove()
                    continue
                }

                val below = instance.getBlock(key.x, key.y - 1, key.z)
                if (below == Block.AIR) {
                    instance.setBlock(key.x, key.y - 1, key.z, block)
                    vines.add(VineKey(key.instanceHash, key.x, key.y - 1, key.z))
                }
            }
        }.repeat(TaskSchedule.tick(100)).schedule()
    }

    override fun onDisable() {
        vines.clear()
        super.onDisable()
    }
}
