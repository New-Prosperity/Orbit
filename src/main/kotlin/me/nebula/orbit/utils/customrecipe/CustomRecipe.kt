package me.nebula.orbit.utils.customrecipe

import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class RecipeHandle(val id: Long)

sealed interface Recipe {
    val handle: RecipeHandle
    val result: ItemStack
}

data class ShapedRecipe(
    override val handle: RecipeHandle,
    override val result: ItemStack,
    val pattern: List<String>,
    val ingredients: Map<Char, Material>,
    val width: Int,
    val height: Int,
) : Recipe

data class ShapelessRecipe(
    override val handle: RecipeHandle,
    override val result: ItemStack,
    val ingredients: List<Material>,
) : Recipe

data class SmeltingRecipe(
    override val handle: RecipeHandle,
    override val result: ItemStack,
    val input: Material,
    val cookTime: Int,
) : Recipe

class ShapedRecipeBuilder @PublishedApi internal constructor(
    @PublishedApi internal val result: ItemStack,
) {
    @PublishedApi internal val patternLines = mutableListOf<String>()
    @PublishedApi internal val ingredients = mutableMapOf<Char, Material>()

    fun pattern(vararg lines: String) {
        patternLines.clear()
        patternLines.addAll(lines)
    }

    fun ingredient(key: Char, material: Material) {
        ingredients[key] = material
    }

    @PublishedApi internal fun build(): ShapedRecipe {
        require(patternLines.isNotEmpty()) { "Shaped recipe requires a pattern" }
        require(patternLines.size <= 3) { "Pattern max 3 rows" }
        require(patternLines.all { it.length <= 3 }) { "Pattern max 3 columns" }
        val width = patternLines.maxOf { it.length }
        val height = patternLines.size
        return ShapedRecipe(
            handle = RecipeRegistry.nextHandle(),
            result = result,
            pattern = patternLines.toList(),
            ingredients = ingredients.toMap(),
            width = width,
            height = height,
        )
    }
}

class ShapelessRecipeBuilder @PublishedApi internal constructor(
    @PublishedApi internal val result: ItemStack,
) {
    @PublishedApi internal val ingredients = mutableListOf<Material>()

    fun ingredient(material: Material, count: Int = 1) {
        repeat(count) { ingredients += material }
    }

    @PublishedApi internal fun build(): ShapelessRecipe {
        require(ingredients.isNotEmpty()) { "Shapeless recipe requires at least one ingredient" }
        return ShapelessRecipe(
            handle = RecipeRegistry.nextHandle(),
            result = result,
            ingredients = ingredients.toList(),
        )
    }
}

object RecipeRegistry {

    private val nextId = AtomicLong(1)
    private val recipes = ConcurrentHashMap<Long, Recipe>()

    fun nextHandle(): RecipeHandle = RecipeHandle(nextId.getAndIncrement())

    fun register(recipe: Recipe) {
        recipes[recipe.handle.id] = recipe
    }

    fun unregister(handle: RecipeHandle) {
        recipes.remove(handle.id)
    }

    fun get(handle: RecipeHandle): Recipe? = recipes[handle.id]

    fun all(): Collection<Recipe> = recipes.values

    fun matchShaped(grid: Array<Array<Material?>>): ItemStack? {
        val height = grid.size
        if (height == 0) return null
        val width = grid[0].size

        for (recipe in recipes.values) {
            if (recipe !is ShapedRecipe) continue
            if (matchesShapedAt(grid, recipe, width, height)) return recipe.result
        }
        return null
    }

    fun matchShapeless(materials: List<Material>): ItemStack? {
        val sorted = materials.sortedBy { it.name() }
        for (recipe in recipes.values) {
            if (recipe !is ShapelessRecipe) continue
            if (recipe.ingredients.sortedBy { it.name() } == sorted) return recipe.result
        }
        return null
    }

    fun matchSmelting(input: Material): Pair<ItemStack, Int>? {
        for (recipe in recipes.values) {
            if (recipe !is SmeltingRecipe) continue
            if (recipe.input == input) return recipe.result to recipe.cookTime
        }
        return null
    }

    private fun matchesShapedAt(
        grid: Array<Array<Material?>>,
        recipe: ShapedRecipe,
        gridWidth: Int,
        gridHeight: Int,
    ): Boolean {
        for (offsetY in 0..(gridHeight - recipe.height)) {
            for (offsetX in 0..(gridWidth - recipe.width)) {
                if (matchesAtOffset(grid, recipe, offsetX, offsetY, gridWidth, gridHeight)) return true
            }
        }
        return false
    }

    private fun matchesAtOffset(
        grid: Array<Array<Material?>>,
        recipe: ShapedRecipe,
        offsetX: Int,
        offsetY: Int,
        gridWidth: Int,
        gridHeight: Int,
    ): Boolean {
        for (y in 0 until gridHeight) {
            for (x in 0 until gridWidth) {
                val recipeY = y - offsetY
                val recipeX = x - offsetX
                val expected = if (recipeY in recipe.pattern.indices && recipeX in 0 until recipe.pattern[recipeY].length) {
                    val c = recipe.pattern[recipeY][recipeX]
                    if (c == ' ') null else recipe.ingredients[c]
                } else {
                    null
                }
                val actual = grid[y][x]
                if (expected != actual) return false
            }
        }
        return true
    }
}

inline fun shapedRecipe(result: ItemStack, block: ShapedRecipeBuilder.() -> Unit): RecipeHandle {
    val recipe = ShapedRecipeBuilder(result).apply(block).build()
    RecipeRegistry.register(recipe)
    return recipe.handle
}

inline fun shapelessRecipe(result: ItemStack, block: ShapelessRecipeBuilder.() -> Unit): RecipeHandle {
    val recipe = ShapelessRecipeBuilder(result).apply(block).build()
    RecipeRegistry.register(recipe)
    return recipe.handle
}

fun smeltingRecipe(input: Material, result: ItemStack, cookTime: Int): RecipeHandle {
    val handle = RecipeRegistry.nextHandle()
    val recipe = SmeltingRecipe(handle, result, input, cookTime)
    RecipeRegistry.register(recipe)
    return handle
}
