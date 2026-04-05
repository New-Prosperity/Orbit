package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.recipe.Ingredient
import net.minestom.server.recipe.Recipe
import net.minestom.server.recipe.RecipeBookCategory
import net.minestom.server.recipe.display.RecipeDisplay
import net.minestom.server.recipe.display.SlotDisplay
import java.time.Duration

private sealed interface CraftingRecipe : Recipe {
    val result: ItemStack
    fun matches(grid: Array<Material?>): Boolean

    data class Shaped(
        val width: Int,
        val height: Int,
        val pattern: List<Material?>,
        override val result: ItemStack,
    ) : CraftingRecipe {

        override fun matches(grid: Array<Material?>): Boolean {
            for (sx in 0..(3 - width)) {
                for (sy in 0..(3 - height)) {
                    if (matchAt(grid, sx, sy)) return true
                }
            }
            return false
        }

        private fun matchAt(grid: Array<Material?>, sx: Int, sy: Int): Boolean {
            for (y in 0 until 3) {
                for (x in 0 until 3) {
                    val inRecipe = x in sx until sx + width && y in sy until sy + height
                    if (inRecipe) {
                        if (pattern[(y - sy) * width + (x - sx)] != grid[y * 3 + x]) return false
                    } else {
                        if (grid[y * 3 + x] != null) return false
                    }
                }
            }
            return true
        }

        override fun createRecipeDisplays(): List<RecipeDisplay> = listOf(
            RecipeDisplay.CraftingShaped(
                width, height,
                pattern.map { mat -> mat?.let { SlotDisplay.Item(it) } ?: SlotDisplay.Empty.INSTANCE as SlotDisplay },
                SlotDisplay.ItemStack(result),
                SlotDisplay.Item(Material.CRAFTING_TABLE)
            )
        )

        override fun recipeBookCategory(): RecipeBookCategory = RecipeBookCategory.CRAFTING_MISC

        override fun craftingRequirements(): List<Ingredient> =
            pattern.filterNotNull().distinct().map { Ingredient(it) }
    }

    data class Shapeless(
        val ingredients: List<Material>,
        override val result: ItemStack,
    ) : CraftingRecipe {

        override fun matches(grid: Array<Material?>): Boolean {
            val remaining = ingredients.toMutableList()
            for (mat in grid) {
                if (mat != null && !remaining.remove(mat)) return false
            }
            return remaining.isEmpty()
        }

        override fun createRecipeDisplays(): List<RecipeDisplay> = listOf(
            RecipeDisplay.CraftingShapeless(
                ingredients.map { SlotDisplay.Item(it) as SlotDisplay },
                SlotDisplay.ItemStack(result),
                SlotDisplay.Item(Material.CRAFTING_TABLE)
            )
        )

        override fun recipeBookCategory(): RecipeBookCategory = RecipeBookCategory.CRAFTING_MISC

        override fun craftingRequirements(): List<Ingredient> =
            listOf(Ingredient(ingredients))
    }
}

private fun shaped(w: Int, h: Int, vararg materials: Material?, result: ItemStack): CraftingRecipe.Shaped =
    CraftingRecipe.Shaped(w, h, materials.toList(), result)

private fun shapeless(vararg ingredients: Material, result: ItemStack): CraftingRecipe.Shapeless =
    CraftingRecipe.Shapeless(ingredients.toList(), result)

private fun item(material: Material, count: Int = 1): ItemStack = ItemStack.of(material, count)

