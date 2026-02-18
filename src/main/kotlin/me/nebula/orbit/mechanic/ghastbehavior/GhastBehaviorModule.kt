package me.nebula.orbit.mechanic.ghastbehavior

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val LAST_ATTACK_TAG = Tag.Long("mechanic:ghast_behavior:last_attack").defaultValue(0L)
private val FIREBALL_SHOOTER_TAG = Tag.Integer("mechanic:ghast_behavior:shooter").defaultValue(-1)
private val DEFLECTED_TAG = Tag.Boolean("mechanic:ghast_behavior:deflected").defaultValue(false)

private const val ATTACK_RANGE = 48.0
private const val ATTACK_COOLDOWN_MS = 5000L
private const val FIREBALL_SPEED = 20.0
private const val EXPLOSION_RADIUS = 1.0
private const val FIREBALL_DAMAGE = 6f

class GhastBehaviorModule : OrbitModule("ghast-behavior") {

    private var tickTask: Task? = null
    private val trackedGhasts: MutableSet<Entity> = ConcurrentHashMap.newKeySet()
    private val activeFireballs: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val attacker = event.entity as? Player ?: return@addListener
            val target = event.target

            if (target.entityType != EntityType.FIREBALL) return@addListener
            if (!activeFireballs.contains(target)) return@addListener

            val direction = attacker.position.direction()
            target.velocity = Vec(direction.x(), direction.y(), direction.z()).normalize().mul(FIREBALL_SPEED)
            target.setTag(DEFLECTED_TAG, true)
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(10))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedGhasts.clear()
        activeFireballs.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedGhasts.removeIf { it.isRemoved }
        activeFireballs.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.GHAST) return@entityLoop
                trackedGhasts.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedGhasts.forEach { ghast ->
            if (ghast.isRemoved) return@forEach
            val lastAttack = ghast.getTag(LAST_ATTACK_TAG)
            if (now - lastAttack < ATTACK_COOLDOWN_MS) return@forEach

            val instance = ghast.instance ?: return@forEach
            val target = findNearestPlayer(ghast, instance) ?: return@forEach

            ghast.setTag(LAST_ATTACK_TAG, now)
            shootFireball(ghast, target)
        }
    }

    private fun findNearestPlayer(ghast: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = ATTACK_RANGE * ATTACK_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = ghast.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }

    private fun shootFireball(ghast: Entity, target: Player) {
        val instance = ghast.instance ?: return
        val fireball = Entity(EntityType.FIREBALL)
        fireball.setNoGravity(true)
        fireball.setTag(FIREBALL_SHOOTER_TAG, ghast.entityId)

        val direction = target.position.asVec()
            .add(0.0, target.eyeHeight, 0.0)
            .sub(ghast.position.asVec())
            .normalize()

        fireball.velocity = direction.mul(FIREBALL_SPEED)
        fireball.setInstance(instance, ghast.position.add(0.0, 0.5, 0.0))
        activeFireballs.add(fireball)

        fireball.scheduler().buildTask {
            checkFireballCollision(fireball)
        }.repeat(TaskSchedule.tick(1)).schedule()

        fireball.scheduler().buildTask {
            fireball.remove()
            activeFireballs.remove(fireball)
        }.delay(TaskSchedule.seconds(20)).schedule()
    }

    private fun checkFireballCollision(fireball: Entity) {
        if (fireball.isRemoved) return
        val instance = fireball.instance ?: return
        val pos = fireball.position

        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block != Block.AIR && block != Block.CAVE_AIR && block != Block.VOID_AIR) {
            explodeFireball(fireball, instance, pos)
            return
        }

        val shooterId = fireball.getTag(FIREBALL_SHOOTER_TAG)
        val deflected = fireball.getTag(DEFLECTED_TAG)

        instance.getNearbyEntities(pos, 1.5).forEach { entity ->
            if (entity == fireball) return@forEach
            if (!deflected && entity.entityId == shooterId) return@forEach
            if (deflected && entity.entityType == EntityType.GHAST) {
                if (entity is LivingEntity) entity.damage(DamageType.FIREBALL, FIREBALL_DAMAGE * 2)
                explodeFireball(fireball, instance, pos)
                return
            }
            if (entity is LivingEntity) {
                entity.damage(DamageType.FIREBALL, FIREBALL_DAMAGE)
                explodeFireball(fireball, instance, pos)
                return
            }
        }
    }

    private fun explodeFireball(fireball: Entity, instance: Instance, center: Point) {
        activeFireballs.remove(fireball)
        fireball.remove()

        val radius = EXPLOSION_RADIUS.toInt()
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val bx = center.blockX() + x
                    val by = center.blockY() + y
                    val bz = center.blockZ() + z
                    val existing = instance.getBlock(bx, by, bz)
                    if (existing != Block.AIR && existing != Block.BEDROCK) {
                        val hardness = existing.registry()?.hardness() ?: continue
                        if (hardness >= 0) instance.setBlock(bx, by, bz, Block.AIR)
                    }
                }
            }
        }

        for (dx in -1..1) {
            for (dz in -1..1) {
                val bx = center.blockX() + dx
                val bz = center.blockZ() + dz
                val by = center.blockY()
                if (instance.getBlock(bx, by, bz) == Block.AIR && instance.getBlock(bx, by - 1, bz) != Block.AIR) {
                    instance.setBlock(bx, by, bz, Block.FIRE)
                }
            }
        }

        instance.getNearbyEntities(center, 3.0).forEach { entity ->
            if (entity is LivingEntity) {
                val dist = entity.position.distance(center)
                val impact = (1.0 - dist / 3.0).coerceAtLeast(0.0).toFloat()
                entity.damage(DamageType.EXPLOSION, FIREBALL_DAMAGE * impact)
            }
        }
    }
}
