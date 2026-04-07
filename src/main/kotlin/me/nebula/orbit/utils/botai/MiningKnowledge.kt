package me.nebula.orbit.utils.botai

import me.nebula.orbit.utils.vanilla.modules.SMELTING_RECIPES
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

object MiningKnowledge {

    private val ORE_BLOCKS = setOf(
        Block.COAL_ORE, Block.DEEPSLATE_COAL_ORE,
        Block.IRON_ORE, Block.DEEPSLATE_IRON_ORE,
        Block.GOLD_ORE, Block.DEEPSLATE_GOLD_ORE,
        Block.DIAMOND_ORE, Block.DEEPSLATE_DIAMOND_ORE,
        Block.LAPIS_ORE, Block.DEEPSLATE_LAPIS_ORE,
        Block.REDSTONE_ORE, Block.DEEPSLATE_REDSTONE_ORE,
        Block.EMERALD_ORE, Block.DEEPSLATE_EMERALD_ORE,
        Block.COPPER_ORE, Block.DEEPSLATE_COPPER_ORE,
    )

    private val TOOL_TIERS = mapOf(
        Material.WOODEN_PICKAXE to 0,
        Material.WOODEN_AXE to 0,
        Material.WOODEN_SWORD to 0,
        Material.WOODEN_SHOVEL to 0,
        Material.STONE_PICKAXE to 1,
        Material.STONE_AXE to 1,
        Material.STONE_SWORD to 1,
        Material.STONE_SHOVEL to 1,
        Material.IRON_PICKAXE to 2,
        Material.IRON_AXE to 2,
        Material.IRON_SWORD to 2,
        Material.IRON_SHOVEL to 2,
        Material.DIAMOND_PICKAXE to 3,
        Material.DIAMOND_AXE to 3,
        Material.DIAMOND_SWORD to 3,
        Material.DIAMOND_SHOVEL to 3,
        Material.NETHERITE_PICKAXE to 4,
        Material.NETHERITE_AXE to 4,
        Material.NETHERITE_SWORD to 4,
        Material.NETHERITE_SHOVEL to 4,
        Material.GOLDEN_PICKAXE to 0,
        Material.GOLDEN_AXE to 0,
        Material.GOLDEN_SWORD to 0,
        Material.GOLDEN_SHOVEL to 0,
    )

    private val ITEM_VALUES = mapOf(
        Material.DIAMOND to 100,
        Material.EMERALD to 90,
        Material.NETHERITE_INGOT to 120,
        Material.GOLD_INGOT to 60,
        Material.IRON_INGOT to 50,
        Material.RAW_GOLD to 55,
        Material.RAW_IRON to 45,
        Material.RAW_COPPER to 20,
        Material.LAPIS_LAZULI to 30,
        Material.REDSTONE to 25,
        Material.COAL to 15,
        Material.COPPER_INGOT to 22,
        Material.NETHERITE_PICKAXE to 115,
        Material.DIAMOND_PICKAXE to 95,
        Material.DIAMOND_SWORD to 95,
        Material.IRON_PICKAXE to 48,
        Material.IRON_SWORD to 48,
        Material.STONE_PICKAXE to 10,
        Material.STONE_SWORD to 10,
        Material.WOODEN_PICKAXE to 3,
        Material.WOODEN_SWORD to 3,
        Material.OAK_LOG to 8,
        Material.BIRCH_LOG to 8,
        Material.SPRUCE_LOG to 8,
        Material.STICK to 2,
        Material.OAK_PLANKS to 5,
        Material.COBBLESTONE to 1,
        Material.DIRT to 0,
        Material.GRAVEL to 0,
        Material.SAND to 1,
    )

    val SMELTABLE_RAW_ORES = setOf(
        Material.RAW_IRON,
        Material.RAW_GOLD,
        Material.RAW_COPPER,
    )

    fun requiredTool(block: Block): Material? = when {
        block.compare(Block.IRON_ORE) || block.compare(Block.DEEPSLATE_IRON_ORE) ||
            block.compare(Block.LAPIS_ORE) || block.compare(Block.DEEPSLATE_LAPIS_ORE) -> Material.STONE_PICKAXE
        block.compare(Block.GOLD_ORE) || block.compare(Block.DEEPSLATE_GOLD_ORE) ||
            block.compare(Block.DIAMOND_ORE) || block.compare(Block.DEEPSLATE_DIAMOND_ORE) ||
            block.compare(Block.EMERALD_ORE) || block.compare(Block.DEEPSLATE_EMERALD_ORE) ||
            block.compare(Block.REDSTONE_ORE) || block.compare(Block.DEEPSLATE_REDSTONE_ORE) -> Material.IRON_PICKAXE
        block.compare(Block.COAL_ORE) || block.compare(Block.DEEPSLATE_COAL_ORE) ||
            block.compare(Block.COPPER_ORE) || block.compare(Block.DEEPSLATE_COPPER_ORE) ||
            block.compare(Block.STONE) || block.compare(Block.COBBLESTONE) ||
            block.compare(Block.DEEPSLATE) -> Material.WOODEN_PICKAXE
        block.compare(Block.OBSIDIAN) -> Material.DIAMOND_PICKAXE
        else -> null
    }

