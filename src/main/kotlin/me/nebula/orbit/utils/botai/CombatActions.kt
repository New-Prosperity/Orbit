package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.metadata.LivingEntityMeta
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

class AttackEntity(private val target: LivingEntity, private val times: Int = 1) : BotAction {
    override var isComplete = false
        private set
    private var hits = 0
    private var cooldown = 0

    override fun tick(player: Player) {
        if (!target.isActive || target.isRemoved) {
            isComplete = true
            return
        }
        if (cooldown > 0) {
            cooldown--
            return
        }
        val skill = BotAI.skillLevels[player.uuid]
        val jitter = skill?.movementJitter ?: 0f
        val distSq = player.position.distanceSquared(target.position)
        if (distSq > 9.0) {
            BotMovement.moveToward(player, target.position, true, jitter)
            return
        }
        BotMovement.lookAt(player, target.position.add(0.0, target.eyeHeight, 0.0))
        player.swingMainHand()
        val aimAccuracy = skill?.aimAccuracy ?: 1f
        if (Random.nextFloat() < aimAccuracy) {
            target.damage(Damage.fromEntity(player, player.getAttributeValue(Attribute.ATTACK_DAMAGE).toFloat()))
        }
        hits++
        cooldown = 10
        if (hits >= times) isComplete = true
    }

    override fun cancel(player: Player) {
        player.isSprinting = false
    }
}

class CriticalHit(private val target: LivingEntity) : BotAction {
    override var isComplete = false
        private set

    private enum class Phase { APPROACH, JUMP, WAIT_PEAK, STRIKE }
    private var phase = Phase.APPROACH
    private var waitTicks = 0

    override fun tick(player: Player) {
        if (!target.isActive || target.isRemoved) {
            isComplete = true
            return
        }
        when (phase) {
            Phase.APPROACH -> {
                val skill = BotAI.skillLevels[player.uuid]
                val jitter = skill?.movementJitter ?: 0f
                val distSq = player.position.distanceSquared(target.position)
                if (distSq <= 9.0) {
                    phase = Phase.JUMP
                    return
                }
                BotMovement.moveToward(player, target.position, true, jitter)
            }
            Phase.JUMP -> {
                BotMovement.lookAt(player, target.position.add(0.0, target.eyeHeight, 0.0))
                BotMovement.jump(player)
                waitTicks = 0
                phase = Phase.WAIT_PEAK
            }
            Phase.WAIT_PEAK -> {
                BotMovement.lookAt(player, target.position.add(0.0, target.eyeHeight, 0.0))
                waitTicks++
                val vy = player.velocity.y()
                val atPeak = (vy <= 0.5 && !player.isOnGround) || waitTicks >= 5
                if (atPeak) phase = Phase.STRIKE
            }
            Phase.STRIKE -> {
                BotMovement.lookAt(player, target.position.add(0.0, target.eyeHeight, 0.0))
                player.swingMainHand()
                val baseDamage = player.getAttributeValue(Attribute.ATTACK_DAMAGE).toFloat()
                val skill = BotAI.skillLevels[player.uuid]
                val critChance = skill?.criticalHitChance ?: 0.5f
                val multiplier = if (Random.nextFloat() < critChance) 1.5f else 1.0f
                target.damage(Damage.fromEntity(player, baseDamage * multiplier))
                isComplete = true
            }
        }
    }

    override fun cancel(player: Player) {
        player.isSprinting = false
    }
}

class ShieldBlock(private val durationTicks: Int = 60) : BotAction {
    override var isComplete = false
        private set
    private var elapsed = 0
    private var activated = false
    private var reactionDelay = 0

    override fun start(player: Player) {
        val offhand = player.inventory.getItemStack(player.inventory.size - 1)
        if (offhand.material() != Material.SHIELD) {
            isComplete = true
            return
        }
        val skill = BotAI.skillLevels[player.uuid]
        reactionDelay = skill?.reactionTimeTicks ?: 0
    }

