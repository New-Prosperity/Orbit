package me.nebula.orbit.mechanic.amethystgrowth

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import kotlin.random.Random

private val GROWTH_STAGES = mapOf(
    Block.SMALL_AMETHYST_BUD to Block.MEDIUM_AMETHYST_BUD,
    Block.MEDIUM_AMETHYST_BUD to Block.LARGE_AMETHYST_BUD,
    Block.LARGE_AMETHYST_BUD to Block.AMETHYST_CLUSTER,
)

private val FACE_OFFSETS = listOf(
    Vec(1.0, 0.0, 0.0),
    Vec(-1.0, 0.0, 0.0),
    Vec(0.0, 1.0, 0.0),
    Vec(0.0, -1.0, 0.0),
    Vec(0.0, 0.0, 1.0),
    Vec(0.0, 0.0, -1.0),
)

class AmethystGrowthModule : OrbitModule("amethyst-growth") {

    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()
        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.seconds(10))
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
                val radius = 16
                repeat(4) {
                    val x = pos.blockX() + Random.nextInt(-radius, radius + 1)
                    val y = pos.blockY() + Random.nextInt(-radius, radius + 1)
                    val z = pos.blockZ() + Random.nextInt(-radius, radius + 1)
                    processBlock(instance, x, y, z)
                }
            }
        }
    }

    private fun processBlock(instance: Instance, x: Int, y: Int, z: Int) {
        val block = instance.getBlock(x, y, z)
        if (block.name() != "minecraft:budding_amethyst") return

        if (Random.nextFloat() > 0.2f) return

        val faces = FACE_OFFSETS.shuffled()
        for (offset in faces) {
            val adjX = x + offset.x().toInt()
            val adjY = y + offset.y().toInt()
            val adjZ = z + offset.z().toInt()
            val adjacent = instance.getBlock(adjX, adjY, adjZ)

            if (adjacent == Block.AIR) {
                instance.setBlock(adjX, adjY, adjZ, Block.SMALL_AMETHYST_BUD)
                return
            }

            val grown = GROWTH_STAGES.entries
                .firstOrNull { (stage, _) -> adjacent.compare(stage) }
                ?.value ?: continue
            instance.setBlock(adjX, adjY, adjZ, grown)
            return
        }
    }
}
