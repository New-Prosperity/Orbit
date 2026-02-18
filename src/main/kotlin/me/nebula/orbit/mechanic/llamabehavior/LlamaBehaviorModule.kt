package me.nebula.orbit.mechanic.llamabehavior

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val LAST_SPIT_TAG = Tag.Long("mechanic:llama:last_spit").defaultValue(0L)
private val LEASHED_TAG = Tag.Boolean("mechanic:llama:leashed").defaultValue(false)
private val CARAVAN_LEADER_TAG = Tag.Integer("mechanic:llama:caravan_leader").defaultValue(-1)

private const val SPIT_RANGE = 10.0
private const val SPIT_DAMAGE = 1f
private const val SPIT_COOLDOWN_MS = 3000L
private const val CARAVAN_FOLLOW_RANGE = 6.0
private const val CARAVAN_SPEED = 14.0
private const val SCAN_INTERVAL_TICKS = 20

private val LLAMA_TYPES = setOf(EntityType.LLAMA, EntityType.TRADER_LLAMA)

private val HOSTILE_TYPES = setOf(
    EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
    EntityType.CREEPER, EntityType.WITCH, EntityType.PILLAGER,
    EntityType.VINDICATOR, EntityType.RAVAGER,
)

class LlamaBehaviorModule : OrbitModule("llama-behavior") {

    private var tickTask: Task? = null
    private val trackedLlamas: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(SCAN_INTERVAL_TICKS))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedLlamas.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedLlamas.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType !in LLAMA_TYPES) return@entityLoop
                trackedLlamas.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedLlamas.forEach { llama ->
            if (llama.isRemoved) return@forEach
            val instance = llama.instance ?: return@forEach

            spitAtHostile(llama, instance, now)
            followCaravan(llama, instance)
        }
    }

    private fun spitAtHostile(llama: Entity, instance: Instance, now: Long) {
        val lastSpit = llama.getTag(LAST_SPIT_TAG)
        if (now - lastSpit < SPIT_COOLDOWN_MS) return

        val target = findNearestHostile(llama, instance) ?: return

        llama.setTag(LAST_SPIT_TAG, now)
        if (target is LivingEntity) {
            target.damage(DamageType.MOB_ATTACK, SPIT_DAMAGE)
        }
    }

    private fun followCaravan(llama: Entity, instance: Instance) {
        val leaderId = llama.getTag(CARAVAN_LEADER_TAG)
        if (leaderId < 0) {
            if (llama.getTag(LEASHED_TAG)) {
                val nearestLlama = findNearestUnleashedLlama(llama, instance)
                if (nearestLlama != null) {
                    nearestLlama.setTag(CARAVAN_LEADER_TAG, llama.entityId)
                }
            }
            return
        }

        val leader = instance.entities.firstOrNull { it.entityId == leaderId && !it.isRemoved } ?: run {
            llama.setTag(CARAVAN_LEADER_TAG, -1)
            return
        }

        val distance = llama.position.distanceSquared(leader.position)
        if (distance > CARAVAN_FOLLOW_RANGE * CARAVAN_FOLLOW_RANGE) {
            val direction = leader.position.asVec().sub(llama.position.asVec())
            if (direction.length() > 0.1) {
                llama.velocity = direction.normalize().mul(CARAVAN_SPEED)
            }
        }
    }

    private fun findNearestHostile(llama: Entity, instance: Instance): Entity? {
        var nearest: Entity? = null
        var nearestDist = SPIT_RANGE * SPIT_RANGE

        instance.entities.forEach { entity ->
            if (entity.entityType !in HOSTILE_TYPES) return@forEach
            val dist = llama.position.distanceSquared(entity.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = entity
            }
        }
        return nearest
    }

    private fun findNearestUnleashedLlama(leader: Entity, instance: Instance): Entity? {
        return instance.getNearbyEntities(leader.position, CARAVAN_FOLLOW_RANGE)
            .firstOrNull {
                it !== leader &&
                    it.entityType in LLAMA_TYPES &&
                    it.getTag(CARAVAN_LEADER_TAG) < 0 &&
                    !it.getTag(LEASHED_TAG)
            }
    }
}
