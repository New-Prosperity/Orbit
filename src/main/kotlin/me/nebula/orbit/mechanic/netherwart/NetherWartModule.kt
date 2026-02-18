package me.nebula.orbit.mechanic.netherwart

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import kotlin.random.Random

class NetherWartModule : OrbitModule("nether-wart") {

    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:nether_wart") return@addListener
            val instance = event.player.instance ?: return@addListener
            val below = instance.getBlock(event.blockPosition.add(0, -1, 0))
            if (below.name() != "minecraft:soul_sand") {
                event.isCancelled = true
            }
        }

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
        if (block.name() != "minecraft:nether_wart") return

        val currentAge = block.getProperty("age")?.toIntOrNull() ?: return
        if (currentAge >= 3) return

        if (Random.nextFloat() > 0.10f) return

        val below = instance.getBlock(x, y - 1, z)
        if (below.name() != "minecraft:soul_sand") return

        instance.setBlock(x, y, z, block.withProperty("age", (currentAge + 1).toString()))
    }
}
