package me.nebula.orbit.mechanic.guardianbeam

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val BEAM_START_TAG = Tag.Long("mechanic:guardian:beam_start").defaultValue(0L)
private val BEAM_TARGET_TAG = Tag.Integer("mechanic:guardian:beam_target").defaultValue(-1)

private const val SEARCH_RANGE = 16.0
private const val CHARGE_TIME_MS = 2000L
private const val BEAM_DAMAGE = 6f

class GuardianBeamModule : OrbitModule("guardian-beam") {

    private var tickTask: Task? = null
    private val trackedGuardians: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedGuardians.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedGuardians.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.GUARDIAN && entity.entityType != EntityType.ELDER_GUARDIAN) return@entityLoop
                trackedGuardians.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedGuardians.forEach { guardian ->
            if (guardian.isRemoved) return@forEach
            val instance = guardian.instance ?: return@forEach

            val targetId = guardian.getTag(BEAM_TARGET_TAG)
            if (targetId >= 0) {
                val target = instance.players.firstOrNull { it.entityId == targetId && !it.isDead && !it.isRemoved }
                if (target == null) {
                    guardian.setTag(BEAM_TARGET_TAG, -1)
                    guardian.setTag(BEAM_START_TAG, 0L)
                    return@forEach
                }

                if (guardian.position.distanceSquared(target.position) > SEARCH_RANGE * SEARCH_RANGE) {
                    guardian.setTag(BEAM_TARGET_TAG, -1)
                    guardian.setTag(BEAM_START_TAG, 0L)
                    return@forEach
                }

                val beamStart = guardian.getTag(BEAM_START_TAG)
                if (now - beamStart >= CHARGE_TIME_MS) {
                    target.damage(DamageType.MOB_ATTACK, BEAM_DAMAGE)
                    guardian.setTag(BEAM_TARGET_TAG, -1)
                    guardian.setTag(BEAM_START_TAG, 0L)
                }
            } else {
                val target = findNearestPlayer(guardian, instance) ?: return@forEach
                guardian.setTag(BEAM_TARGET_TAG, target.entityId)
                guardian.setTag(BEAM_START_TAG, now)
            }
        }
    }

    private fun findNearestPlayer(guardian: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = SEARCH_RANGE * SEARCH_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = guardian.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }
}
