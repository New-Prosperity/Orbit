package me.nebula.orbit.mechanic.polarbear

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val BABY_TAG = Tag.Boolean("mechanic:polar_bear:is_baby").defaultValue(false)
private val AGGRO_TARGET_TAG = Tag.Integer("mechanic:polar_bear:aggro_target").defaultValue(-1)
private val LAST_ATTACK_TAG = Tag.Long("mechanic:polar_bear:last_attack").defaultValue(0L)

private const val AGGRO_RANGE = 12.0
private const val MELEE_RANGE = 2.5
private const val ATTACK_DAMAGE = 6f
private const val ATTACK_COOLDOWN_MS = 1500L
private const val CHASE_SPEED = 16.0
private const val SCAN_INTERVAL_TICKS = 10

class PolarBearModule : OrbitModule("polar-bear") {

    private var tickTask: Task? = null
    private val trackedBears: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val entity = event.entity
            if (entity.entityType != EntityType.POLAR_BEAR) return@addListener
            if (!entity.getTag(BABY_TAG)) return@addListener

            val attacker = event.damage.attacker ?: return@addListener
            val instance = entity.instance ?: return@addListener

            alertNearbyAdults(entity, attacker, instance)
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(SCAN_INTERVAL_TICKS))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedBears.clear()
        super.onDisable()
    }

    private fun alertNearbyAdults(baby: Entity, attacker: Entity, instance: Instance) {
        instance.getNearbyEntities(baby.position, AGGRO_RANGE).forEach { entity ->
            if (entity.entityType != EntityType.POLAR_BEAR) return@forEach
            if (entity.getTag(BABY_TAG)) return@forEach
            entity.setTag(AGGRO_TARGET_TAG, attacker.entityId)
            trackedBears.add(entity)
        }
    }

    private fun tick() {
        trackedBears.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.POLAR_BEAR) return@entityLoop
                trackedBears.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedBears.forEach { bear ->
            if (bear.isRemoved) return@forEach
            if (bear.getTag(BABY_TAG)) return@forEach

            val targetId = bear.getTag(AGGRO_TARGET_TAG)
            if (targetId < 0) return@forEach

            val instance = bear.instance ?: return@forEach
            val target = instance.entities.firstOrNull { it.entityId == targetId && !it.isRemoved }
            if (target == null) {
                bear.setTag(AGGRO_TARGET_TAG, -1)
                return@forEach
            }

            val distance = bear.position.distanceSquared(target.position)

            if (distance > AGGRO_RANGE * AGGRO_RANGE) {
                bear.setTag(AGGRO_TARGET_TAG, -1)
                return@forEach
            }

            if (distance <= MELEE_RANGE * MELEE_RANGE) {
                val lastAttack = bear.getTag(LAST_ATTACK_TAG)
                if (now - lastAttack >= ATTACK_COOLDOWN_MS) {
                    bear.setTag(LAST_ATTACK_TAG, now)
                    if (target is LivingEntity) {
                        target.damage(DamageType.MOB_ATTACK, ATTACK_DAMAGE)
                    }
                }
            } else {
                val direction = target.position.asVec().sub(bear.position.asVec())
                if (direction.length() > 0.1) {
                    bear.velocity = direction.normalize().mul(CHASE_SPEED)
                }
            }
        }
    }
}
