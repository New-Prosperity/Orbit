package me.nebula.orbit.mechanic.combat

import me.nebula.orbit.mechanic.food.addExhaustion
import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.tag.Tag
import kotlin.math.cos
import kotlin.math.sin

private val LAST_ATTACK_TAG = Tag.Long("mechanic:combat:last_attack_time").defaultValue(0L)
private val LAST_DAMAGE_TAG = Tag.Long("mechanic:combat:last_damage_time").defaultValue(0L)

class CombatModule : OrbitModule("combat") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val attacker = event.entity as? Player ?: return@addListener
            val target = event.target as? LivingEntity ?: return@addListener

            val attackDamage = attacker.getAttributeValue(Attribute.ATTACK_DAMAGE).toFloat()
            val attackSpeed = attacker.getAttributeValue(Attribute.ATTACK_SPEED).toFloat()

            val now = System.currentTimeMillis()
            val lastAttack = attacker.getTag(LAST_ATTACK_TAG)
            val cooldownMs = 1000.0 / attackSpeed
            val cooldownProgress = ((now - lastAttack) / cooldownMs).coerceIn(0.0, 1.0)
            var damage = attackDamage * cooldownProgress.toFloat()

            val isCrit = !attacker.isOnGround
                && attacker.velocity.y() < 0
                && !attacker.isSprinting
                && cooldownProgress >= 0.9
            if (isCrit) damage *= 1.5f

            target.damage(EntityDamage(attacker, damage))

            val yaw = Math.toRadians(attacker.position.yaw().toDouble())
            val knockback = Vec(-sin(yaw) * 8.0, 4.0, cos(yaw) * 8.0)
            target.velocity = target.velocity.add(knockback)

            attacker.setTag(LAST_ATTACK_TAG, now)
            attacker.addExhaustion(0.1f)
        }

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            if (event.damage.type == DamageType.FALL) return@addListener
            if (event.damage.type == DamageType.STARVE) return@addListener

            val entity = event.entity
            val now = System.currentTimeMillis()
            val lastDamage = entity.getTag(LAST_DAMAGE_TAG)
            if (now - lastDamage < 500) {
                event.isCancelled = true
                return@addListener
            }
            entity.setTag(LAST_DAMAGE_TAG, now)
        }
    }
}
