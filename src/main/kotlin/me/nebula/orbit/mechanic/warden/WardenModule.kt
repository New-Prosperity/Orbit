package me.nebula.orbit.mechanic.warden

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.ai.goal.MeleeAttackGoal
import net.minestom.server.entity.ai.goal.RandomStrollGoal
import net.minestom.server.entity.ai.target.ClosestEntityTarget
import net.minestom.server.entity.ai.target.LastEntityDamagerTarget
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Instance
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val SHRIEKER_ACTIVATION_TAG = Tag.Integer("mechanic:warden:shrieker_count").defaultValue(0)
private val LAST_SHRIEKER_TAG = Tag.Long("mechanic:warden:last_shrieker").defaultValue(0L)
private val WARDEN_SUSPICION_TAG = Tag.Integer("mechanic:warden:suspicion").defaultValue(0)
private val LAST_SONIC_BOOM_TAG = Tag.Long("mechanic:warden:last_sonic_boom").defaultValue(0L)

private const val SPAWN_THRESHOLD = 3
private const val SHRIEKER_COOLDOWN_MS = 10000L
private const val ATTACK_RANGE = 16.0
private const val SONIC_BOOM_COOLDOWN_MS = 5000L
private const val SONIC_BOOM_DAMAGE = 10f
private const val DARKNESS_RANGE = 20.0

class WardenModule : OrbitModule("warden") {

    private var tickTask: Task? = null
    private val trackedWardens: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            if (player.isSneaking) return@addListener
            val instance = player.instance ?: return@addListener
            val pos = player.position

            val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
            if (block.name() != "minecraft:sculk_shrieker") return@addListener

            val now = System.currentTimeMillis()
            val lastShrieker = player.getTag(LAST_SHRIEKER_TAG)
            if (now - lastShrieker < SHRIEKER_COOLDOWN_MS) return@addListener

            player.setTag(LAST_SHRIEKER_TAG, now)
            val count = player.getTag(SHRIEKER_ACTIVATION_TAG) + 1
            player.setTag(SHRIEKER_ACTIVATION_TAG, count)

            if (count >= SPAWN_THRESHOLD) {
                player.setTag(SHRIEKER_ACTIVATION_TAG, 0)
                spawnWarden(instance, pos)
            }
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedWardens.forEach { it.remove() }
        trackedWardens.clear()
        super.onDisable()
    }

    private fun spawnWarden(instance: Instance, pos: Pos) {
        val warden = EntityCreature(EntityType.WARDEN)

        warden.addAIGroup(
            listOf(
                MeleeAttackGoal(warden, 1.2, Duration.ofMillis(800)),
                RandomStrollGoal(warden, 3),
            ),
            listOf(
                ClosestEntityTarget(warden, ATTACK_RANGE.toFloat(), Player::class.java),
                LastEntityDamagerTarget(warden, ATTACK_RANGE.toFloat()),
            ),
        )

        warden.setTag(WARDEN_SUSPICION_TAG, 0)
        warden.setInstance(instance, Pos(pos.x(), pos.y() + 1.0, pos.z()))
        trackedWardens.add(warden)
    }

    private fun tick() {
        trackedWardens.removeIf { it.isRemoved }

        trackedWardens.forEach { warden ->
            val instance = warden.instance ?: return@forEach
            val now = System.currentTimeMillis()

            applyDarknessToNearbyPlayers(warden, instance)

            val lastBoom = warden.getTag(LAST_SONIC_BOOM_TAG)
            if (now - lastBoom < SONIC_BOOM_COOLDOWN_MS) return@forEach

            val target = findNearestPlayer(warden, instance) ?: return@forEach
            val distance = warden.position.distance(target.position)

            if (distance > 4.0 && distance <= ATTACK_RANGE) {
                warden.setTag(LAST_SONIC_BOOM_TAG, now)
                sonicBoom(warden, target)
            }
        }
    }

    private fun findNearestPlayer(warden: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = ATTACK_RANGE * ATTACK_RANGE

        instance.players.forEach { player ->
            val dist = warden.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }

    private fun sonicBoom(warden: Entity, target: Player) {
        target.damage(DamageType.SONIC_BOOM, SONIC_BOOM_DAMAGE)

        val direction = target.position.asVec().sub(warden.position.asVec()).normalize()
        target.velocity = target.velocity.add(direction.mul(15.0).withY(5.0))
    }

    private fun applyDarknessToNearbyPlayers(warden: Entity, instance: Instance) {
        instance.players.forEach { player ->
            if (warden.position.distance(player.position) <= DARKNESS_RANGE) {
                player.addEffect(Potion(PotionEffect.DARKNESS, 0, 260))
            }
        }
    }
}
