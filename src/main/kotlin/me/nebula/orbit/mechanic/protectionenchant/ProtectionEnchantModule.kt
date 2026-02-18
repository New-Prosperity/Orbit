package me.nebula.orbit.mechanic.protectionenchant

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.item.enchant.Enchantment

private val ARMOR_SLOTS = arrayOf(
    EquipmentSlot.HELMET,
    EquipmentSlot.CHESTPLATE,
    EquipmentSlot.LEGGINGS,
    EquipmentSlot.BOOTS,
)

private val FIRE_DAMAGE_TYPES = setOf(
    DamageType.IN_FIRE,
    DamageType.ON_FIRE,
    DamageType.LAVA,
)

private val EXPLOSION_DAMAGE_TYPES = setOf(
    DamageType.EXPLOSION,
)

private val PROJECTILE_DAMAGE_TYPES = setOf(
    DamageType.ARROW,
    DamageType.TRIDENT,
)

private const val REDUCTION_PER_LEVEL = 0.04f

class ProtectionEnchantModule : OrbitModule("protection-enchant") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            val damageType = event.damage.type

            var totalLevels = 0

            ARMOR_SLOTS.forEach { slot ->
                val item = player.getEquipment(slot)
                if (item.isAir) return@forEach

                val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@forEach

                totalLevels += enchantments.level(Enchantment.PROTECTION)

                if (damageType in FIRE_DAMAGE_TYPES) {
                    totalLevels += enchantments.level(Enchantment.FIRE_PROTECTION) * 2
                }
                if (damageType in EXPLOSION_DAMAGE_TYPES) {
                    totalLevels += enchantments.level(Enchantment.BLAST_PROTECTION) * 2
                }
                if (damageType in PROJECTILE_DAMAGE_TYPES) {
                    totalLevels += enchantments.level(Enchantment.PROJECTILE_PROTECTION) * 2
                }
                if (damageType == DamageType.FALL && slot == EquipmentSlot.BOOTS) {
                    totalLevels += enchantments.level(Enchantment.FEATHER_FALLING) * 3
                }
            }

            if (totalLevels <= 0) return@addListener

            val cappedLevels = totalLevels.coerceAtMost(20)
            val reduction = cappedLevels * REDUCTION_PER_LEVEL
            val originalDamage = event.damage.amount
            val reducedDamage = originalDamage * (1f - reduction)

            event.isCancelled = true
            if (reducedDamage > 0f) {
                player.health = (player.health - reducedDamage).coerceAtLeast(0f)
            }
        }
    }
}
