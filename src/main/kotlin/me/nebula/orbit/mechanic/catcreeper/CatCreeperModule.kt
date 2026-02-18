package me.nebula.orbit.mechanic.catcreeper

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private const val FLEE_RANGE = 6.0
private const val FLEE_SPEED = 18.0

private val CAT_TYPES = setOf(EntityType.CAT, EntityType.OCELOT)

class CatCreeperModule : OrbitModule("cat-creeper") {

    private var tickTask: Task? = null
    private val trackedCreepers: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(10))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedCreepers.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedCreepers.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.CREEPER) return@entityLoop
                trackedCreepers.add(entity)
            }
        }

        trackedCreepers.forEach { creeper ->
            if (creeper.isRemoved) return@forEach
            val instance = creeper.instance ?: return@forEach

            val nearestCat = findNearestCat(creeper, instance) ?: return@forEach
            val direction = creeper.position.asVec()
                .sub(nearestCat.position.asVec())

            if (direction.length() < 0.1) return@forEach

            creeper.velocity = direction.normalize().mul(FLEE_SPEED)
        }
    }

    private fun findNearestCat(creeper: Entity, instance: Instance): Entity? {
        var nearest: Entity? = null
        var nearestDist = FLEE_RANGE * FLEE_RANGE

        instance.entities.forEach { entity ->
            if (entity.entityType !in CAT_TYPES) return@forEach
            val dist = creeper.position.distanceSquared(entity.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = entity
            }
        }
        return nearest
    }
}
