package me.nebula.orbit.mechanic.knockbackenchant

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.item.enchant.Enchantment
import kotlin.math.cos
import kotlin.math.sin

class KnockbackEnchantModule : OrbitModule("knockback-enchant") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val attacker = event.entity as? Player ?: return@addListener
            val target = event.target as? LivingEntity ?: return@addListener

            val item = attacker.getItemInMainHand()
            if (item.isAir) return@addListener

            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener
            val level = enchantments.level(Enchantment.KNOCKBACK)
            if (level <= 0) return@addListener

            val yaw = Math.toRadians(attacker.position.yaw().toDouble())
            val multiplier = 0.5 * level
            val extraKnockback = Vec(-sin(yaw) * 8.0 * multiplier, 2.0 * multiplier, cos(yaw) * 8.0 * multiplier)
            target.velocity = target.velocity.add(extraKnockback)
        }
    }
}
