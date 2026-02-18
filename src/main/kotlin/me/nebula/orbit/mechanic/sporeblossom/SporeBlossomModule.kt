package me.nebula.orbit.mechanic.sporeblossom

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.utils.blockindex.BlockPositionIndex
import me.nebula.orbit.utils.particle.particleEffect
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.particle.Particle
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import kotlin.random.Random

private const val SCAN_RADIUS = 24

class SporeBlossomModule : OrbitModule("spore-blossom") {

    private val index = BlockPositionIndex(setOf("minecraft:spore_blossom"), eventNode).install()
    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        index.instancePositions.cleanOnInstanceRemove { it }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(10))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        index.clear()
        super.onDisable()
    }

    private fun tick() {
        val particle = runCatching { Particle.FALLING_SPORE_BLOSSOM }.getOrElse { Particle.POOF }
        val effect = particleEffect(particle)

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            val nearby = index.positionsNear(instance, player.position.asVec(), SCAN_RADIUS.toDouble())

            for (vec in nearby) {
                repeat(3) {
                    val offsetX = Random.nextDouble(-0.5, 0.5)
                    val offsetZ = Random.nextDouble(-0.5, 0.5)
                    val offsetY = Random.nextDouble(-1.5, -0.2)
                    effect.spawn(instance, Pos(vec.x() + 0.5 + offsetX, vec.y() + offsetY, vec.z() + 0.5 + offsetZ))
                }
            }
        }
    }
}
