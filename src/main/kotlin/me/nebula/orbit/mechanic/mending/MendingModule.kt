package me.nebula.orbit.mechanic.mending

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.ExperienceOrb
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.enchant.Enchantment
import java.util.concurrent.ThreadLocalRandom

private val EQUIPMENT_SLOTS = arrayOf(
    EquipmentSlot.MAIN_HAND,
    EquipmentSlot.OFF_HAND,
    EquipmentSlot.HELMET,
    EquipmentSlot.CHESTPLATE,
    EquipmentSlot.LEGGINGS,
    EquipmentSlot.BOOTS,
)

class MendingModule : OrbitModule("mending") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener

            val nearby = instance.getNearbyEntities(player.position, 1.5)
            val orbs = nearby.filterIsInstance<ExperienceOrb>()
            if (orbs.isEmpty()) return@addListener

            val candidates = EQUIPMENT_SLOTS.mapNotNull { slot ->
                val item = player.getEquipment(slot)
                if (item.isAir) return@mapNotNull null
                if (!hasMending(item)) return@mapNotNull null
                val damage = item.get(DataComponents.DAMAGE) ?: return@mapNotNull null
                if (damage <= 0) return@mapNotNull null
                slot to item
            }
            if (candidates.isEmpty()) return@addListener

            orbs.forEach { orb ->
                if (orb.isRemoved) return@forEach

                val xp = orb.experienceCount.toInt()
                val (slot, item) = candidates[ThreadLocalRandom.current().nextInt(candidates.size)]

                val currentDamage = item.get(DataComponents.DAMAGE) ?: return@forEach
                val repairAmount = (xp * 2).coerceAtMost(currentDamage)
                val repairedItem = item.with(DataComponents.DAMAGE, currentDamage - repairAmount)
                player.setEquipment(slot, repairedItem)

                orb.remove()
            }
        }
    }

    private fun hasMending(item: ItemStack): Boolean {
        val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return false
        return enchantments.level(Enchantment.MENDING) > 0
    }
}
