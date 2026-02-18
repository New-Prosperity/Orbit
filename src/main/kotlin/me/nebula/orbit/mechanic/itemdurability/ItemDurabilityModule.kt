package me.nebula.orbit.mechanic.itemdurability

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.item.ItemStack

private val WEAPON_MATERIALS = setOf(
    "wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword", "netherite_sword",
    "wooden_axe", "stone_axe", "iron_axe", "golden_axe", "diamond_axe", "netherite_axe",
    "trident", "mace",
)

private val TOOL_MATERIALS = setOf(
    "wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "golden_pickaxe", "diamond_pickaxe", "netherite_pickaxe",
    "wooden_axe", "stone_axe", "iron_axe", "golden_axe", "diamond_axe", "netherite_axe",
    "wooden_shovel", "stone_shovel", "iron_shovel", "golden_shovel", "diamond_shovel", "netherite_shovel",
    "wooden_hoe", "stone_hoe", "iron_hoe", "golden_hoe", "diamond_hoe", "netherite_hoe",
    "shears",
)

private val ARMOR_SLOTS = arrayOf(
    EquipmentSlot.HELMET,
    EquipmentSlot.CHESTPLATE,
    EquipmentSlot.LEGGINGS,
    EquipmentSlot.BOOTS,
)

class ItemDurabilityModule : OrbitModule("item-durability") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            val item = player.getItemInMainHand()
            val name = item.material().name().substringAfter("minecraft:")
            if (name !in WEAPON_MATERIALS) return@addListener
            applyDamage(player, EquipmentSlot.MAIN_HAND, item, 1)
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val player = event.player
            val item = player.getItemInMainHand()
            val name = item.material().name().substringAfter("minecraft:")
            if (name !in TOOL_MATERIALS) return@addListener
            applyDamage(player, EquipmentSlot.MAIN_HAND, item, 1)
        }

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            for (slot in ARMOR_SLOTS) {
                val item = player.getEquipment(slot)
                if (item.isAir) continue
                applyDamage(player, slot, item, 1)
            }
        }
    }

    private fun applyDamage(player: Player, slot: EquipmentSlot, item: ItemStack, amount: Int) {
        val currentDamage = item.get(DataComponents.DAMAGE) ?: 0
        val maxDamage = item.get(DataComponents.MAX_DAMAGE) ?: return
        val newDamage = currentDamage + amount

        if (newDamage >= maxDamage) {
            player.setEquipment(slot, ItemStack.AIR)
        } else {
            player.setEquipment(slot, item.with(DataComponents.DAMAGE, newDamage))
        }
    }
}
