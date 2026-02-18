package me.nebula.orbit.mechanic.axolotlbehavior

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.utils.entitytracker.nearbyEntities
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntitySpawnEvent
import net.minestom.server.instance.Instance
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val PLAYING_DEAD_TAG = Tag.Boolean("mechanic:axolotl:playing_dead").defaultValue(false)
private val PLAY_DEAD_START_TAG = Tag.Long("mechanic:axolotl:play_dead_start").defaultValue(0L)
private val LAST_ATTACK_TAG = Tag.Long("mechanic:axolotl:last_attack").defaultValue(0L)

private const val ATTACK_RANGE = 8.0
private const val MELEE_RANGE = 2.0
private const val ATTACK_DAMAGE = 2f
private const val ATTACK_COOLDOWN_MS = 2000L
private const val PLAY_DEAD_DURATION_MS = 10000L
private const val REGEN_DURATION_TICKS = 200
private const val LOW_HP_THRESHOLD = 4f
private const val CHASE_SPEED = 14.0
private const val REGEN_GRANT_RANGE = 8.0
private const val SCAN_INTERVAL_TICKS = 20

private val TARGET_TYPES = setOf(EntityType.DROWNED, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN)

class AxolotlBehaviorModule : OrbitModule("axolotl-behavior") {

    private var tickTask: Task? = null
    private val trackedAxolotls: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntitySpawnEvent::class.java) { event ->
            if (event.entity.entityType == EntityType.AXOLOTL) {
                trackedAxolotls.add(event.entity)
            }
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(SCAN_INTERVAL_TICKS))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedAxolotls.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedAxolotls.removeIf { it.isRemoved }

        val now = System.currentTimeMillis()
        trackedAxolotls.forEach { axolotl ->
            if (axolotl.isRemoved) return@forEach
            val living = axolotl as? LivingEntity ?: return@forEach
            val instance = axolotl.instance ?: return@forEach

            if (axolotl.getTag(PLAYING_DEAD_TAG)) {
                handlePlayingDead(living, now)
                return@forEach
            }

            if (living.health <= LOW_HP_THRESHOLD) {
                axolotl.setTag(PLAYING_DEAD_TAG, true)
                axolotl.setTag(PLAY_DEAD_START_TAG, now)
                return@forEach
            }

            attackNearbyTargets(living, instance, now)
        }
    }

    private fun handlePlayingDead(axolotl: LivingEntity, now: Long) {
        val start = axolotl.getTag(PLAY_DEAD_START_TAG)
        if (now - start >= PLAY_DEAD_DURATION_MS) {
            axolotl.setTag(PLAYING_DEAD_TAG, false)
            axolotl.health = axolotl.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
        }
    }

    private fun attackNearbyTargets(axolotl: LivingEntity, instance: Instance, now: Long) {
        val lastAttack = axolotl.getTag(LAST_ATTACK_TAG)
        if (now - lastAttack < ATTACK_COOLDOWN_MS) return

        val target = instance.nearbyEntities(axolotl.position, ATTACK_RANGE)
            .firstOrNull { it.entityType in TARGET_TYPES && !it.isRemoved }
            ?: return

        val distance = axolotl.position.distanceSquared(target.position)

        if (distance <= MELEE_RANGE * MELEE_RANGE) {
            axolotl.setTag(LAST_ATTACK_TAG, now)

            val targetLiving = target as? LivingEntity
            val wasDead = targetLiving?.isDead == true
            targetLiving?.damage(DamageType.MOB_ATTACK, ATTACK_DAMAGE)
            val nowDead = targetLiving?.isDead == true

            if (!wasDead && nowDead) {
                grantRegenToNearbyPlayers(axolotl, instance)
            }
        } else {
            val direction = target.position.asVec().sub(axolotl.position.asVec())
            if (direction.length() > 0.1) {
                axolotl.velocity = direction.normalize().mul(CHASE_SPEED)
            }
        }
    }

    private fun grantRegenToNearbyPlayers(axolotl: Entity, instance: Instance) {
        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            if (axolotl.position.distanceSquared(player.position) <= REGEN_GRANT_RANGE * REGEN_GRANT_RANGE) {
                player.addEffect(Potion(PotionEffect.REGENERATION, 0, REGEN_DURATION_TICKS))
            }
        }
    }
}
