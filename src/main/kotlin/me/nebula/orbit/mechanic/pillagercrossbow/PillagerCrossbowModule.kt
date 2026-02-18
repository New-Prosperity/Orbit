package me.nebula.orbit.mechanic.pillagercrossbow

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
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
import kotlin.random.Random

private val LAST_SHOT_TAG = Tag.Long("mechanic:pillager_crossbow:last_shot").defaultValue(0L)

private const val SEARCH_RANGE = 16.0
private const val SHOT_COOLDOWN_MS = 2000L
private const val ARROW_SPEED = 30.0
private const val ARROW_MIN_DAMAGE = 3f
private const val ARROW_MAX_DAMAGE = 6f

class PillagerCrossbowModule : OrbitModule("pillager-crossbow") {

    private var tickTask: Task? = null
    private val trackedPillagers: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        trackedPillagers.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedPillagers.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.PILLAGER) return@entityLoop
                trackedPillagers.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedPillagers.forEach { pillager ->
            if (pillager.isRemoved) return@forEach
            val lastShot = pillager.getTag(LAST_SHOT_TAG)
            if (now - lastShot < SHOT_COOLDOWN_MS) return@forEach

            val instance = pillager.instance ?: return@forEach
            val target = findNearestPlayer(pillager, instance) ?: return@forEach

            pillager.setTag(LAST_SHOT_TAG, now)
            shootArrow(pillager, target)
        }
    }

    private fun shootArrow(pillager: Entity, target: Player) {
        val instance = pillager.instance ?: return
        val arrow = Entity(EntityType.ARROW)
        arrow.setNoGravity(false)

        val direction = target.position.asVec()
            .add(0.0, target.eyeHeight * 0.5, 0.0)
            .sub(pillager.position.asVec().add(0.0, 1.5, 0.0))
            .normalize()

        arrow.velocity = direction.mul(ARROW_SPEED)
        val damage = ARROW_MIN_DAMAGE + Random.nextFloat() * (ARROW_MAX_DAMAGE - ARROW_MIN_DAMAGE)
        arrow.setTag(Tag.Float("mechanic:pillager_crossbow:damage"), damage)
        arrow.setTag(Tag.Integer("mechanic:pillager_crossbow:shooter"), pillager.entityId)

        arrow.setInstance(instance, pillager.position.add(0.0, 1.5, 0.0))

        arrow.scheduler().buildTask {
            checkArrowCollision(arrow)
        }.repeat(TaskSchedule.tick(1)).schedule()

        arrow.scheduler().buildTask { arrow.remove() }
            .delay(TaskSchedule.seconds(30))
            .schedule()
    }

    private fun checkArrowCollision(arrow: Entity) {
        if (arrow.isRemoved) return
        val instance = arrow.instance ?: return
        val pos = arrow.position

        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block != Block.AIR && block != Block.CAVE_AIR && block != Block.VOID_AIR) {
            arrow.remove()
            return
        }

        val shooterId = arrow.getTag(Tag.Integer("mechanic:pillager_crossbow:shooter").defaultValue(-1))
        instance.getNearbyEntities(pos, 1.0).forEach { entity ->
            if (entity == arrow) return@forEach
            if (entity.entityId == shooterId) return@forEach
            if (entity is LivingEntity) {
                val damage = arrow.getTag(Tag.Float("mechanic:pillager_crossbow:damage").defaultValue(4f))
                entity.damage(DamageType.MOB_PROJECTILE, damage)
                arrow.remove()
                return
            }
        }
    }

    private fun findNearestPlayer(pillager: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = SEARCH_RANGE * SEARCH_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = pillager.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }
}
