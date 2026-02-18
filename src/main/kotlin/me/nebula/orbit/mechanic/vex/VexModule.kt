package me.nebula.orbit.mechanic.vex

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val TARGET_TAG = Tag.Integer("mechanic:vex:target_id").defaultValue(-1)
private val SPAWN_TIME_TAG = Tag.Long("mechanic:vex:spawn_time").defaultValue(0L)
private val LAST_ATTACK_TAG = Tag.Long("mechanic:vex:last_attack").defaultValue(0L)

private const val LIFETIME_MS = 30000L
private const val ATTACK_RANGE = 1.5
private const val SEARCH_RANGE = 16.0
private const val VEX_DAMAGE = 9f
private const val ATTACK_COOLDOWN_MS = 1500L
private const val FLY_SPEED = 18.0

class VexModule : OrbitModule("vex") {

    private var tickTask: Task? = null
    private val trackedVexes: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(5))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedVexes.forEach { it.remove() }
        trackedVexes.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedVexes.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.VEX) return@entityLoop
                if (entity in trackedVexes) return@entityLoop
                entity.setNoGravity(true)
                entity.setTag(SPAWN_TIME_TAG, System.currentTimeMillis())
                trackedVexes.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedVexes.toList().forEach { vex ->
            if (vex.isRemoved) return@forEach

            val spawnTime = vex.getTag(SPAWN_TIME_TAG)
            if (now - spawnTime >= LIFETIME_MS) {
                vex.remove()
                trackedVexes.remove(vex)
                return@forEach
            }

            val instance = vex.instance ?: return@forEach
            updateVex(vex, instance, now)
        }
    }

    private fun updateVex(vex: Entity, instance: Instance, now: Long) {
        var targetId = vex.getTag(TARGET_TAG)
        var target: Player? = null

        if (targetId >= 0) {
            target = instance.players.firstOrNull { it.entityId == targetId && !it.isDead && !it.isRemoved }
            if (target == null) {
                vex.setTag(TARGET_TAG, -1)
                targetId = -1
            }
        }

        if (targetId < 0) {
            target = findNearestPlayer(vex, instance)
            if (target != null) vex.setTag(TARGET_TAG, target.entityId)
        }

        if (target == null) return

        val direction = target.position.asVec()
            .add(0.0, target.eyeHeight, 0.0)
            .sub(vex.position.asVec())
        val distance = direction.length()

        if (distance > 0.1) {
            vex.velocity = direction.normalize().mul(FLY_SPEED)
        }

        if (distance <= ATTACK_RANGE) {
            val lastAttack = vex.getTag(LAST_ATTACK_TAG)
            if (now - lastAttack >= ATTACK_COOLDOWN_MS) {
                target.damage(DamageType.MOB_ATTACK, VEX_DAMAGE)
                vex.setTag(LAST_ATTACK_TAG, now)
            }
        }
    }

    private fun findNearestPlayer(vex: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = SEARCH_RANGE * SEARCH_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = vex.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }
}
