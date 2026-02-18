package me.nebula.orbit.mechanic.creeperexplosion

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Explosion
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val ACTIVATED_TAG = Tag.Boolean("mechanic:creeper_explosion:activated").defaultValue(false)
private val ACTIVATION_TIME_TAG = Tag.Long("mechanic:creeper_explosion:activation_time").defaultValue(0L)
private val CHARGED_TAG = Tag.Boolean("mechanic:creeper_explosion:charged").defaultValue(false)

private const val ACTIVATION_RANGE = 3.0
private const val FUSE_TIME_MS = 1500L
private const val BASE_RADIUS = 3.0f
private const val CHARGED_RADIUS = 6.0f

class CreeperExplosionModule : OrbitModule("creeper-explosion") {

    private var tickTask: Task? = null
    private val trackedCreepers: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        trackedCreepers.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedCreepers.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.CREEPER) return@entityLoop
                trackedCreepers.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedCreepers.toList().forEach { creeper ->
            if (creeper.isRemoved) return@forEach
            val instance = creeper.instance ?: return@forEach

            val nearestPlayer = findNearestPlayer(creeper, instance)

            if (!creeper.getTag(ACTIVATED_TAG)) {
                if (nearestPlayer != null && creeper.position.distance(nearestPlayer.position) <= ACTIVATION_RANGE) {
                    creeper.setTag(ACTIVATED_TAG, true)
                    creeper.setTag(ACTIVATION_TIME_TAG, now)
                    playHissSound(creeper, instance)
                }
            } else {
                val activationTime = creeper.getTag(ACTIVATION_TIME_TAG)
                if (now - activationTime >= FUSE_TIME_MS) {
                    explode(creeper, instance)
                } else if (nearestPlayer == null || creeper.position.distance(nearestPlayer.position) > ACTIVATION_RANGE * 2) {
                    creeper.setTag(ACTIVATED_TAG, false)
                }
            }
        }
    }

    private fun findNearestPlayer(creeper: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = Double.MAX_VALUE

        instance.players.forEach { player ->
            val dist = creeper.position.distance(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }

    private fun playHissSound(creeper: Entity, instance: Instance) {
        instance.players.forEach { player ->
            if (player.position.distance(creeper.position) <= 16.0) {
                player.playSound(
                    Sound.sound(SoundEvent.ENTITY_CREEPER_PRIMED.key(), Sound.Source.HOSTILE, 1f, 1f),
                    creeper.position.x(), creeper.position.y(), creeper.position.z(),
                )
            }
        }
    }

    private fun explode(creeper: Entity, instance: Instance) {
        val pos = creeper.position
        val charged = creeper.getTag(CHARGED_TAG)
        val radius = if (charged) CHARGED_RADIUS else BASE_RADIUS

        trackedCreepers.remove(creeper)
        creeper.remove()

        val explosion = CreeperExplosion(
            pos.x().toFloat(), pos.y().toFloat(), pos.z().toFloat(), radius,
        )
        explosion.apply(instance)
        damageNearby(instance, pos, radius)
    }

    private fun damageNearby(instance: Instance, center: Point, strength: Float) {
        val radius = strength * 2.0
        instance.getNearbyEntities(center, radius).forEach { entity ->
            if (entity.entityType == EntityType.CREEPER) return@forEach
            val distance = entity.position.distance(center)
            if (distance > radius) return@forEach
            val impact = (1.0 - distance / radius).toFloat()
            val damage = (impact * impact + impact) / 2f * 7f * strength + 1f
            if (entity is LivingEntity) {
                entity.damage(DamageType.EXPLOSION, damage)
            }
            val knockback = Vec(
                entity.position.x() - center.x(),
                entity.position.y() - center.y() + 0.5,
                entity.position.z() - center.z(),
            ).normalize().mul(impact.toDouble() * 1.5)
            entity.velocity = entity.velocity.add(knockback)
        }
    }

    private class CreeperExplosion(
        centerX: Float, centerY: Float, centerZ: Float, strength: Float,
    ) : Explosion(centerX, centerY, centerZ, strength) {

        override fun prepare(instance: Instance): List<Point> {
            val blocks = mutableListOf<Point>()
            val radius = strength.toInt()
            val center = Vec(centerX.toDouble(), centerY.toDouble(), centerZ.toDouble())
            for (x in -radius..radius) {
                for (y in -radius..radius) {
                    for (z in -radius..radius) {
                        val pos = Vec(centerX.toDouble() + x, centerY.toDouble() + y, centerZ.toDouble() + z)
                        if (pos.distance(center) > strength) continue
                        val block = instance.getBlock(pos)
                        if (block == Block.AIR || block == Block.BEDROCK) continue
                        val hardness = block.registry()?.hardness() ?: continue
                        if (hardness < 0) continue
                        blocks.add(pos)
                    }
                }
            }
            return blocks
        }
    }
}
