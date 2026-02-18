# Loot Table

DSL for defining weighted loot tables with configurable roll counts and per-entry amount ranges.

## Key Classes

- **`LootTable`** -- named table with weighted entries and roll count
- **`LootEntry`** -- item with weight, min/max count
- **`LootTableBuilder`** -- DSL builder

## Usage

### Create

```kotlin
val chest = lootTable("dungeon-chest") {
    rolls(2..4)
    entry(Material.IRON_INGOT, weight = 10, minCount = 1, maxCount = 3)
    entry(Material.DIAMOND, weight = 2)
    entry(ItemStack.of(Material.GOLDEN_APPLE), weight = 5, maxCount = 2)
}
```

### Roll

```kotlin
val items: List<ItemStack> = chest.roll()

val single: ItemStack? = chest.rollSingle()
```

## API

| Method | Description |
|--------|-------------|
| `roll()` | Roll the table N times (based on rolls range), returns list of items |
| `rollSingle()` | Single weighted roll, returns one item or null if empty |

Each entry's weight determines selection probability. Item count is randomized between `minCount` and `maxCount` per roll.
