package me.nebula.orbit.utils.modelengine.vfx

import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object VFXRegistry {

    private val effects = ConcurrentHashMap<Int, VFX>()
    private var tickTask: Task? = null

    fun register(vfx: VFX) {
        effects[vfx.entityId] = vfx
        ensureInstalled()
    }

    fun unregister(vfx: VFX) {
        effects.remove(vfx.entityId)
        vfx.remove()
    }

    fun evictPlayer(uuid: UUID) {
        effects.values.forEach { it.evictViewer(uuid) }
    }

    fun all(): Collection<VFX> = effects.values

    fun install() {
        if (tickTask != null) return
        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(1))
            .schedule()
    }

    fun uninstall() {
        tickTask?.cancel()
        tickTask = null
        effects.values.forEach { it.remove() }
        effects.clear()
    }

    private fun ensureInstalled() {
        if (tickTask == null) install()
    }

    private fun tick() {
        val iterator = effects.entries.iterator()
        while (iterator.hasNext()) {
            val (_, vfx) = iterator.next()
            if (vfx.removed) {
                iterator.remove()
                continue
            }
            vfx.age++
            if (vfx.lifetime > 0 && vfx.age >= vfx.lifetime) {
                vfx.remove()
                iterator.remove()
            }
        }
    }
}
