package me.nebula.orbit.mechanic.thorns

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.item.enchant.Enchantment
import java.util.concurrent.ThreadLocalRandom

private val ARMOR_SLOTS = arrayOf(
    EquipmentSlot.HELMET,
    EquipmentSlot.CHESTPLATE,
    EquipmentSlot.LEGGINGS,
    EquipmentSlot.BOOTS,
)

class ThornsModule : OrbitModule("thorns") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            val entityDamage = event.damage as? EntityDamage ?: return@addListener
            val attacker = entityDamage.source as? LivingEntity ?: return@addListener

            val random = ThreadLocalRandom.current()

            ARMOR_SLOTS.forEach { slot ->
                val item = player.getEquipment(slot)
                if (item.isAir) return@forEach

                val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@forEach
                val level = enchantments.level(Enchantment.THORNS)
                if (level <= 0) return@forEach

                val chance = level * 0.15f
                if (random.nextFloat() < chance) {
                    val reflectedDamage = random.nextFloat(1f, 5f)
                    attacker.damage(EntityDamage(player, reflectedDamage))
                }
            }
        }
    }
}
