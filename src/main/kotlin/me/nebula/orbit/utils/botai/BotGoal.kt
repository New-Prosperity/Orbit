package me.nebula.orbit.utils.botai

import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material

internal val WEAPON_MATERIALS = setOf(
    Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
    Material.IRON_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
    Material.STONE_SWORD, Material.STONE_AXE, Material.WOODEN_SWORD,
)

internal val HEALING_POTIONS = setOf(Material.POTION, Material.SPLASH_POTION)

internal val LOG_BLOCKS = setOf(
    Block.OAK_LOG, Block.BIRCH_LOG, Block.SPRUCE_LOG,
    Block.JUNGLE_LOG, Block.ACACIA_LOG, Block.DARK_OAK_LOG,
    Block.MANGROVE_LOG, Block.CHERRY_LOG,
)

internal val LOG_MATERIALS = setOf(
    Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG,
    Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
    Material.MANGROVE_LOG, Material.CHERRY_LOG,
)

internal val PLANK_MATERIALS = setOf(
    Material.OAK_PLANKS, Material.BIRCH_PLANKS, Material.SPRUCE_PLANKS,
    Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
    Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS,
)

internal val ARMOR_TIERS = listOf(
    listOf(Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS),
    listOf(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS),
    listOf(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS),
    listOf(Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS),
    listOf(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS),
    listOf(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS),
)

internal val EQUIPMENT_SLOTS = listOf(
    EquipmentSlot.HELMET, EquipmentSlot.CHESTPLATE, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS,
)

internal val FOOD_MATERIALS = setOf(
    Material.APPLE, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE,
    Material.BREAD, Material.COOKED_BEEF, Material.COOKED_PORKCHOP,
    Material.COOKED_CHICKEN, Material.COOKED_MUTTON, Material.COOKED_SALMON,
    Material.COOKED_COD, Material.GOLDEN_CARROT, Material.BAKED_POTATO,
    Material.COOKIE, Material.MELON_SLICE, Material.DRIED_KELP,
    Material.SWEET_BERRIES, Material.CARROT, Material.POTATO,
    Material.BEETROOT, Material.MUSHROOM_STEW, Material.BEETROOT_SOUP,
    Material.RABBIT_STEW, Material.ROTTEN_FLESH,
)

internal val BUILD_BLOCKS = setOf(
    Material.COBBLESTONE, Material.DIRT, Material.OAK_PLANKS,
    Material.BIRCH_PLANKS, Material.SPRUCE_PLANKS, Material.STONE,
    Material.SAND, Material.SANDSTONE,
)

abstract class BotGoal {
    abstract fun calculateUtility(brain: BotBrain): Float
    abstract fun shouldActivate(brain: BotBrain): Boolean
    abstract fun createActions(brain: BotBrain): List<BotAction>
    open fun shouldCancel(brain: BotBrain): Boolean = false
}
