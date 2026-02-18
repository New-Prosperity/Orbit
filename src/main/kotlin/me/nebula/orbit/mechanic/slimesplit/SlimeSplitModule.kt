package me.nebula.orbit.mechanic.slimesplit

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.ai.goal.MeleeAttackGoal
import net.minestom.server.entity.ai.goal.RandomStrollGoal
import net.minestom.server.entity.ai.target.ClosestEntityTarget
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val SIZE_TAG = Tag.Integer("mechanic:slime_split:size").defaultValue(3)

private val SPLITTABLE_TYPES = setOf(EntityType.SLIME, EntityType.MAGMA_CUBE)

class SlimeSplitModule : OrbitModule("slime-split") {

    private var tickTask: Task? = null
    private val trackedEntities: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        trackedEntities.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedEntities.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType !in SPLITTABLE_TYPES) return@entityLoop
                trackedEntities.add(entity)
            }
        }

        trackedEntities.toList().forEach { entity ->
            if (entity.isRemoved) return@forEach
            val living = entity as? LivingEntity ?: return@forEach
            if (!living.isDead) return@forEach

            val size = entity.getTag(SIZE_TAG)
            if (size <= 1) {
                trackedEntities.remove(entity)
                return@forEach
            }

            val instance = entity.instance ?: return@forEach
            val pos = entity.position
            val type = entity.entityType

            trackedEntities.remove(entity)
            spawnSmaller(instance, pos, type, size - 1)
            spawnSmaller(instance, pos, type, size - 1)
        }
    }

    private fun spawnSmaller(instance: Instance, pos: Pos, type: EntityType, newSize: Int) {
        val creature = EntityCreature(type)
        creature.setTag(SIZE_TAG, newSize)

        creature.addAIGroup(
            listOf(
                MeleeAttackGoal(creature, 1.4, Duration.ofMillis(1000)),
                RandomStrollGoal(creature, 5),
            ),
            listOf(
                ClosestEntityTarget(creature, 16f, Player::class.java),
            ),
        )

        val offsetX = (Math.random() - 0.5) * 0.5
        val offsetZ = (Math.random() - 0.5) * 0.5
        creature.setInstance(instance, Pos(pos.x() + offsetX, pos.y(), pos.z() + offsetZ))
        trackedEntities.add(creature)
    }
}
