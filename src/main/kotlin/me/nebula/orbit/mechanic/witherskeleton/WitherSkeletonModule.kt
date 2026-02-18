package me.nebula.orbit.mechanic.witherskeleton

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.ai.goal.MeleeAttackGoal
import net.minestom.server.entity.ai.goal.RandomStrollGoal
import net.minestom.server.entity.ai.target.ClosestEntityTarget
import net.minestom.server.entity.ai.target.LastEntityDamagerTarget
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val AI_CONFIGURED_TAG = Tag.Boolean("mechanic:wither_skeleton:ai_configured").defaultValue(false)
private val LAST_ATTACK_TAG = Tag.Long("mechanic:wither_skeleton:last_attack").defaultValue(0L)

private const val ATTACK_RANGE = 16.0
private const val MELEE_RANGE = 2.5
private const val ATTACK_COOLDOWN_MS = 1000L
private const val WITHER_DAMAGE = 5f
private const val WITHER_DURATION_TICKS = 200
private const val SKULL_DROP_CHANCE = 0.025

class WitherSkeletonModule : OrbitModule("wither-skeleton") {

    private var tickTask: Task? = null
    private val trackedSkeletons: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        trackedSkeletons.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedSkeletons.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.WITHER_SKELETON) return@entityLoop
                if (entity !is EntityCreature) return@entityLoop

                if (!entity.getTag(AI_CONFIGURED_TAG)) {
                    configureAI(entity)
                    entity.setTag(AI_CONFIGURED_TAG, true)
                }
                trackedSkeletons.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedSkeletons.toList().forEach { skeleton ->
            if (skeleton.isRemoved) return@forEach
            val instance = skeleton.instance ?: return@forEach

            if ((skeleton as? LivingEntity)?.isDead == true) {
                handleDeath(skeleton, instance)
                trackedSkeletons.remove(skeleton)
                return@forEach
            }

            val lastAttack = skeleton.getTag(LAST_ATTACK_TAG)
            if (now - lastAttack < ATTACK_COOLDOWN_MS) return@forEach

            val target = findNearestPlayer(skeleton, instance) ?: return@forEach
            if (skeleton.position.distance(target.position) <= MELEE_RANGE) {
                skeleton.setTag(LAST_ATTACK_TAG, now)
                target.damage(DamageType.MOB_ATTACK, WITHER_DAMAGE)
                target.addEffect(Potion(PotionEffect.WITHER, 0, WITHER_DURATION_TICKS))
            }
        }
    }

    private fun configureAI(skeleton: EntityCreature) {
        skeleton.addAIGroup(
            listOf(
                MeleeAttackGoal(skeleton, 1.6, Duration.ofMillis(ATTACK_COOLDOWN_MS)),
                RandomStrollGoal(skeleton, 5),
            ),
            listOf(
                ClosestEntityTarget(skeleton, ATTACK_RANGE.toFloat(), Player::class.java),
                LastEntityDamagerTarget(skeleton, ATTACK_RANGE.toFloat()),
            ),
        )
    }

    private fun findNearestPlayer(skeleton: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = ATTACK_RANGE * ATTACK_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = skeleton.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }

    private fun handleDeath(skeleton: Entity, instance: Instance) {
        if (Random.nextDouble() < SKULL_DROP_CHANCE) {
            val pos = skeleton.position
            val skull = ItemEntity(ItemStack.of(Material.WITHER_SKELETON_SKULL))
            skull.setPickupDelay(Duration.ofMillis(500))
            skull.setInstance(instance, Pos(pos.x(), pos.y() + 0.5, pos.z()))

            skull.scheduler().buildTask { skull.remove() }
                .delay(TaskSchedule.minutes(5))
                .schedule()
        }
    }
}
