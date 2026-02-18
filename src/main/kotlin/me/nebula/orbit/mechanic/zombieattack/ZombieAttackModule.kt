package me.nebula.orbit.mechanic.zombieattack

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.ai.goal.MeleeAttackGoal
import net.minestom.server.entity.ai.goal.RandomStrollGoal
import net.minestom.server.entity.ai.target.ClosestEntityTarget
import net.minestom.server.entity.ai.target.LastEntityDamagerTarget
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val AI_CONFIGURED_TAG = Tag.Boolean("mechanic:zombie_attack:ai_configured").defaultValue(false)
private val LAST_ATTACK_TAG = Tag.Long("mechanic:zombie_attack:last_attack").defaultValue(0L)

private const val ATTACK_RANGE = 16.0
private const val MELEE_RANGE = 2.0
private const val ATTACK_COOLDOWN_MS = 1000L
private const val ZOMBIE_DAMAGE = 3f

private val ZOMBIE_TYPES = setOf(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK)

class ZombieAttackModule : OrbitModule("zombie-attack") {

    private var tickTask: Task? = null
    private val trackedZombies: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        trackedZombies.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedZombies.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType !in ZOMBIE_TYPES) return@entityLoop
                if (entity !is EntityCreature) return@entityLoop

                if (!entity.getTag(AI_CONFIGURED_TAG)) {
                    configureAI(entity)
                    entity.setTag(AI_CONFIGURED_TAG, true)
                }
                trackedZombies.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedZombies.forEach { zombie ->
            if (zombie.isRemoved) return@forEach
            val lastAttack = zombie.getTag(LAST_ATTACK_TAG)
            if (now - lastAttack < ATTACK_COOLDOWN_MS) return@forEach

            val instance = zombie.instance ?: return@forEach
            val target = findNearestPlayer(zombie, instance) ?: return@forEach

            if (zombie.position.distance(target.position) <= MELEE_RANGE) {
                zombie.setTag(LAST_ATTACK_TAG, now)
                target.damage(DamageType.MOB_ATTACK, ZOMBIE_DAMAGE)
            }
        }
    }

    private fun configureAI(zombie: EntityCreature) {
        val speed = if (zombie.entityType == EntityType.ZOMBIE && zombie.entityMeta.let { false }) 2.0 else 1.6
        zombie.addAIGroup(
            listOf(
                MeleeAttackGoal(zombie, speed, Duration.ofMillis(ATTACK_COOLDOWN_MS)),
                RandomStrollGoal(zombie, 5),
            ),
            listOf(
                ClosestEntityTarget(zombie, ATTACK_RANGE.toFloat(), Player::class.java),
                LastEntityDamagerTarget(zombie, ATTACK_RANGE.toFloat()),
            ),
        )
    }

    private fun findNearestPlayer(zombie: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = ATTACK_RANGE * ATTACK_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = zombie.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }
}
