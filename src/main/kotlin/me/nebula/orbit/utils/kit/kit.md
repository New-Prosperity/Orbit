# Kit

DSL for defining item kits with inventory slots, armor, offhand, and a global registry.

## Key Classes

- **`Kit`** -- named kit with items, armor, and offhand
- **`KitBuilder`** -- DSL builder
- **`KitRegistry`** -- global thread-safe kit registry

## Usage

### Create

```kotlin
val warrior = kit("warrior") {
    item(0, ItemStack.of(Material.IRON_SWORD))
    item(1, Material.GOLDEN_APPLE, 3)
    helmet(Material.IRON_HELMET)
    chestplate(Material.IRON_CHESTPLATE)
    leggings(Material.IRON_LEGGINGS)
    boots(Material.IRON_BOOTS)
    offhand(Material.SHIELD)
}
KitRegistry.register(warrior)
```

### Apply

```kotlin
player.applyKit(warrior)

warrior.apply(player)

warrior.applyKeepExisting(player)
```

### Registry

```kotlin
KitRegistry["warrior"]
KitRegistry.require("warrior")
KitRegistry.all()
KitRegistry.names()
KitRegistry.unregister("warrior")
```

`apply` clears inventory first. `applyKeepExisting` only fills empty slots.
