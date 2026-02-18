package me.nebula.orbit.mechanic.hoglinbehavior

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

private val LAST_ATTACK_TAG = Tag.Long("mechanic:hoglin:last_attack").defaultValue(0L)

private const val ATTACK_RANGE = 8.0
private const val MELEE_RANGE = 2.5
private const val FLEE_RANGE = 7
private const val ATTACK_COOLDOWN_MS = 2000L
private const val ATTACK_DAMAGE = 6f
private const val FLEE_SPEED = 18.0
private const val SCAN_INTERVAL_TICKS = 10

class HoglinBehaviorModule : OrbitModule("hoglin-behavior") {

    private var tickTask: Task? = null
    private val trackedHoglins: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        trackedHoglins.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedHoglins.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.HOGLIN) return@entityLoop
                trackedHoglins.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedHoglins.forEach { hoglin ->
            if (hoglin.isRemoved) return@forEach
            val instance = hoglin.instance ?: return@forEach

            if (fleeFromWarpedFungus(hoglin, instance)) return@forEach

            val lastAttack = hoglin.getTag(LAST_ATTACK_TAG)
            if (now - lastAttack < ATTACK_COOLDOWN_MS) return@forEach

            val target = findNearestPlayer(hoglin, instance) ?: return@forEach
            if (hoglin.position.distance(target.position) <= MELEE_RANGE) {
                hoglin.setTag(LAST_ATTACK_TAG, now)
                target.damage(DamageType.MOB_ATTACK, ATTACK_DAMAGE)
            }
        }
    }

    private fun fleeFromWarpedFungus(hoglin: Entity, instance: Instance): Boolean {
        val pos = hoglin.position
        for (dx in -FLEE_RANGE..FLEE_RANGE) {
            for (dy in -2..2) {
                for (dz in -FLEE_RANGE..FLEE_RANGE) {
                    val block = instance.getBlock(pos.blockX() + dx, pos.blockY() + dy, pos.blockZ() + dz)
                    if (block.name() == "minecraft:warped_fungus") {
                        val direction = pos.asVec()
                            .sub(net.minestom.server.coordinate.Vec(
                                (pos.blockX() + dx).toDouble(),
                                pos.y(),
                                (pos.blockZ() + dz).toDouble(),
                            ))
                        if (direction.length() > 0.1) {
                            hoglin.velocity = direction.normalize().mul(FLEE_SPEED)
                        }
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun findNearestPlayer(hoglin: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = ATTACK_RANGE * ATTACK_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = hoglin.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }
}
