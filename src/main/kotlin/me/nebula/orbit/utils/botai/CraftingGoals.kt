package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

class CraftToolGoal(private val tool: Material) : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        if (brain.hasItem(tool)) return 0f
        val recipe = recipeFor(tool) ?: return 0f
        val hasMaterials = recipe.all { (mat, count) -> brain.countItem(mat) >= count }
        return if (hasMaterials) 0.6f else 0.3f
    }

    override fun shouldActivate(brain: BotBrain): Boolean {
        if (brain.hasItem(tool)) return false
        val recipe = recipeFor(tool) ?: return false
        return recipe.all { (mat, count) -> brain.countItem(mat) >= count }
    }

    override fun createActions(brain: BotBrain): List<BotAction> {
        val recipe = recipeFor(tool) ?: return listOf(Wait(10))
        val tablePos = brain.vision.canSee(Block.CRAFTING_TABLE)?.pos
            ?: brain.memory.nearestRecalled("crafting_table", brain.player.position)
        if (tablePos != null) brain.memory.rememberLocation("crafting_table", tablePos)
        return listOf(CraftItem(ItemStack.of(tool), recipe, tablePos))
    }

    override fun shouldCancel(brain: BotBrain): Boolean = brain.hasItem(tool)

    private fun recipeFor(material: Material): List<Pair<Material, Int>>? = when (material) {
        Material.WOODEN_SWORD -> listOf(Material.OAK_PLANKS to 2, Material.STICK to 1)
        Material.WOODEN_PICKAXE -> listOf(Material.OAK_PLANKS to 3, Material.STICK to 2)
        Material.WOODEN_AXE -> listOf(Material.OAK_PLANKS to 3, Material.STICK to 2)
        Material.WOODEN_SHOVEL -> listOf(Material.OAK_PLANKS to 1, Material.STICK to 2)
        Material.STONE_SWORD -> listOf(Material.COBBLESTONE to 2, Material.STICK to 1)
        Material.STONE_PICKAXE -> listOf(Material.COBBLESTONE to 3, Material.STICK to 2)
        Material.STONE_AXE -> listOf(Material.COBBLESTONE to 3, Material.STICK to 2)
        Material.IRON_SWORD -> listOf(Material.IRON_INGOT to 2, Material.STICK to 1)
        Material.IRON_PICKAXE -> listOf(Material.IRON_INGOT to 3, Material.STICK to 2)
        Material.DIAMOND_SWORD -> listOf(Material.DIAMOND to 2, Material.STICK to 1)
        Material.DIAMOND_PICKAXE -> listOf(Material.DIAMOND to 3, Material.STICK to 2)
        Material.CRAFTING_TABLE -> listOf(Material.OAK_PLANKS to 4)
        Material.STICK -> listOf(Material.OAK_PLANKS to 2)
        Material.OAK_PLANKS -> listOf(Material.OAK_LOG to 1)
        else -> null
    }
}

class ToolProgressionGoal : BotGoal() {

    private val pickaxeTiers = listOf(
        Material.DIAMOND_PICKAXE to listOf(Material.DIAMOND to 3, Material.STICK to 2),
        Material.IRON_PICKAXE to listOf(Material.IRON_INGOT to 3, Material.STICK to 2),
        Material.STONE_PICKAXE to listOf(Material.COBBLESTONE to 3, Material.STICK to 2),
        Material.WOODEN_PICKAXE to listOf(Material.OAK_PLANKS to 3, Material.STICK to 2),
    )

    private val swordTiers = listOf(
        Material.DIAMOND_SWORD to listOf(Material.DIAMOND to 2, Material.STICK to 1),
        Material.IRON_SWORD to listOf(Material.IRON_INGOT to 2, Material.STICK to 1),
        Material.STONE_SWORD to listOf(Material.COBBLESTONE to 2, Material.STICK to 1),
        Material.WOODEN_SWORD to listOf(Material.OAK_PLANKS to 2, Material.STICK to 1),
    )

    private val axeTiers = listOf(
        Material.DIAMOND_AXE to listOf(Material.DIAMOND to 3, Material.STICK to 2),
        Material.IRON_AXE to listOf(Material.IRON_INGOT to 3, Material.STICK to 2),
        Material.STONE_AXE to listOf(Material.COBBLESTONE to 3, Material.STICK to 2),
        Material.WOODEN_AXE to listOf(Material.OAK_PLANKS to 3, Material.STICK to 2),
    )