    fun blockDrops(block: Block): List<ItemStack> = when {
        block.compare(Block.COAL_ORE) || block.compare(Block.DEEPSLATE_COAL_ORE) ->
            listOf(ItemStack.of(Material.COAL))
        block.compare(Block.IRON_ORE) || block.compare(Block.DEEPSLATE_IRON_ORE) ->
            listOf(ItemStack.of(Material.RAW_IRON))
        block.compare(Block.GOLD_ORE) || block.compare(Block.DEEPSLATE_GOLD_ORE) ->
            listOf(ItemStack.of(Material.RAW_GOLD))
        block.compare(Block.DIAMOND_ORE) || block.compare(Block.DEEPSLATE_DIAMOND_ORE) ->
            listOf(ItemStack.of(Material.DIAMOND))
        block.compare(Block.LAPIS_ORE) || block.compare(Block.DEEPSLATE_LAPIS_ORE) ->
            listOf(ItemStack.of(Material.LAPIS_LAZULI, 5))
        block.compare(Block.REDSTONE_ORE) || block.compare(Block.DEEPSLATE_REDSTONE_ORE) ->
            listOf(ItemStack.of(Material.REDSTONE, 4))
        block.compare(Block.EMERALD_ORE) || block.compare(Block.DEEPSLATE_EMERALD_ORE) ->
            listOf(ItemStack.of(Material.EMERALD))
        block.compare(Block.COPPER_ORE) || block.compare(Block.DEEPSLATE_COPPER_ORE) ->
            listOf(ItemStack.of(Material.RAW_COPPER, 3))
        block.compare(Block.STONE) -> listOf(ItemStack.of(Material.COBBLESTONE))
        block.compare(Block.COBBLESTONE) -> listOf(ItemStack.of(Material.COBBLESTONE))
        block.compare(Block.DEEPSLATE) -> listOf(ItemStack.of(Material.COBBLED_DEEPSLATE))
        else -> emptyList()
    }

    fun breakTime(block: Block, tool: Material?): Int {
        val baseTicks = when {
            block.compare(Block.DIRT) || block.compare(Block.GRASS_BLOCK) -> 10
            block.compare(Block.SAND) || block.compare(Block.GRAVEL) -> 12
            block.compare(Block.STONE) || block.compare(Block.COBBLESTONE) -> 40
            block.compare(Block.DEEPSLATE) || block.compare(Block.COBBLED_DEEPSLATE) -> 50
            block.compare(Block.COAL_ORE) || block.compare(Block.DEEPSLATE_COAL_ORE) -> 40
            block.compare(Block.COPPER_ORE) || block.compare(Block.DEEPSLATE_COPPER_ORE) -> 45
            block.compare(Block.IRON_ORE) || block.compare(Block.DEEPSLATE_IRON_ORE) -> 50
            block.compare(Block.GOLD_ORE) || block.compare(Block.DEEPSLATE_GOLD_ORE) -> 55
            block.compare(Block.DIAMOND_ORE) || block.compare(Block.DEEPSLATE_DIAMOND_ORE) -> 60
            block.compare(Block.EMERALD_ORE) || block.compare(Block.DEEPSLATE_EMERALD_ORE) -> 55
            block.compare(Block.REDSTONE_ORE) || block.compare(Block.DEEPSLATE_REDSTONE_ORE) -> 50
            block.compare(Block.LAPIS_ORE) || block.compare(Block.DEEPSLATE_LAPIS_ORE) -> 50
            block.compare(Block.OBSIDIAN) -> 250
            else -> 30
        }
        if (tool == null) return baseTicks
        val tier = toolTier(tool)
        val speedMultiplier = when (tier) {
            0 -> 0.7f
            1 -> 0.5f
            2 -> 0.35f
            3 -> 0.25f
            4 -> 0.2f
            else -> 1f
        }
        return (baseTicks * speedMultiplier).toInt().coerceAtLeast(1)
    }

    fun isOre(block: Block): Boolean = ORE_BLOCKS.any { block.compare(it) }

    fun smeltResult(input: Material): Material? = SMELTING_RECIPES[input]

    fun toolTier(material: Material): Int = TOOL_TIERS[material] ?: -1

    fun canMine(tool: Material?, block: Block): Boolean {
        val required = requiredTool(block) ?: return true
        val requiredTier = toolTier(required)
        val toolTierVal = if (tool != null) toolTier(tool) else -1
        return toolTierVal >= requiredTier
    }

    fun itemValue(material: Material): Int = ITEM_VALUES[material] ?: 2

    fun materialToOre(material: Material): Block? = when (material) {
        Material.IRON_INGOT, Material.RAW_IRON -> Block.IRON_ORE
        Material.GOLD_INGOT, Material.RAW_GOLD -> Block.GOLD_ORE
        Material.DIAMOND -> Block.DIAMOND_ORE
        Material.COAL -> Block.COAL_ORE
        Material.EMERALD -> Block.EMERALD_ORE
        Material.LAPIS_LAZULI -> Block.LAPIS_ORE
        Material.REDSTONE -> Block.REDSTONE_ORE
        Material.RAW_COPPER, Material.COPPER_INGOT -> Block.COPPER_ORE
        else -> null
    }

    fun isPickaxe(material: Material): Boolean = material in setOf(
        Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
        Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE, Material.GOLDEN_PICKAXE,
    )

    fun bestPickaxe(materials: Set<Material>): Material? =
        materials.filter { isPickaxe(it) }.maxByOrNull { toolTier(it) }
}
