package me.nebula.orbit.utils.entityleash

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

class LeashHandle @PublishedApi internal constructor(
    private val entityId: Int,
    private val task: Task,
) {

    fun release() {
        EntityLeashManager.unleash(entityId)
    }
}

private data class LeashEntry(
    val entity: Entity,
    val holder: Entity,
    val maxDistance: Double,
    val task: Task,
)

object EntityLeashManager {

    private val leashes = ConcurrentHashMap<Int, LeashEntry>()

    fun leash(entity: Entity, holder: Entity, maxDistance: Double = 10.0): LeashHandle {
        require(entity !== holder) { "Cannot leash an entity to itself" }
        require(maxDistance > 0.0) { "Max distance must be positive" }
        unleash(entity)
        val maxDistSq = maxDistance * maxDistance
        val task = MinecraftServer.getSchedulerManager()
            .buildTask {
                if (entity.isRemoved || holder.isRemoved) {
                    unleash(entity)
                    return@buildTask
                }
                val distSq = entity.position.distanceSquared(holder.position)
                if (distSq > maxDistSq) {
                    val dir = Vec(
                        holder.position.x() - entity.position.x(),
                        holder.position.y() - entity.position.y(),
                        holder.position.z() - entity.position.z(),
                    ).normalize().mul(0.4)
                    entity.velocity = entity.velocity.add(dir)
                }
            }
            .repeat(TaskSchedule.tick(2))
            .schedule()

        val entry = LeashEntry(entity, holder, maxDistance, task)
        leashes[System.identityHashCode(entity)] = entry
        return LeashHandle(System.identityHashCode(entity), task)
    }

    fun unleash(entity: Entity) {
        unleash(System.identityHashCode(entity))
    }

    internal fun unleash(entityId: Int) {
        leashes.remove(entityId)?.task?.cancel()
    }

    fun isLeashed(entity: Entity): Boolean =
        leashes.containsKey(System.identityHashCode(entity))

    fun holder(entity: Entity): Entity? =
        leashes[System.identityHashCode(entity)]?.holder
}

fun Entity.leashTo(holder: Entity, maxDistance: Double = 10.0): LeashHandle =
    EntityLeashManager.leash(this, holder, maxDistance)

fun Entity.unleash() = EntityLeashManager.unleash(this)

fun Entity.isLeashed(): Boolean = EntityLeashManager.isLeashed(this)

fun Entity.leashHolder(): Entity? = EntityLeashManager.holder(this)
