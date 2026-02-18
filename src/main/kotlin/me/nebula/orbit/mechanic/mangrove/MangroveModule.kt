package me.nebula.orbit.mechanic.mangrove

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import kotlin.random.Random

private const val MAX_AGE = 4

class MangroveModule : OrbitModule("mangrove") {

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
        if (block.name() != "minecraft:mangrove_propagule") return

        val hanging = block.getProperty("hanging")
        if (hanging != "true") return

        val currentAge = block.getProperty("age")?.toIntOrNull() ?: return
        if (currentAge >= MAX_AGE) return

        if (Random.nextFloat() > 0.10f) return

        instance.setBlock(x, y, z, block.withProperty("age", (currentAge + 1).toString()))
    }
}
