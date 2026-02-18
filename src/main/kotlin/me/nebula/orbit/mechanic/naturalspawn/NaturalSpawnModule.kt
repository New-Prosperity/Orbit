package me.nebula.orbit.mechanic.naturalspawn

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ai.goal.MeleeAttackGoal
import net.minestom.server.entity.ai.goal.RandomStrollGoal
import net.minestom.server.entity.ai.target.ClosestEntityTarget
import net.minestom.server.entity.ai.target.LastEntityDamagerTarget
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val HOSTILE_TYPES = listOf(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER)
private val PASSIVE_TYPES = listOf(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN)

class NaturalSpawnModule : OrbitModule("natural-spawn") {

    private var tickTask: Task? = null
    private val spawnedEntities: MutableSet<Entity> = ConcurrentHashMap.newKeySet()
    private val maxEntities = 100

    override fun onEnable() {
        super.onEnable()
        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.seconds(20))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        spawnedEntities.forEach { it.remove() }
        spawnedEntities.clear()
        super.onDisable()
    }

    private fun tick() {
        spawnedEntities.removeIf { it.isRemoved }
        if (spawnedEntities.size >= maxEntities) return

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            repeat(4) { attemptSpawn(instance, player.position) }
        }
    }

    private fun attemptSpawn(instance: Instance, center: Pos) {
        if (spawnedEntities.size >= maxEntities) return

        val x = center.blockX() + Random.nextInt(-48, 49)
        val z = center.blockZ() + Random.nextInt(-48, 49)
        val y = findSurface(instance, x, z) ?: return

        if (instance.getBlock(x, y + 1, z) != Block.AIR) return
        if (instance.getBlock(x, y + 2, z) != Block.AIR) return

        val time = instance.time % 24000
        val isNight = time in 13000..23000
        val isUnderground = y < center.blockY() - 10

        val types = if (isNight || isUnderground) HOSTILE_TYPES else PASSIVE_TYPES
        val entityType = types.random()

        val creature = EntityCreature(entityType)
        setupAI(creature, entityType)
        creature.setInstance(instance, Pos(x + 0.5, y + 1.0, z + 0.5))
        spawnedEntities.add(creature)

        creature.scheduler().buildTask {
            creature.remove()
            spawnedEntities.remove(creature)
        }.delay(TaskSchedule.minutes(5)).schedule()
    }

    private fun findSurface(instance: Instance, x: Int, z: Int): Int? {
        for (y in 255 downTo 1) {
            if (instance.getBlock(x, y, z) != Block.AIR) return y
        }
        return null
    }

    private fun setupAI(creature: EntityCreature, type: EntityType) {
        if (type in HOSTILE_TYPES) {
            creature.addAIGroup(
                listOf(
                    MeleeAttackGoal(creature, 1.6, Duration.ofMillis(1000)),
                    RandomStrollGoal(creature, 5),
                ),
                listOf(
                    ClosestEntityTarget(creature, 32f, net.minestom.server.entity.Player::class.java),
                    LastEntityDamagerTarget(creature, 16f),
                ),
            )
        } else {
            creature.addAIGroup(
                listOf(RandomStrollGoal(creature, 5)),
                emptyList(),
            )
        }
    }
}
