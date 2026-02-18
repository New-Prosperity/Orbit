package me.nebula.orbit.mechanic.daynight

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

class DayNightModule : OrbitModule("day-night") {

    private var syncTask: Task? = null

    override fun onEnable() {
        super.onEnable()
        applyTimeRate(1)
        syncTask = MinecraftServer.getSchedulerManager()
            .buildTask { applyTimeRate(1) }
            .repeat(TaskSchedule.seconds(60))
            .schedule()
    }

    override fun onDisable() {
        syncTask?.cancel()
        syncTask = null
        applyTimeRate(0)
        super.onDisable()
    }

    private fun applyTimeRate(rate: Int) {
        MinecraftServer.getInstanceManager().instances.forEach { it.timeRate = rate }
    }
}
