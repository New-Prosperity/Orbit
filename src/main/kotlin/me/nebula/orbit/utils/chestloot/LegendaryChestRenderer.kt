package me.nebula.orbit.utils.chestloot

import me.nebula.orbit.utils.particle.spawnParticleAt
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.particle.Particle
import net.minestom.server.timer.Task

class LegendaryChestRenderer(
    private val instance: Instance,
    private val intervalTicks: Int = 20,
    private val beamHeight: Double = 1.6,
) {

    private var task: Task? = null

    fun install() {
        if (task != null) return
        task = repeat(intervalTicks) { tick() }
    }

    fun uninstall() {
        task?.cancel()
        task = null
    }

    private fun tick() {
        for ((x, y, z) in ChestLootManager.legendaryChestPositions()) {
            val base = Pos(x + 0.5, y + 1.0, z + 0.5)
            instance.spawnParticleAt(Particle.END_ROD, base, count = 3, spread = 0.1f, speed = 0.01f)
            instance.spawnParticleAt(Particle.HAPPY_VILLAGER, base.add(0.0, beamHeight * 0.5, 0.0), count = 2, spread = 0.25f)
            instance.spawnParticleAt(Particle.FIREWORK, base.add(0.0, beamHeight, 0.0), count = 1, spread = 0f, speed = 0f)
        }
    }
}
