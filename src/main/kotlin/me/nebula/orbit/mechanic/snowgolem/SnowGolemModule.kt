package me.nebula.orbit.mechanic.snowgolem

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val LAST_THROW_TAG = Tag.Long("mechanic:snow_golem:last_throw").defaultValue(0L)

private const val ATTACK_RANGE = 10.0
private const val THROW_COOLDOWN_MS = 1000L
private const val SNOWBALL_SPEED = 25.0
private const val SNOWBALL_DAMAGE = 1f

private val HOSTILE_TYPES = setOf(
    EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
    EntityType.CREEPER, EntityType.WITCH, EntityType.PILLAGER,
    EntityType.VINDICATOR, EntityType.RAVAGER, EntityType.BLAZE,
)

class SnowGolemModule : OrbitModule("snow-golem") {

    private var tickTask: Task? = null
    private val trackedGolems: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        trackedGolems.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedGolems.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.SNOW_GOLEM) return@entityLoop
                trackedGolems.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedGolems.forEach { golem ->
            if (golem.isRemoved) return@forEach
            val instance = golem.instance ?: return@forEach

            leaveSnowTrail(golem, instance)

            val lastThrow = golem.getTag(LAST_THROW_TAG)
            if (now - lastThrow < THROW_COOLDOWN_MS) return@forEach

            val target = findNearestHostile(golem, instance) ?: return@forEach
            golem.setTag(LAST_THROW_TAG, now)
            throwSnowball(golem, target)
        }
    }

    private fun leaveSnowTrail(golem: Entity, instance: Instance) {
        val pos = golem.position
        val bx = pos.blockX()
        val by = pos.blockY()
        val bz = pos.blockZ()

        val blockBelow = instance.getBlock(bx, by - 1, bz)
        val blockAt = instance.getBlock(bx, by, bz)

        if (blockBelow.isSolid && blockAt == Block.AIR) {
            instance.setBlock(bx, by, bz, Block.SNOW)
        }
    }

    private fun throwSnowball(golem: Entity, target: Entity) {
        val instance = golem.instance ?: return
        val snowball = Entity(EntityType.SNOWBALL)
        snowball.setNoGravity(false)

        val direction = target.position.asVec()
            .add(0.0, 1.0, 0.0)
            .sub(golem.position.asVec().add(0.0, 1.5, 0.0))
            .normalize()

        snowball.velocity = direction.mul(SNOWBALL_SPEED)
        snowball.setInstance(instance, golem.position.add(0.0, 1.5, 0.0))

        snowball.scheduler().buildTask {
            checkSnowballCollision(snowball, golem.entityId)
        }.repeat(TaskSchedule.tick(1)).schedule()

        snowball.scheduler().buildTask { snowball.remove() }
            .delay(TaskSchedule.seconds(10))
            .schedule()
    }

    private fun checkSnowballCollision(snowball: Entity, shooterId: Int) {
        if (snowball.isRemoved) return
        val instance = snowball.instance ?: return
        val pos = snowball.position

        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block != Block.AIR && block != Block.CAVE_AIR && block != Block.VOID_AIR) {
            snowball.remove()
            return
        }

        instance.getNearbyEntities(pos, 1.0).forEach { entity ->
            if (entity == snowball) return@forEach
            if (entity.entityId == shooterId) return@forEach
            if (entity is LivingEntity && entity.entityType in HOSTILE_TYPES) {
                entity.damage(DamageType.MOB_PROJECTILE, SNOWBALL_DAMAGE)
                snowball.remove()
                return
            }
        }
    }

    private fun findNearestHostile(golem: Entity, instance: Instance): Entity? {
        var nearest: Entity? = null
        var nearestDist = ATTACK_RANGE * ATTACK_RANGE

        instance.entities.forEach { entity ->
            if (entity.entityType !in HOSTILE_TYPES) return@forEach
            val dist = golem.position.distanceSquared(entity.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = entity
            }
        }
        return nearest
    }
}
