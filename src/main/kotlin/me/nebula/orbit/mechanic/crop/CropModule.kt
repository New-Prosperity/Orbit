package me.nebula.orbit.mechanic.crop

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import kotlin.random.Random

private val CROP_BLOCKS = setOf(
    "minecraft:wheat",
    "minecraft:carrots",
    "minecraft:potatoes",
    "minecraft:beetroots",
    "minecraft:melon_stem",
    "minecraft:pumpkin_stem",
    "minecraft:nether_wart",
)

private val MAX_AGE = mapOf(
    "minecraft:wheat" to 7,
    "minecraft:carrots" to 7,
    "minecraft:potatoes" to 7,
    "minecraft:beetroots" to 3,
    "minecraft:melon_stem" to 7,
    "minecraft:pumpkin_stem" to 7,
    "minecraft:nether_wart" to 3,
)

class CropModule : OrbitModule("crop") {

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
                    tryGrowCrop(instance, x, y, z)
                }
            }
        }
    }

    private fun tryGrowCrop(instance: Instance, x: Int, y: Int, z: Int) {
        val block = instance.getBlock(x, y, z)
        val name = block.name()
        if (name !in CROP_BLOCKS) return

        val maxAge = MAX_AGE[name] ?: return
        val currentAge = block.getProperty("age")?.toIntOrNull() ?: return
        if (currentAge >= maxAge) return

        if (Random.nextFloat() > 0.15f) return

        val below = instance.getBlock(x, y - 1, z)
        if (name == "minecraft:nether_wart") {
            if (below.name() != "minecraft:soul_sand") return
        } else {
            if (below.name() != "minecraft:farmland") return
        }

        val grown = block.withProperty("age", (currentAge + 1).toString())
        instance.setBlock(x, y, z, grown)
    }
}
