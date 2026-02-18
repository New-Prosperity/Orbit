package me.nebula.orbit.mechanic.wolftaming

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val TAMED_TAG = Tag.Boolean("mechanic:wolf_taming:tamed").defaultValue(false)
private val OWNER_UUID_TAG = Tag.String("mechanic:wolf_taming:owner")
private val SITTING_TAG = Tag.Boolean("mechanic:wolf_taming:sitting").defaultValue(false)
private val ATTACK_TARGET_TAG = Tag.Integer("mechanic:wolf_taming:attack_target").defaultValue(-1)
private val LAST_ATTACK_TAG = Tag.Long("mechanic:wolf_taming:last_attack").defaultValue(0L)

private const val TAME_CHANCE = 0.33
private const val FOLLOW_RANGE = 12.0
private const val FOLLOW_SPEED = 16.0
private const val ATTACK_RANGE = 2.0
private const val ATTACK_DAMAGE = 4f
private const val ATTACK_COOLDOWN_MS = 1000L
private const val SCAN_INTERVAL_TICKS = 10

class WolfTamingModule : OrbitModule("wolf-taming") {

    private var tickTask: Task? = null
    private val tamedWolves: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            val entity = event.target
            if (entity.entityType != EntityType.WOLF) return@addListener
            val player = event.player

            if (entity.getTag(TAMED_TAG)) {
                if (entity.getTag(OWNER_UUID_TAG) == player.uuid.toString() && player.isSneaking) {
                    val sitting = !entity.getTag(SITTING_TAG)
                    entity.setTag(SITTING_TAG, sitting)
                }
                return@addListener
            }

            val heldItem = player.getItemInMainHand()
            if (heldItem.material() != Material.BONE) return@addListener

            consumeItem(player)

            if (Random.nextDouble() < TAME_CHANCE) {
                entity.setTag(TAMED_TAG, true)
                entity.setTag(OWNER_UUID_TAG, player.uuid.toString())
                entity.setTag(SITTING_TAG, false)
                tamedWolves.add(entity)
            }
        }

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val damaged = event.entity
            if (damaged !is Player) return@addListener

            val attacker = event.damage.attacker ?: return@addListener

            tamedWolves.forEach { wolf ->
                if (wolf.isRemoved) return@forEach
                if (wolf.getTag(OWNER_UUID_TAG) != damaged.uuid.toString()) return@forEach
                if (wolf.getTag(SITTING_TAG)) return@forEach
                wolf.setTag(ATTACK_TARGET_TAG, attacker.entityId)
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
        tamedWolves.clear()
        super.onDisable()
    }

    private fun tick() {
        tamedWolves.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.WOLF) return@entityLoop
                if (!entity.getTag(TAMED_TAG)) return@entityLoop
                tamedWolves.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        tamedWolves.forEach { wolf ->
            if (wolf.isRemoved) return@forEach
            if (wolf.getTag(SITTING_TAG)) return@forEach

            val instance = wolf.instance ?: return@forEach
            val ownerUuid = wolf.getTag(OWNER_UUID_TAG) ?: return@forEach

            val attackTargetId = wolf.getTag(ATTACK_TARGET_TAG)
            if (attackTargetId >= 0) {
                val target = instance.entities.firstOrNull { it.entityId == attackTargetId && !it.isRemoved }
                if (target != null && wolf.position.distanceSquared(target.position) <= ATTACK_RANGE * ATTACK_RANGE) {
                    val lastAttack = wolf.getTag(LAST_ATTACK_TAG)
                    if (now - lastAttack >= ATTACK_COOLDOWN_MS) {
                        wolf.setTag(LAST_ATTACK_TAG, now)
                        if (target is net.minestom.server.entity.LivingEntity) {
                            target.damage(DamageType.MOB_ATTACK, ATTACK_DAMAGE)
                        }
                    }
                } else if (target != null) {
                    val direction = target.position.asVec().sub(wolf.position.asVec())
                    if (direction.length() > 0.1) {
                        wolf.velocity = direction.normalize().mul(FOLLOW_SPEED)
                    }
                } else {
                    wolf.setTag(ATTACK_TARGET_TAG, -1)
                }
                return@forEach
            }

            val owner = instance.players.firstOrNull { it.uuid.toString() == ownerUuid } ?: return@forEach
            val distance = wolf.position.distanceSquared(owner.position)

            if (distance > FOLLOW_RANGE * FOLLOW_RANGE) {
                wolf.teleport(owner.position)
            } else if (distance > 4.0) {
                val direction = owner.position.asVec().sub(wolf.position.asVec())
                if (direction.length() > 0.1) {
                    wolf.velocity = direction.normalize().mul(FOLLOW_SPEED)
                }
            }
        }
    }

    private fun consumeItem(player: Player) {
        val item = player.getItemInMainHand()
        if (item.amount() > 1) {
            player.setItemInMainHand(item.withAmount(item.amount() - 1))
        } else {
            player.setItemInMainHand(ItemStack.AIR)
        }
    }
}
