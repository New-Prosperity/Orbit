# InventoryLayout

Declarative inventory GUI builder with borders, patterns, centered items, and click handlers. Auto-cleans event listeners on close.

## DSL

```kotlin
val layout = inventoryLayout {
    title = Component.text("Shop")
    type = InventoryType.CHEST_3_ROW
    fillEmpty = fillerItem()
    cancelClicks = true

    border(fillerItem(Material.BLACK_STAINED_GLASS_PANE))

    slot(13, ItemStack.of(Material.DIAMOND)) { player, slot ->
        player.sendMM("<green>Purchased diamond!")
    }
}
```

## API

| Method | Description |
|---|---|
| `open(player)` | Create inventory, populate slots, register events, and open for player |

## Builder Methods

| Method | Description |
|---|---|
| `slot(index, item, onClick?)` | Set item at a specific slot with optional click handler |
| `border(item)` | Fill border slots of the inventory |
| `row(rowIndex, item, onClick?)` | Fill an entire row |
| `column(colIndex, item, onClick?)` | Fill an entire column |
| `pattern(lines, mappings)` | Place items by character pattern |
| `centerItems(items, rowIndex)` | Center a list of items in a row |

## Utility

`fillerItem(material)` creates a named-empty glass pane (default `GRAY_STAINED_GLASS_PANE`).

## Example

```kotlin
val menu = inventoryLayout {
    title = mm("<gradient:gold:yellow>Game Menu")
    type = InventoryType.CHEST_6_ROW
    fillEmpty = fillerItem()
    border(fillerItem(Material.BLACK_STAINED_GLASS_PANE))

    centerItems(listOf(
        ItemStack.of(Material.IRON_SWORD) to { p, _ -> joinGame(p) },
        ItemStack.of(Material.BOOK) to { p, _ -> openStats(p) },
        ItemStack.of(Material.EMERALD) to { p, _ -> openShop(p) },
    ), rowIndex = 2)

    pattern(
        listOf(
            "         ",
            "  A B C  ",
            "         ",
        ),
        mapOf(
            'A' to LayoutSlot(ItemStack.of(Material.DIAMOND_SWORD)) { p, _ -> kit1(p) },
            'B' to LayoutSlot(ItemStack.of(Material.BOW)) { p, _ -> kit2(p) },
            'C' to LayoutSlot(ItemStack.of(Material.GOLDEN_APPLE)) { p, _ -> kit3(p) },
        ),
    )
}

menu.open(player)
```
