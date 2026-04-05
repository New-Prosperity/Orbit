package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.instance.Instance
import kotlin.math.max
import kotlin.math.min

object ArmorReductionModule : VanillaModule {

    override val id = "armor-reduction"
    override val description = "Damage reduction from armor and armor toughness (vanilla formula)"

    private val BYPASSES_ARMOR = setOf(
        DamageType.STARVE,
        DamageType.DROWN,
        DamageType.OUT_OF_WORLD,
        DamageType.ON_FIRE,
        DamageType.MAGIC,
        DamageType.WITHER,
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-armor-reduction")

        node.addListener(EntityDamageEvent::class.java) { event ->
            val entity = event.entity
            if (entity !is LivingEntity) return@addListener
            if (event.damage.getType() in BYPASSES_ARMOR) return@addListener

            val armor = entity.getAttributeValue(Attribute.ARMOR)
            val toughness = entity.getAttributeValue(Attribute.ARMOR_TOUGHNESS)
            val rawDamage = event.damage.amount

            val reduced = applyArmorReduction(rawDamage, armor, toughness)
            event.damage.setAmount(reduced)
        }

        return node
    }

    private fun applyArmorReduction(damage: Float, armor: Double, toughness: Double): Float {
        val effectiveArmor = max(armor / 5.0, armor - (4.0 * damage) / (toughness + 8.0))
        val clampedArmor = effectiveArmor.coerceIn(0.0, 20.0)
        val reduction = clampedArmor / 25.0
        return (damage * (1.0 - reduction)).toFloat()
    }
}
