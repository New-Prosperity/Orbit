# GUI

DSL for building chest inventory GUIs with click handlers, fill/border decorations, and pagination.

## Key Classes

- **`Gui`** — static inventory with slot click handlers
- **`PaginatedGui`** — auto-paginated inventory with arrow navigation
- **`GuiBuilder`** / **`PaginatedGuiBuilder`** — DSL builders
- **`GuiSlot`** — item + click handler pair

## Usage

```kotlin
val menu = gui("<gold>Shop", rows = 3) {
    fill(Material.GRAY_STAINED_GLASS_PANE)
    border(Material.BLACK_STAINED_GLASS_PANE)
    slot(13, ItemStack.of(Material.DIAMOND)) { player -> player.sendMessage("Bought!") }
    onClose { player -> player.sendMessage("Closed") }
}
player.openGui(menu)
```

## Paginated GUI

```kotlin
val paginated = paginatedGui("<green>Items", rows = 6) {
    fill(Material.GRAY_STAINED_GLASS_PANE)
    border(Material.BLACK_STAINED_GLASS_PANE)
    contentSlots(10..43)
    items(myItems, { ItemStack.of(it.material) }) { player, item -> player.sendMessage(item.name) }
    staticSlot(49, ItemStack.of(Material.BARRIER)) { it.closeInventory() }
}
paginated.open(player, page = 0)
```

Arrow navigation items are auto-placed at bottom-left (previous) and bottom-right (next).