    override fun calculateUtility(brain: BotBrain): Float {
        val canUpgrade = findBestCraftable(brain, pickaxeTiers) != null ||
            findBestCraftable(brain, swordTiers) != null ||
            findBestCraftable(brain, axeTiers) != null
        if (!canUpgrade) return 0f
        return 0.65f * (brain.personality.resourcefulness + 0.5f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean =
        findBestCraftable(brain, pickaxeTiers) != null ||
            findBestCraftable(brain, swordTiers) != null ||
            findBestCraftable(brain, axeTiers) != null

    override fun createActions(brain: BotBrain): List<BotAction> {
        val tablePos = brain.vision.canSee(Block.CRAFTING_TABLE)?.pos
            ?: brain.memory.nearestRecalled("crafting_table", brain.player.position)
        val actions = mutableListOf<BotAction>()
        for (tiers in listOf(pickaxeTiers, swordTiers, axeTiers)) {
            val upgrade = findBestCraftable(brain, tiers)
            if (upgrade != null) {
                val (material, recipe) = upgrade
                actions.add(CraftItem(ItemStack.of(material), recipe, tablePos))
            }
        }
        return actions.ifEmpty { listOf(Wait(10)) }
    }

    private fun findBestCraftable(
        brain: BotBrain,
        tiers: List<Pair<Material, List<Pair<Material, Int>>>>,
    ): Pair<Material, List<Pair<Material, Int>>>? {
        val currentBest = tiers.indexOfFirst { (mat, _) -> brain.hasItem(mat) }
        for ((index, entry) in tiers.withIndex()) {
            if (currentBest in 0..index) continue
            val (material, recipe) = entry
            val canCraft = recipe.all { (mat, count) -> brain.countItem(mat) >= count }
            if (canCraft) return material to recipe
        }
        return null
    }
}

class SmeltOresGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val hasRawOres = MiningKnowledge.SMELTABLE_RAW_ORES.any { brain.hasItem(it) }
        if (!hasRawOres) return 0f
        return 0.5f * (brain.personality.resourcefulness + 0.5f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean =
        MiningKnowledge.SMELTABLE_RAW_ORES.any { brain.hasItem(it) } &&
            (brain.hasItem(Material.COAL) || brain.hasItem(Material.CHARCOAL))

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val furnacePos = brain.vision.canSee(Block.FURNACE)?.pos
            ?: brain.memory.nearestRecalled("furnace", player.position)
        if (furnacePos == null) return listOf(Wait(20))
        brain.memory.rememberLocation("furnace", furnacePos)
        val fuel = if (brain.hasItem(Material.COAL)) Material.COAL else Material.CHARCOAL
        val actions = mutableListOf<BotAction>()
        for (ore in MiningKnowledge.SMELTABLE_RAW_ORES) {
            if (brain.hasItem(ore)) {
                actions.add(SmeltItems(furnacePos, ore, fuel))
            }
        }
        return actions.ifEmpty { listOf(Wait(10)) }
    }
}

class PlaceFurnaceGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val hasRawOres = MiningKnowledge.SMELTABLE_RAW_ORES.any { brain.hasItem(it) }
        val hasCobble = brain.countItem(Material.COBBLESTONE) >= 8
        val hasFurnace = brain.vision.canSee(Block.FURNACE) != null ||
            brain.memory.nearestRecalled("furnace", brain.player.position) != null
        if (!hasRawOres || !hasCobble || hasFurnace) return 0f
        return 0.5f
    }

    override fun shouldActivate(brain: BotBrain): Boolean {
        val hasRawOres = MiningKnowledge.SMELTABLE_RAW_ORES.any { brain.hasItem(it) }
        val hasCobble = brain.countItem(Material.COBBLESTONE) >= 8
        val noFurnace = brain.vision.canSee(Block.FURNACE) == null &&
            brain.memory.nearestRecalled("furnace", brain.player.position) == null
        return hasRawOres && hasCobble && noFurnace
    }

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val furnaceRecipe = listOf(Material.COBBLESTONE to 8)
        val placePos = Pos(
            player.position.blockX() + 1.0,
            player.position.blockY().toDouble(),
            player.position.blockZ().toDouble(),
        )
        brain.memory.rememberLocation("furnace", placePos)
        return listOf(
            CraftItem(ItemStack.of(Material.FURNACE), furnaceRecipe, null),
            PlaceBlock(placePos, Block.FURNACE),
        )
    }
}
