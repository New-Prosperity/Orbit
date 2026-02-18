package me.nebula.orbit.mechanic.sharpness

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.item.enchant.Enchantment

private val UNDEAD_TYPES = setOf(
    EntityType.ZOMBIE,
    EntityType.ZOMBIE_VILLAGER,
    EntityType.HUSK,
    EntityType.DROWNED,
    EntityType.SKELETON,
    EntityType.WITHER_SKELETON,
    EntityType.STRAY,
    EntityType.PHANTOM,
    EntityType.ZOGLIN,
    EntityType.ZOMBIFIED_PIGLIN,
    EntityType.WITHER,
)

private val ARTHROPOD_TYPES = setOf(
    EntityType.SPIDER,
    EntityType.CAVE_SPIDER,
    EntityType.BEE,
    EntityType.SILVERFISH,
    EntityType.ENDERMITE,
)

class SharpnessModule : OrbitModule("sharpness") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val attacker = event.entity as? Player ?: return@addListener
            val target = event.target as? LivingEntity ?: return@addListener

            val item = attacker.getItemInMainHand()
            if (item.isAir) return@addListener

            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener

            var extraDamage = 0f

            val sharpnessLevel = enchantments.level(Enchantment.SHARPNESS)
            if (sharpnessLevel > 0) {
                extraDamage += 0.5f * sharpnessLevel + 0.5f
            }

            val smiteLevel = enchantments.level(Enchantment.SMITE)
            if (smiteLevel > 0 && target.entityType in UNDEAD_TYPES) {
                extraDamage += 2.5f * smiteLevel
            }

            val baneLevel = enchantments.level(Enchantment.BANE_OF_ARTHROPODS)
            if (baneLevel > 0 && target.entityType in ARTHROPOD_TYPES) {
                extraDamage += 2.5f * baneLevel
            }

            if (extraDamage > 0f) {
                target.damage(EntityDamage(attacker, extraDamage))
            }
        }
    }
}
