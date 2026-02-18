package me.nebula.orbit.mechanic.efficiency

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.tag.Tag

private val EFFICIENCY_BONUS_TAG = Tag.Integer("mechanic:efficiency:bonus").defaultValue(0)

private val TOOL_MATERIALS = setOf(
    "wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "golden_pickaxe", "diamond_pickaxe", "netherite_pickaxe",
    "wooden_axe", "stone_axe", "iron_axe", "golden_axe", "diamond_axe", "netherite_axe",
    "wooden_shovel", "stone_shovel", "iron_shovel", "golden_shovel", "diamond_shovel", "netherite_shovel",
    "wooden_hoe", "stone_hoe", "iron_hoe", "golden_hoe", "diamond_hoe", "netherite_hoe",
    "shears",
)

class EfficiencyModule : OrbitModule("efficiency") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val player = event.player
            val item = player.getItemInMainHand()
            if (item.isAir) return@addListener

            val materialName = item.material().name().substringAfter("minecraft:")
            if (materialName !in TOOL_MATERIALS) return@addListener

            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener
            val level = enchantments.level(Enchantment.EFFICIENCY)
            if (level <= 0) return@addListener

            player.setTag(EFFICIENCY_BONUS_TAG, level)
        }
    }
}
