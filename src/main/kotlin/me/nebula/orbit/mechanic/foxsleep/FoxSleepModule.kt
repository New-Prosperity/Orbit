package me.nebula.orbit.mechanic.foxsleep

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.animal.FoxMeta
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val SLEEPING_TAG = Tag.Boolean("mechanic:fox_sleep:sleeping").defaultValue(false)

class FoxSleepModule : OrbitModule("fox-sleep") {

    private var tickTask: Task? = null
    private val trackedFoxes: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(40))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedFoxes.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedFoxes.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.FOX) return@entityLoop
                trackedFoxes.add(entity)
            }
        }

        trackedFoxes.forEach { fox ->
            if (fox.isRemoved) return@forEach
            val instance = fox.instance ?: return@forEach
            val time = instance.time % 24000
            val isDaytime = time in 0..12000
            val isSleeping = fox.getTag(SLEEPING_TAG)
            val meta = fox.entityMeta as? FoxMeta ?: return@forEach

            if (isDaytime && !isSleeping) {
                meta.isSitting = true
                fox.setTag(SLEEPING_TAG, true)
            } else if (!isDaytime && isSleeping) {
                meta.isSitting = false
                fox.setTag(SLEEPING_TAG, false)
            }
        }
    }
}
