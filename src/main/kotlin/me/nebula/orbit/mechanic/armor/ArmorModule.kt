package me.nebula.orbit.mechanic.armor

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityDamageEvent

class ArmorModule : OrbitModule("armor") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val entity = event.entity
            if (entity !is Player) return@addListener

            val damageType = event.damage.type
            if (damageType == DamageType.STARVE || damageType == DamageType.OUT_OF_WORLD || damageType == DamageType.GENERIC_KILL) return@addListener

            val armor = entity.getAttributeValue(Attribute.ARMOR).toFloat()
            val toughness = entity.getAttributeValue(Attribute.ARMOR_TOUGHNESS).toFloat()
            val damage = event.damage.amount

            val reduction = calculateArmorReduction(damage, armor, toughness)
            event.damage.amount = damage * (1f - reduction)
        }
    }

    private fun calculateArmorReduction(damage: Float, armor: Float, toughness: Float): Float {
        val effective = (armor - damage / (2f + toughness / 4f)).coerceAtLeast(armor * 0.2f)
        return (effective / 25f).coerceIn(0f, 0.8f)
    }
}
