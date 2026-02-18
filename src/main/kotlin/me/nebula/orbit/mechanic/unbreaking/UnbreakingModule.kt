package me.nebula.orbit.mechanic.unbreaking

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.tag.Tag
import java.util.concurrent.ThreadLocalRandom

private val SKIP_DURABILITY_TAG = Tag.Boolean("mechanic:unbreaking:skip").defaultValue(false)

private val ARMOR_SLOTS = arrayOf(
    EquipmentSlot.HELMET,
    EquipmentSlot.CHESTPLATE,
    EquipmentSlot.LEGGINGS,
    EquipmentSlot.BOOTS,
)

class UnbreakingModule : OrbitModule("unbreaking") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            checkAndSkipDurability(player, player.getItemInMainHand(), EquipmentSlot.MAIN_HAND)
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val player = event.player
            checkAndSkipDurability(player, player.getItemInMainHand(), EquipmentSlot.MAIN_HAND)
        }

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            ARMOR_SLOTS.forEach { slot ->
                val item = player.getEquipment(slot)
                if (!item.isAir) {
                    checkAndSkipDurability(player, item, slot)
                }
            }
        }
    }

    private fun checkAndSkipDurability(player: Player, item: ItemStack, slot: EquipmentSlot) {
        if (item.isAir) return

        val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return
        val level = enchantments.level(Enchantment.UNBREAKING)
        if (level <= 0) return

        val chance = 1.0 / (level + 1)
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            val currentDamage = item.get(DataComponents.DAMAGE) ?: return
            val maxDamage = item.get(DataComponents.MAX_DAMAGE) ?: return
            if (currentDamage > 0) {
                player.setEquipment(slot, item.with(DataComponents.DAMAGE, (currentDamage - 1).coerceAtLeast(0)))
            }
        }
    }
}
