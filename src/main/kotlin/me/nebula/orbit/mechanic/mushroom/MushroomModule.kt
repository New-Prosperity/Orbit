package me.nebula.orbit.mechanic.mushroom

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class MushroomKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val MUSHROOM_BLOCKS = setOf("minecraft:brown_mushroom", "minecraft:red_mushroom")

class MushroomModule : OrbitModule("mushroom") {

    private val mushrooms = ConcurrentHashMap.newKeySet<MushroomKey>()

    override fun onEnable() {
        super.onEnable()

        mushrooms.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in MUSHROOM_BLOCKS) return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            mushrooms.add(MushroomKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ()))
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val snapshot = mushrooms.toList()
            for (key in snapshot) {
                if (Random.nextFloat() > 0.005f) continue

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    mushrooms.remove(key)
                    continue
                }

                val block = instance.getBlock(key.x, key.y, key.z)
                if (block.name() !in MUSHROOM_BLOCKS) {
                    mushrooms.remove(key)
                    continue
                }

                val dx = Random.nextInt(-1, 2)
                val dz = Random.nextInt(-1, 2)
                val target = instance.getBlock(key.x + dx, key.y, key.z + dz)
                if (target == Block.AIR) {
                    val below = instance.getBlock(key.x + dx, key.y - 1, key.z + dz)
                    if (below.isSolid) {
                        instance.setBlock(key.x + dx, key.y, key.z + dz, block)
                        mushrooms.add(MushroomKey(key.instanceHash, key.x + dx, key.y, key.z + dz))
                    }
                }
            }
        }.repeat(TaskSchedule.tick(200)).schedule()
    }

    override fun onDisable() {
        mushrooms.clear()
        super.onDisable()
    }
}