    override fun tick(player: Player) {
        if (reactionDelay > 0) {
            reactionDelay--
            return
        }
        if (!activated) {
            val offhand = player.inventory.getItemStack(player.inventory.size - 1)
            if (offhand.material() != Material.SHIELD) {
                isComplete = true
                return
            }
            player.editEntityMeta(LivingEntityMeta::class.java) {
                it.setHandActive(true)
                it.setActiveHand(PlayerHand.OFF)
            }
            activated = true
        }
        elapsed++
        if (elapsed >= durationTicks) {
            player.editEntityMeta(LivingEntityMeta::class.java) {
                it.setHandActive(false)
            }
            isComplete = true
        }
    }

    override fun cancel(player: Player) {
        if (activated) {
            player.editEntityMeta(LivingEntityMeta::class.java) {
                it.setHandActive(false)
            }
        }
    }
}

class ShootBow(private val target: LivingEntity, private val chargeTicks: Int = 20) : BotAction {
    override var isComplete = false
        private set

    private enum class Phase { EQUIP, DRAW, RELEASE }
    private var phase = Phase.EQUIP
    private var elapsed = 0
    private var aimOffset = Vec.ZERO

    override fun start(player: Player) {
        val skill = BotAI.skillLevels[player.uuid]
        val inaccuracy = (1f - (skill?.aimAccuracy ?: 0.7f)) * 4.0
        val rng = ThreadLocalRandom.current()
        aimOffset = Vec(
            rng.nextGaussian() * inaccuracy,
            rng.nextGaussian() * inaccuracy * 0.5,
            rng.nextGaussian() * inaccuracy,
        )
    }

    override fun tick(player: Player) {
        if (!target.isActive || target.isRemoved) {
            isComplete = true
            return
        }
        val aimTarget = target.position.add(aimOffset.x(), target.eyeHeight + aimOffset.y(), aimOffset.z())
        when (phase) {
            Phase.EQUIP -> {
                val bowSlot = findBowSlot(player)
                if (bowSlot < 0) {
                    isComplete = true
                    return
                }
                player.setHeldItemSlot(bowSlot.coerceIn(0, 8).toByte())
                BotMovement.lookAt(player, aimTarget)
                val event = PlayerUseItemEvent(player, PlayerHand.MAIN, player.itemInMainHand, 0L)
                EventDispatcher.call(event)
                player.editEntityMeta(LivingEntityMeta::class.java) {
                    it.setHandActive(true)
                    it.setActiveHand(PlayerHand.MAIN)
                }
                elapsed = 0
                phase = Phase.DRAW
            }
            Phase.DRAW -> {
                BotMovement.lookAt(player, aimTarget)
                elapsed++
                if (elapsed >= chargeTicks) phase = Phase.RELEASE
            }
            Phase.RELEASE -> {
                BotMovement.lookAt(player, aimTarget)
                player.editEntityMeta(LivingEntityMeta::class.java) {
                    it.setHandActive(false)
                }
                player.setItemInMainHand(ItemStack.AIR)
                val bowSlot = findBowSlot(player)
                if (bowSlot >= 0) player.setHeldItemSlot(bowSlot.coerceIn(0, 8).toByte())
                isComplete = true
            }
        }
    }

    override fun cancel(player: Player) {
        player.editEntityMeta(LivingEntityMeta::class.java) {
            it.setHandActive(false)
        }
    }

    private fun findBowSlot(player: Player): Int {
        for (i in 0..8) {
            if (player.inventory.getItemStack(i).material() == Material.BOW) return i
        }
        return -1
    }
}

class ThrowProjectile(private val type: Material, private val target: Point) : BotAction {
    override var isComplete = false
        private set

    override fun tick(player: Player) {
        val slot = findSlot(player, type)
        if (slot < 0) {
            isComplete = true
            return
        }
        player.setHeldItemSlot(slot.coerceIn(0, 8).toByte())
        BotMovement.lookAt(player, target)
        val event = PlayerUseItemEvent(player, PlayerHand.MAIN, player.itemInMainHand, 0L)
        EventDispatcher.call(event)
        isComplete = true
    }

    private fun findSlot(player: Player, material: Material): Int {
        for (i in 0..8) {
            if (player.inventory.getItemStack(i).material() == material) return i
        }
        return -1
    }
}
