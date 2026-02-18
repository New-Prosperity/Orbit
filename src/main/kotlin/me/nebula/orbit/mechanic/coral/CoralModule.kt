package me.nebula.orbit.mechanic.coral

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private data class CoralKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val CORAL_TO_DEAD = mapOf(
    "minecraft:tube_coral_block" to "minecraft:dead_tube_coral_block",
    "minecraft:brain_coral_block" to "minecraft:dead_brain_coral_block",
    "minecraft:bubble_coral_block" to "minecraft:dead_bubble_coral_block",
    "minecraft:fire_coral_block" to "minecraft:dead_fire_coral_block",
    "minecraft:horn_coral_block" to "minecraft:dead_horn_coral_block",
    "minecraft:tube_coral" to "minecraft:dead_tube_coral",
    "minecraft:brain_coral" to "minecraft:dead_brain_coral",
    "minecraft:bubble_coral" to "minecraft:dead_bubble_coral",
    "minecraft:fire_coral" to "minecraft:dead_fire_coral",
    "minecraft:horn_coral" to "minecraft:dead_horn_coral",
    "minecraft:tube_coral_fan" to "minecraft:dead_tube_coral_fan",
    "minecraft:brain_coral_fan" to "minecraft:dead_brain_coral_fan",
    "minecraft:bubble_coral_fan" to "minecraft:dead_bubble_coral_fan",
    "minecraft:fire_coral_fan" to "minecraft:dead_fire_coral_fan",
    "minecraft:horn_coral_fan" to "minecraft:dead_horn_coral_fan",
)

class CoralModule : OrbitModule("coral") {

    private val coralBlocks = ConcurrentHashMap<CoralKey, String>()

    override fun onEnable() {
        super.onEnable()

        coralBlocks.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val deadName = CORAL_TO_DEAD[event.block.name()] ?: return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            coralBlocks[CoralKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())] = deadName
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = coralBlocks.entries.iterator()
            while (iterator.hasNext()) {
                val (key, deadName) = iterator.next()

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }

                val block = instance.getBlock(key.x, key.y, key.z)
                if (block.name() !in CORAL_TO_DEAD) {
                    iterator.remove()
                    continue
                }

                val hasWater = listOf(
                    instance.getBlock(key.x + 1, key.y, key.z),
                    instance.getBlock(key.x - 1, key.y, key.z),
                    instance.getBlock(key.x, key.y + 1, key.z),
                    instance.getBlock(key.x, key.y - 1, key.z),
                    instance.getBlock(key.x, key.y, key.z + 1),
                    instance.getBlock(key.x, key.y, key.z - 1),
                ).any { it.name() == "minecraft:water" }

                if (!hasWater) {
                    val dead = Block.fromKey(deadName)
                    if (dead != null) {
                        instance.setBlock(key.x, key.y, key.z, dead)
                    }
                    iterator.remove()
                }
            }
        }.repeat(TaskSchedule.tick(100)).schedule()
    }

    override fun onDisable() {
        coralBlocks.clear()
        super.onDisable()
    }
}