private val RECIPES: List<CraftingRecipe> = listOf(
    shaped(1, 1, Material.OAK_LOG, result = item(Material.OAK_PLANKS, 4)),
    shaped(1, 1, Material.SPRUCE_LOG, result = item(Material.SPRUCE_PLANKS, 4)),
    shaped(1, 1, Material.BIRCH_LOG, result = item(Material.BIRCH_PLANKS, 4)),
    shaped(1, 1, Material.JUNGLE_LOG, result = item(Material.JUNGLE_PLANKS, 4)),
    shaped(1, 1, Material.ACACIA_LOG, result = item(Material.ACACIA_PLANKS, 4)),
    shaped(1, 1, Material.DARK_OAK_LOG, result = item(Material.DARK_OAK_PLANKS, 4)),
    shaped(1, 1, Material.CHERRY_LOG, result = item(Material.CHERRY_PLANKS, 4)),
    shaped(1, 1, Material.MANGROVE_LOG, result = item(Material.MANGROVE_PLANKS, 4)),
    shaped(1, 2, Material.OAK_PLANKS, Material.OAK_PLANKS, result = item(Material.STICK, 4)),
    shaped(2, 2, Material.OAK_PLANKS, Material.OAK_PLANKS, Material.OAK_PLANKS, Material.OAK_PLANKS, result = item(Material.CRAFTING_TABLE)),
    shaped(2, 3, Material.OAK_PLANKS, Material.OAK_PLANKS, null, null, Material.OAK_PLANKS, Material.OAK_PLANKS, result = item(Material.OAK_DOOR, 3)),
    shaped(3, 3, Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE, null, Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE, result = item(Material.FURNACE)),
    shaped(3, 3, Material.OAK_PLANKS, Material.OAK_PLANKS, Material.OAK_PLANKS, null, null, null, Material.OAK_PLANKS, Material.OAK_PLANKS, Material.OAK_PLANKS, result = item(Material.CHEST)),
    shaped(3, 2, Material.IRON_INGOT, null, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, result = item(Material.BUCKET)),
    shaped(1, 2, Material.COAL, Material.STICK, result = item(Material.TORCH, 4)),
    shaped(1, 2, Material.CHARCOAL, Material.STICK, result = item(Material.TORCH, 4)),
    shaped(3, 3, Material.OAK_PLANKS, Material.OAK_PLANKS, Material.OAK_PLANKS, null, Material.STICK, null, null, Material.STICK, null, result = item(Material.WOODEN_PICKAXE)),
    shaped(3, 3, Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE, null, Material.STICK, null, null, Material.STICK, null, result = item(Material.STONE_PICKAXE)),
    shaped(3, 3, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, null, Material.STICK, null, null, Material.STICK, null, result = item(Material.IRON_PICKAXE)),
    shaped(3, 3, Material.GOLD_INGOT, Material.GOLD_INGOT, Material.GOLD_INGOT, null, Material.STICK, null, null, Material.STICK, null, result = item(Material.GOLDEN_PICKAXE)),
    shaped(3, 3, Material.DIAMOND, Material.DIAMOND, Material.DIAMOND, null, Material.STICK, null, null, Material.STICK, null, result = item(Material.DIAMOND_PICKAXE)),
    shaped(2, 3, Material.OAK_PLANKS, Material.OAK_PLANKS, null, Material.STICK, null, Material.STICK, result = item(Material.WOODEN_AXE)),
    shaped(2, 3, Material.COBBLESTONE, Material.COBBLESTONE, null, Material.STICK, null, Material.STICK, result = item(Material.STONE_AXE)),
    shaped(2, 3, Material.IRON_INGOT, Material.IRON_INGOT, null, Material.STICK, null, Material.STICK, result = item(Material.IRON_AXE)),
    shaped(2, 3, Material.GOLD_INGOT, Material.GOLD_INGOT, null, Material.STICK, null, Material.STICK, result = item(Material.GOLDEN_AXE)),
    shaped(2, 3, Material.DIAMOND, Material.DIAMOND, null, Material.STICK, null, Material.STICK, result = item(Material.DIAMOND_AXE)),
    shaped(1, 3, Material.OAK_PLANKS, Material.STICK, Material.STICK, result = item(Material.WOODEN_SHOVEL)),
    shaped(1, 3, Material.COBBLESTONE, Material.STICK, Material.STICK, result = item(Material.STONE_SHOVEL)),
    shaped(1, 3, Material.IRON_INGOT, Material.STICK, Material.STICK, result = item(Material.IRON_SHOVEL)),
    shaped(1, 3, Material.GOLD_INGOT, Material.STICK, Material.STICK, result = item(Material.GOLDEN_SHOVEL)),
    shaped(1, 3, Material.DIAMOND, Material.STICK, Material.STICK, result = item(Material.DIAMOND_SHOVEL)),
    shaped(1, 3, Material.OAK_PLANKS, Material.STICK, Material.OAK_PLANKS, result = item(Material.WOODEN_HOE)),
    shaped(1, 2, Material.OAK_PLANKS, Material.STICK, result = item(Material.WOODEN_SWORD)),
    shaped(1, 2, Material.COBBLESTONE, Material.STICK, result = item(Material.STONE_SWORD)),
    shaped(1, 2, Material.IRON_INGOT, Material.STICK, result = item(Material.IRON_SWORD)),
    shaped(1, 2, Material.GOLD_INGOT, Material.STICK, result = item(Material.GOLDEN_SWORD)),
    shaped(1, 2, Material.DIAMOND, Material.STICK, result = item(Material.DIAMOND_SWORD)),
    shaped(3, 2, Material.STICK, Material.STRING, Material.STICK, null, Material.STRING, null, Material.STICK, Material.STRING, null, result = item(Material.BOW)),
    shaped(3, 1, Material.STRING, Material.STRING, Material.STRING, result = item(Material.STRING, 3)),
    shaped(3, 3, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, null, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, result = item(Material.IRON_CHESTPLATE)),
    shaped(3, 3, Material.IRON_INGOT, null, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, result = item(Material.IRON_LEGGINGS)),
    shaped(3, 2, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, null, Material.IRON_INGOT, result = item(Material.IRON_HELMET)),
    shaped(3, 2, Material.IRON_INGOT, null, null, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, result = item(Material.IRON_BOOTS)),
    shaped(3, 3, Material.DIAMOND, Material.DIAMOND, Material.DIAMOND, Material.DIAMOND, null, Material.DIAMOND, Material.DIAMOND, Material.DIAMOND, Material.DIAMOND, result = item(Material.DIAMOND_CHESTPLATE)),
    shaped(3, 2, Material.DIAMOND, Material.DIAMOND, Material.DIAMOND, Material.DIAMOND, null, Material.DIAMOND, result = item(Material.DIAMOND_HELMET)),
    shaped(2, 1, Material.IRON_INGOT, Material.FLINT, result = item(Material.FLINT_AND_STEEL)),
    shaped(3, 3, null, Material.IRON_INGOT, null, null, Material.IRON_INGOT, null, null, Material.IRON_INGOT, null, result = item(Material.IRON_BARS, 16)),
    shaped(1, 2, Material.IRON_INGOT, Material.STICK, result = item(Material.ARROW, 4)),
    shaped(3, 2, Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE, null, null, null, result = item(Material.STONE_SLAB, 6)),
    shaped(3, 3, null, null, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, null, null, Material.IRON_INGOT, result = item(Material.SHIELD)),
    shaped(2, 2, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, result = item(Material.IRON_BLOCK)),
    shaped(2, 2, Material.GOLD_INGOT, Material.GOLD_INGOT, Material.GOLD_INGOT, Material.GOLD_INGOT, result = item(Material.GOLD_BLOCK)),
    shaped(2, 2, Material.DIAMOND, Material.DIAMOND, Material.DIAMOND, Material.DIAMOND, result = item(Material.DIAMOND_BLOCK)),
    shaped(1, 1, Material.IRON_BLOCK, result = item(Material.IRON_INGOT, 9)),
    shaped(1, 1, Material.GOLD_BLOCK, result = item(Material.GOLD_INGOT, 9)),
    shaped(1, 1, Material.DIAMOND_BLOCK, result = item(Material.DIAMOND, 9)),
    shapeless(Material.OAK_PLANKS, result = item(Material.OAK_BUTTON)),
    shapeless(Material.SUGAR_CANE, result = item(Material.SUGAR)),
    shapeless(Material.WHEAT, Material.WHEAT, Material.WHEAT, result = item(Material.BREAD)),
    shapeless(Material.PUMPKIN, result = item(Material.PUMPKIN_SEEDS, 4)),
    shapeless(Material.MELON_SLICE, result = item(Material.MELON_SEEDS)),
)

