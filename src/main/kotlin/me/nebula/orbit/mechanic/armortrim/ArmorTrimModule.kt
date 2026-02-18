package me.nebula.orbit.mechanic.armortrim

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material

private val TRIM_TEMPLATES = setOf(
    Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
    Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
)

private val TRIM_MATERIALS = setOf(
    Material.IRON_INGOT, Material.COPPER_INGOT, Material.GOLD_INGOT,
    Material.LAPIS_LAZULI, Material.EMERALD, Material.DIAMOND,
    Material.NETHERITE_INGOT, Material.REDSTONE, Material.AMETHYST_SHARD,
    Material.QUARTZ,
)

private val TRIMMABLE_ARMOR = setOf(
    Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
    Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
    Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
    Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
    Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
    Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
)

class ArmorTrimModule : OrbitModule("armor-trim") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:smithing_table") return@addListener
            event.player.openInventory(Inventory(InventoryType.SMITHING, event.player.translate("orbit.mechanic.armor_trim.title")))
        }
    }
}
