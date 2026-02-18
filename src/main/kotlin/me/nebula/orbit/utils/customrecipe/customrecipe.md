# CustomRecipe

Custom recipe registration and matching system for shaped, shapeless, and smelting recipes.

## Key Classes

- **`RecipeHandle`** -- unique identifier for unregistering recipes
- **`ShapedRecipe`** / **`ShapelessRecipe`** / **`SmeltingRecipe`** -- sealed recipe types
- **`RecipeRegistry`** -- central registry with matching logic
- **`ShapedRecipeBuilder`** / **`ShapelessRecipeBuilder`** -- DSL builders

## Usage

```kotlin
val handle = shapedRecipe(ItemStack.of(Material.DIAMOND_SWORD)) {
    pattern("D", "D", "S")
    ingredient('D', Material.DIAMOND)
    ingredient('S', Material.STICK)
}

val handle2 = shapelessRecipe(ItemStack.of(Material.BOOK)) {
    ingredient(Material.PAPER, 3)
    ingredient(Material.LEATHER)
}

val handle3 = smeltingRecipe(Material.IRON_ORE, ItemStack.of(Material.IRON_INGOT), 200)

RecipeRegistry.matchShaped(grid)
RecipeRegistry.matchShapeless(listOf(Material.PAPER, Material.PAPER, Material.PAPER, Material.LEATHER))
RecipeRegistry.matchSmelting(Material.IRON_ORE)

RecipeRegistry.unregister(handle)
```

## Details

- Shaped recipes support offset matching (recipe can be placed anywhere in the grid)
- Shapeless matching uses sorted material comparison
- Smelting returns result + cook time as a pair
- Thread-safe via ConcurrentHashMap
- Atomic ID generation for handles
