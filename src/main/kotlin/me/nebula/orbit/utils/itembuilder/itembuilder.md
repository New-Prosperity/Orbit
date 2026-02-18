# Item Builder

DSL for building `ItemStack` instances with name, lore, custom model data, unbreakable, and glowing enchant effect. Uses MiniMessage formatting.

## Key Classes

- **`ItemBuilder`** -- DSL builder for `ItemStack`

## Usage

```kotlin
val sword = itemStack(Material.DIAMOND_SWORD) {
    name("<red>Flame Sword")
    lore("<gray>A powerful weapon")
    lore("<yellow>+10 Attack Damage")
    unbreakable()
    glowing()
}

val apples = itemStack(Material.GOLDEN_APPLE, 16)

val custom = itemStack(Material.PAPER) {
    name("<green>Ticket")
    lore(listOf("<gray>Right-click to use", "<gray>One-time use"))
    customModelData(42)
}
```

## Builder Methods

| Method | Description |
|--------|-------------|
| `amount(n)` | Stack size |
| `name(text)` | Display name (MiniMessage string or Component) |
| `lore(text)` | Add a lore line (MiniMessage string or Component) |
| `lore(lines)` | Add multiple lore lines |
| `customModelData(value)` | Set custom model data |
| `unbreakable()` | Mark as unbreakable |
| `glowing()` | Add enchantment glint without enchantments |
