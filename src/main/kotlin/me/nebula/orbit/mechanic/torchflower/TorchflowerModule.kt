package me.nebula.orbit.mechanic.torchflower

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import kotlin.random.Random

private const val MAX_AGE = 2

class TorchflowerModule : OrbitModule("torchflower") {

    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.seconds(5))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        super.onDisable()
    }

    private fun tick() {
        MinecraftServer.getInstanceManager().instances.forEach { instance ->
            instance.players.forEach { player ->
                val pos = player.position
                val radius = 8
                repeat(3) {
                    val x = pos.blockX() + Random.nextInt(-radius, radius + 1)
                    val y = pos.blockY() + Random.nextInt(-2, 3)
                    val z = pos.blockZ() + Random.nextInt(-radius, radius + 1)
                    tryGrow(instance, x, y, z)
                }
            }
        }
    }

    private fun tryGrow(instance: Instance, x: Int, y: Int, z: Int) {
        val block = instance.getBlock(x, y, z)
        if (block.name() != "minecraft:torchflower_crop") return

        val currentAge = block.getProperty("age")?.toIntOrNull() ?: return
        if (currentAge >= MAX_AGE) return

        if (Random.nextFloat() > 0.10f) return

        val below = instance.getBlock(x, y - 1, z)
        if (below.name() != "minecraft:farmland") return

        val newAge = currentAge + 1
        if (newAge > MAX_AGE) return

        if (newAge == MAX_AGE) {
            val torchflower = Block.fromKey("minecraft:torchflower") ?: return
            instance.setBlock(x, y, z, torchflower)
        } else {
            instance.setBlock(x, y, z, block.withProperty("age", newAge.toString()))
        }
    }
}
