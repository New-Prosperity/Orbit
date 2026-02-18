package me.nebula.orbit.mechanic.blazebehavior

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val LAST_ATTACK_TAG = Tag.Long("mechanic:blaze_behavior:last_attack").defaultValue(0L)
private val BURST_COUNT_TAG = Tag.Integer("mechanic:blaze_behavior:burst_count").defaultValue(0)
private val BURST_START_TAG = Tag.Long("mechanic:blaze_behavior:burst_start").defaultValue(0L)

private const val ATTACK_RANGE = 16.0
private const val ATTACK_COOLDOWN_MS = 3000L
private const val BURST_INTERVAL_MS = 300L
private const val BURST_COUNT = 3
private const val FIREBALL_SPEED = 25.0
private const val FIREBALL_DAMAGE = 5f

class BlazeBehaviorModule : OrbitModule("blaze-behavior") {

    private var tickTask: Task? = null
    private val trackedBlazes: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        trackedBlazes.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedBlazes.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.BLAZE) return@entityLoop
                trackedBlazes.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedBlazes.forEach { blaze ->
            if (blaze.isRemoved) return@forEach
            val instance = blaze.instance ?: return@forEach

            val burstCount = blaze.getTag(BURST_COUNT_TAG)
            if (burstCount > 0) {
                val burstStart = blaze.getTag(BURST_START_TAG)
                if (now - burstStart >= BURST_INTERVAL_MS) {
                    val target = findNearestPlayer(blaze, instance)
                    if (target != null) shootFireball(blaze, target)
                    blaze.setTag(BURST_COUNT_TAG, burstCount - 1)
                    blaze.setTag(BURST_START_TAG, now)
                }
                return@forEach
            }

            val lastAttack = blaze.getTag(LAST_ATTACK_TAG)
            if (now - lastAttack < ATTACK_COOLDOWN_MS) return@forEach

            val target = findNearestPlayer(blaze, instance) ?: return@forEach
            blaze.setTag(LAST_ATTACK_TAG, now)
            blaze.setTag(BURST_COUNT_TAG, BURST_COUNT - 1)
            blaze.setTag(BURST_START_TAG, now)
            shootFireball(blaze, target)
        }
    }

    private fun findNearestPlayer(blaze: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = ATTACK_RANGE * ATTACK_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = blaze.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }

    private fun shootFireball(blaze: Entity, target: Player) {
        val instance = blaze.instance ?: return
        val fireball = Entity(EntityType.SMALL_FIREBALL)
        fireball.setNoGravity(true)

        val direction = target.position.asVec()
            .add(0.0, target.eyeHeight * 0.5, 0.0)
            .sub(blaze.position.asVec().add(0.0, 1.5, 0.0))
            .normalize()

        fireball.velocity = direction.mul(FIREBALL_SPEED)
        fireball.setInstance(instance, blaze.position.add(0.0, 1.5, 0.0))

        fireball.scheduler().buildTask {
            checkFireballCollision(fireball)
        }.repeat(TaskSchedule.tick(1)).schedule()

        fireball.scheduler().buildTask { fireball.remove() }
            .delay(TaskSchedule.seconds(10))
            .schedule()
    }

    private fun checkFireballCollision(fireball: Entity) {
        if (fireball.isRemoved) return
        val instance = fireball.instance ?: return
        val pos = fireball.position

        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block != Block.AIR && block != Block.CAVE_AIR && block != Block.VOID_AIR) {
            setFireAtImpact(instance, pos.blockX(), pos.blockY(), pos.blockZ())
            fireball.remove()
            return
        }

        instance.getNearbyEntities(pos, 1.0).forEach { entity ->
            if (entity == fireball) return@forEach
            if (entity.entityType == EntityType.BLAZE) return@forEach
            if (entity is LivingEntity) {
                entity.damage(DamageType.MOB_PROJECTILE, FIREBALL_DAMAGE)
                entity.entityMeta.setOnFire(true)
                setFireAtImpact(instance, pos.blockX(), pos.blockY(), pos.blockZ())
                fireball.remove()
                return
            }
        }
    }

    private fun setFireAtImpact(instance: Instance, x: Int, y: Int, z: Int) {
        for (dx in -1..1) {
            for (dz in -1..1) {
                val bx = x + dx
                val bz = z + dz
                if (instance.getBlock(bx, y, bz) == Block.AIR && instance.getBlock(bx, y - 1, bz) != Block.AIR) {
                    instance.setBlock(bx, y, bz, Block.FIRE)
                }
            }
        }
    }
}
