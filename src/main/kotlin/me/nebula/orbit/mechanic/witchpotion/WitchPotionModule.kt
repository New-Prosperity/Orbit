package me.nebula.orbit.mechanic.witchpotion

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Instance
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val LAST_THROW_TAG = Tag.Long("mechanic:witch_potion:last_throw").defaultValue(0L)

private const val SEARCH_RANGE = 16.0
private const val THROW_COOLDOWN_MS = 3000L
private const val SPLASH_RANGE = 4.0
private const val HARMING_DAMAGE = 6f

private val SPLASH_EFFECTS = listOf(
    PotionEffect.POISON,
    PotionEffect.SLOWNESS,
    PotionEffect.WEAKNESS,
)

class WitchPotionModule : OrbitModule("witch-potion") {

    private var tickTask: Task? = null
    private val trackedWitches: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        trackedWitches.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedWitches.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.WITCH) return@entityLoop
                trackedWitches.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedWitches.forEach { witch ->
            if (witch.isRemoved) return@forEach
            val lastThrow = witch.getTag(LAST_THROW_TAG)
            if (now - lastThrow < THROW_COOLDOWN_MS) return@forEach

            val instance = witch.instance ?: return@forEach
            val target = findNearestPlayer(witch, instance) ?: return@forEach

            witch.setTag(LAST_THROW_TAG, now)
            throwPotion(witch, target, instance)
        }
    }

    private fun throwPotion(witch: Entity, target: Player, instance: Instance) {
        val useHarming = Random.nextInt(4) == 0

        if (useHarming) {
            instance.getNearbyEntities(target.position, SPLASH_RANGE).forEach { entity ->
                if (entity is Player && !entity.isDead) {
                    entity.damage(DamageType.MAGIC, HARMING_DAMAGE)
                }
            }
        } else {
            val effect = SPLASH_EFFECTS[Random.nextInt(SPLASH_EFFECTS.size)]
            instance.getNearbyEntities(target.position, SPLASH_RANGE).forEach { entity ->
                if (entity is Player && !entity.isDead) {
                    entity.addEffect(Potion(effect, 0, 200))
                }
            }
        }
    }

    private fun findNearestPlayer(witch: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = SEARCH_RANGE * SEARCH_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = witch.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }
}
