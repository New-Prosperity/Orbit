package me.nebula.orbit.mechanic.breeze

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import kotlin.math.cos
import kotlin.math.sin

private val BREEZE_TAG = Tag.Boolean("mechanic:breeze:tracked").defaultValue(false)
private val LAST_ATTACK_TAG = Tag.Long("mechanic:breeze:last_attack").defaultValue(0L)

private const val ATTACK_RANGE = 16.0
private const val ATTACK_COOLDOWN_MS = 3000L
private const val KNOCKBACK_RADIUS = 2.5
private const val KNOCKBACK_STRENGTH = 12.0
private const val PROJECTILE_SPEED = 30.0

class BreezeModule : OrbitModule("breeze") {

    private var tickTask: Task? = null

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
        super.onDisable()
    }

    private fun tick() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.BREEZE) return@entityLoop
                if (!entity.getTag(BREEZE_TAG)) {
                    entity.setTag(BREEZE_TAG, true)
                }

                val now = System.currentTimeMillis()
                val lastAttack = entity.getTag(LAST_ATTACK_TAG)
                if (now - lastAttack < ATTACK_COOLDOWN_MS) return@entityLoop

                val nearest = findNearestPlayer(entity) ?: return@entityLoop
                entity.setTag(LAST_ATTACK_TAG, now)
                launchWindCharge(entity, nearest)
            }
        }
    }

    private fun findNearestPlayer(breeze: Entity): Player? {
        val instance = breeze.instance ?: return null
        var nearest: Player? = null
        var nearestDist = ATTACK_RANGE * ATTACK_RANGE

        instance.players.forEach { player ->
            val dist = breeze.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }

    private fun launchWindCharge(breeze: Entity, target: Player) {
        val instance = breeze.instance ?: return
        val projectile = Entity(EntityType.WIND_CHARGE)
        projectile.setNoGravity(false)

        val direction = target.position.asVec().sub(breeze.position.asVec()).normalize()
        projectile.velocity = direction.mul(PROJECTILE_SPEED)

        projectile.setInstance(instance, breeze.position.add(0.0, 1.5, 0.0))

        projectile.scheduler().buildTask {
            checkProjectileCollision(projectile)
        }.repeat(TaskSchedule.tick(1)).schedule()

        projectile.scheduler().buildTask {
            projectile.remove()
        }.delay(TaskSchedule.seconds(10)).schedule()
    }

    private fun checkProjectileCollision(projectile: Entity) {
        if (projectile.isRemoved) return
        val instance = projectile.instance ?: return
        val pos = projectile.position

        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block != Block.AIR && block != Block.CAVE_AIR && block != Block.VOID_AIR) {
            explodeWindCharge(projectile)
            return
        }

        instance.getNearbyEntities(pos, 1.0).forEach { entity ->
            if (entity == projectile) return@forEach
            if (entity is LivingEntity) {
                explodeWindCharge(projectile)
                return
            }
        }
    }

    private fun explodeWindCharge(projectile: Entity) {
        val instance = projectile.instance ?: return
        val pos = projectile.position

        instance.getNearbyEntities(pos, KNOCKBACK_RADIUS).forEach { entity ->
            if (entity == projectile) return@forEach
            if (entity !is LivingEntity) return@forEach

            val direction = entity.position.asVec().sub(pos.asVec())
            val distance = direction.length()
            if (distance < 0.1) return@forEach

            val strength = KNOCKBACK_STRENGTH * (1.0 - distance / KNOCKBACK_RADIUS).coerceAtLeast(0.2)
            val knockback = direction.normalize().mul(strength)
                .withY(direction.normalize().y().coerceAtLeast(0.3) * strength)
            entity.velocity = entity.velocity.add(knockback)
        }

        projectile.remove()
    }
}
