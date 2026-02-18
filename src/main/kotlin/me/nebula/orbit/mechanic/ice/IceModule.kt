package me.nebula.orbit.mechanic.ice

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class IceKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class IceModule : OrbitModule("ice") {

    private val iceBlocks = ConcurrentHashMap.newKeySet<IceKey>()

    override fun onEnable() {
        super.onEnable()

        iceBlocks.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val block = event.block
            if (block.name() != "minecraft:ice") return@addListener

            val held = event.player.getItemInMainHand()
            val hasSilkTouch = held.material().name().endsWith("_pickaxe")

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            if (!hasSilkTouch) {
                MinecraftServer.getSchedulerManager().buildTask {
                    instance.setBlock(pos, Block.WATER)
                }.delay(TaskSchedule.tick(1)).schedule()
            }
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = iceBlocks.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (Random.nextFloat() > 0.005f) continue

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }

                val block = instance.getBlock(key.x, key.y, key.z)
                if (block.name() == "minecraft:ice") {
                    instance.setBlock(key.x, key.y, key.z, Block.WATER)
                }
                iterator.remove()
            }
        }.repeat(TaskSchedule.tick(100)).schedule()
    }

    override fun onDisable() {
        iceBlocks.clear()
        super.onDisable()
    }
}
