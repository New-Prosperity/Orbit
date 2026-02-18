package me.nebula.orbit.mechanic.sweeping

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.utils.particle.spawnParticleAt
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.particle.Particle
import kotlin.math.cos
import kotlin.math.sin

class SweepingModule : OrbitModule("sweeping") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val attacker = event.entity as? Player ?: return@addListener
            val target = event.target as? LivingEntity ?: return@addListener

            if (!attacker.isOnGround || attacker.isSprinting) return@addListener

            val item = attacker.getItemInMainHand()
            if (item.isAir) return@addListener

            val baseDamage = 2f

            val enchantments = item.get(DataComponents.ENCHANTMENTS)
            val sweepLevel = enchantments?.level(Enchantment.SWEEPING_EDGE) ?: 0

            val sweepDamage = if (sweepLevel > 0) {
                baseDamage * (0.5f + 0.5f / (sweepLevel + 1))
            } else {
                1f
            }

            val instance = target.instance ?: return@addListener
            val nearby = instance.getNearbyEntities(target.position, 1.0)

            nearby.forEach { entity ->
                if (entity === attacker || entity === target) return@forEach
                val livingEntity = entity as? LivingEntity ?: return@forEach
                livingEntity.damage(EntityDamage(attacker, sweepDamage))

                val yaw = Math.toRadians(attacker.position.yaw().toDouble())
                val knockback = Vec(-sin(yaw) * 4.0, 2.0, cos(yaw) * 4.0)
                livingEntity.velocity = livingEntity.velocity.add(knockback)
            }

            instance.spawnParticleAt(Particle.SWEEP_ATTACK, target.position.add(0.0, target.eyeHeight * 0.5, 0.0))
        }
    }
}
