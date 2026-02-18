package me.nebula.orbit.mechanic.sugarcane

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class CaneKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private const val MAX_HEIGHT = 3

class SugarCaneModule : OrbitModule("sugarcane") {

    private val caneBases = ConcurrentHashMap.newKeySet<CaneKey>()

    override fun onEnable() {
        super.onEnable()
        caneBases.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:sugar_cane") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = CaneKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            caneBases.add(key)
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = caneBases.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (Random.nextFloat() > 0.01f) continue

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }

                if (instance.getBlock(key.x, key.y, key.z).name() != "minecraft:sugar_cane") {
                    iterator.remove()
                    continue
                }

                var topY = key.y
                while (instance.getBlock(key.x, topY + 1, key.z).name() == "minecraft:sugar_cane") topY++

                if (topY - key.y >= MAX_HEIGHT - 1) continue
                if (instance.getBlock(key.x, topY + 1, key.z) != Block.AIR) continue

                instance.setBlock(key.x, topY + 1, key.z, Block.SUGAR_CANE)
            }
        }.repeat(TaskSchedule.tick(60)).schedule()
    }

    override fun onDisable() {
        caneBases.clear()
        super.onDisable()
    }
}
