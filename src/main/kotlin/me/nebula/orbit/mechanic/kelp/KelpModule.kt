package me.nebula.orbit.mechanic.kelp

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class KelpKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class KelpModule : OrbitModule("kelp") {

    private val kelpPositions = ConcurrentHashMap.newKeySet<KelpKey>()

    override fun onEnable() {
        super.onEnable()

        kelpPositions.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(net.minestom.server.event.player.PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:kelp") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            kelpPositions.add(KelpKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ()))
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = kelpPositions.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (Random.nextFloat() > 0.14f) continue

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }

                val block = instance.getBlock(key.x, key.y, key.z)
                if (block.name() != "minecraft:kelp" && block.name() != "minecraft:kelp_plant") {
                    iterator.remove()
                    continue
                }

                val age = block.getProperty("age")?.toIntOrNull() ?: 0
                if (age >= 25) {
                    iterator.remove()
                    continue
                }

                val aboveY = key.y + 1
                val above = instance.getBlock(key.x, aboveY, key.z)
                if (above.name() != "minecraft:water") {
                    iterator.remove()
                    continue
                }

                val kelpPlant = Block.fromKey("minecraft:kelp_plant") ?: continue
                instance.setBlock(key.x, key.y, key.z, kelpPlant)

                val newKelp = Block.fromKey("minecraft:kelp")?.withProperty("age", (age + 1).toString()) ?: continue
                instance.setBlock(key.x, aboveY, key.z, newKelp)
                kelpPositions.add(KelpKey(key.instanceHash, key.x, aboveY, key.z))
                iterator.remove()
            }
        }.repeat(TaskSchedule.tick(200)).schedule()
    }

    override fun onDisable() {
        kelpPositions.clear()
        super.onDisable()
    }
}
