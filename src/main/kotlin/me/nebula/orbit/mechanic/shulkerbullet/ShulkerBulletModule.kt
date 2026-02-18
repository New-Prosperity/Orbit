package me.nebula.orbit.mechanic.shulkerbullet

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Instance
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val LAST_SHOT_TAG = Tag.Long("mechanic:shulker_bullet:last_shot").defaultValue(0L)
private val BULLET_TARGET_TAG = Tag.Integer("mechanic:shulker_bullet:target").defaultValue(-1)

private const val SEARCH_RANGE = 16.0
private const val SHOT_COOLDOWN_MS = 3000L
private const val BULLET_SPEED = 15.0
private const val BULLET_DAMAGE = 4f
private const val LEVITATION_DURATION_TICKS = 200
private const val HIT_RANGE = 1.5

class ShulkerBulletModule : OrbitModule("shulker-bullet") {

    private var tickTask: Task? = null
    private val trackedShulkers: MutableSet<Entity> = ConcurrentHashMap.newKeySet()
    private val activeBullets: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        activeBullets.forEach { it.remove() }
        activeBullets.clear()
        trackedShulkers.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedShulkers.removeIf { it.isRemoved }
        activeBullets.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.SHULKER) return@entityLoop
                trackedShulkers.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedShulkers.forEach { shulker ->
            if (shulker.isRemoved) return@forEach
            val lastShot = shulker.getTag(LAST_SHOT_TAG)
            if (now - lastShot < SHOT_COOLDOWN_MS) return@forEach

            val instance = shulker.instance ?: return@forEach
            val target = findNearestPlayer(shulker, instance) ?: return@forEach

            shulker.setTag(LAST_SHOT_TAG, now)
            fireBullet(shulker, target)
        }

        activeBullets.forEach { bullet ->
            if (bullet.isRemoved) return@forEach
            guideBullet(bullet)
        }
    }

    private fun fireBullet(shulker: Entity, target: Player) {
        val instance = shulker.instance ?: return
        val bullet = Entity(EntityType.SHULKER_BULLET)
        bullet.setNoGravity(true)
        bullet.setTag(BULLET_TARGET_TAG, target.entityId)

        val direction = target.position.asVec()
            .sub(shulker.position.asVec().add(0.0, 1.0, 0.0))
            .normalize()

        bullet.velocity = direction.mul(BULLET_SPEED)
        bullet.setInstance(instance, shulker.position.add(0.0, 1.0, 0.0))
        activeBullets.add(bullet)

        bullet.scheduler().buildTask {
            bullet.remove()
            activeBullets.remove(bullet)
        }.delay(TaskSchedule.seconds(15)).schedule()
    }

    private fun guideBullet(bullet: Entity) {
        val instance = bullet.instance ?: return
        val targetId = bullet.getTag(BULLET_TARGET_TAG)
        val target = instance.players.firstOrNull { it.entityId == targetId && !it.isDead && !it.isRemoved }

        if (target == null) {
            bullet.remove()
            activeBullets.remove(bullet)
            return
        }

        val distance = bullet.position.distance(target.position)
        if (distance <= HIT_RANGE) {
            target.damage(DamageType.MOB_PROJECTILE, BULLET_DAMAGE)
            target.addEffect(Potion(PotionEffect.LEVITATION, 0, LEVITATION_DURATION_TICKS))
            bullet.remove()
            activeBullets.remove(bullet)
            return
        }

        val direction = target.position.asVec()
            .add(0.0, target.eyeHeight * 0.5, 0.0)
            .sub(bullet.position.asVec())
            .normalize()

        bullet.velocity = direction.mul(BULLET_SPEED)
    }

    private fun findNearestPlayer(shulker: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = SEARCH_RANGE * SEARCH_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = shulker.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }
}