object CraftingModule : VanillaModule {

    override val id = "crafting"
    override val description = "Open crafting table for 3x3 crafting grid with common recipes"

    private var registered = false

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        if (!registered) {
            val manager = MinecraftServer.getRecipeManager()
            RECIPES.forEach(manager::addRecipe)
            registered = true
        }

        val node = EventNode.all("vanilla-crafting")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:crafting_table") return@addListener
            val inv = Inventory(InventoryType.CRAFTING, Component.text("Crafting"))
            event.player.openInventory(inv)
        }

        node.addListener(InventoryPreClickEvent::class.java) { event ->
            val inv = event.inventory as? Inventory ?: return@addListener
            if (inv.inventoryType != InventoryType.CRAFTING) return@addListener

            if (event.slot == 0) {
                val result = inv.getItemStack(0)
                if (!result.isAir) {
                    for (i in 1..9) {
                        val stack = inv.getItemStack(i)
                        if (!stack.isAir) {
                            inv.setItemStack(i, if (stack.amount() > 1) stack.withAmount(stack.amount() - 1) else ItemStack.AIR)
                        }
                    }
                }
            }

            val inst = event.player.instance ?: return@addListener
            inst.scheduler().buildTask {
                val result = matchRecipe(inv)
                inv.setItemStack(0, result ?: ItemStack.AIR)
            }.delay(Duration.ofMillis(50)).schedule()
        }

        node.addListener(InventoryCloseEvent::class.java) { event ->
            val inv = event.inventory as? Inventory ?: return@addListener
            if (inv.inventoryType != InventoryType.CRAFTING) return@addListener
            val player = event.player
            for (i in 1..9) {
                val stack = inv.getItemStack(i)
                if (!stack.isAir) {
                    if (!player.inventory.addItemStack(stack)) {
                        val itemEntity = ItemEntity(stack)
                        itemEntity.setInstance(player.instance ?: return@addListener, player.position)
                    }
                }
            }
        }

        return node
    }

    private fun matchRecipe(inv: Inventory): ItemStack? {
        val grid = Array(9) { i -> inv.getItemStack(i + 1).material().takeIf { !inv.getItemStack(i + 1).isAir } }
        return RECIPES.firstOrNull { it.matches(grid) }?.result
    }
}
