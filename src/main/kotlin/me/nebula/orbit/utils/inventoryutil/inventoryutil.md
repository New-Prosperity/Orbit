# InventoryUtil

Player inventory extension functions for common item operations.

## Usage

```kotlin
if (player.hasItem(Material.DIAMOND)) {
    player.removeItem(Material.DIAMOND, 5)
}

val count = player.countItem(Material.IRON_INGOT)
player.giveItem(ItemStack.of(Material.GOLDEN_APPLE, 3))
player.sortInventory()
player.swapSlots(0, 8)
player.clearInventory()
```

## Key API

- `Player.hasItem(material)` — check if any slot contains the material
- `Player.countItem(material)` — total count of a material across all slots
- `Player.removeItem(material, amount)` — remove items, returns `true` if enough were found
- `Player.giveItem(item)` — add item to inventory
- `Player.firstEmptySlot()` — index of first empty slot or `null`
- `Player.clearInventory()` — set all slots to AIR
- `Player.sortInventory()` — sort items alphabetically by material key
- `Player.swapSlots(slot1, slot2)` — swap contents of two slots
