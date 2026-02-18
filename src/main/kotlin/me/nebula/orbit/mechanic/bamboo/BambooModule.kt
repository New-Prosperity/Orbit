package me.nebula.orbit.mechanic.bamboo

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class BambooKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private const val MAX_HEIGHT = 16

class BambooModule : OrbitModule("bamboo") {

    private val bambooBases = ConcurrentHashMap.newKeySet<BambooKey>()

    override fun onEnable() {
        super.onEnable()
        bambooBases.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:bamboo") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = BambooKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            bambooBases.add(key)
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = bambooBases.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (Random.nextFloat() > 0.02f) continue

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }

                if (instance.getBlock(key.x, key.y, key.z).name() != "minecraft:bamboo") {
                    iterator.remove()
                    continue
                }

                var topY = key.y
                while (instance.getBlock(key.x, topY + 1, key.z).name() == "minecraft:bamboo") topY++

                if (topY - key.y >= MAX_HEIGHT - 1) continue
                if (instance.getBlock(key.x, topY + 1, key.z) != Block.AIR) continue

                instance.setBlock(key.x, topY + 1, key.z, Block.BAMBOO)
            }
        }.repeat(TaskSchedule.tick(60)).schedule()
    }

    override fun onDisable() {
        bambooBases.clear()
        super.onDisable()
    }
}
